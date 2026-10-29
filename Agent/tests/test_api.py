"""HTTP 接口契约测试（TestClient 直接驱动 FastAPI 应用，不需要外部服务）。

说明：
- 测试期间会把 ARK_API_KEY 清空并关闭 Milvus，强制走规则引擎 + 内存向量库，
  保证用例离线可跑、结果确定，不会消耗线上模型额度。
- 分析接口用 stub 替换 LangGraph 编排，验证的是"状态 -> HTTP 响应"的映射契约。
"""

from __future__ import annotations

import unittest
from unittest import mock

from fastapi.testclient import TestClient

from app.agents import llm as llm_module
from app.config import settings
from app.main import app

client = TestClient(app)


class _StubModel:
    engine = "rule-engine"
    model_name = "rule-engine-stub"

    def invoke(self, prompt: str) -> str:
        return ""


def _analysis_state() -> dict:
    return {
        "summary": "近 14 天累计学习 720 分钟，记录 12 条。",
        "suggestion": "建议把数学投入提升到每周 6 小时。",
        "risks": ["数学投入占比偏低"],
        "plan": [
            {"title": "三角函数专题", "detail": "每天 30 分钟", "due_date": "2026-10-25",
             "estimated_minutes": 120, "priority": "高"}
        ],
        # 不显式给 progress / message，用于验证会回落到枚举定义的默认进度与文案
        "stages": [{"stage": "data_collect"}, {"stage": "analysis"}],
        "model": "rule-engine-stub",
        "tokens_used": 0,
    }


class ApiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._key_patch = mock.patch.object(settings, "ark_api_key", "")
        cls._milvus_patch = mock.patch.object(settings, "milvus_enabled", False)
        cls._key_patch.start()
        cls._milvus_patch.start()
        # 清空密钥后需要重建模型实例，否则会复用真实大模型
        llm_module.reset_model()

    @classmethod
    def tearDownClass(cls):
        cls._key_patch.stop()
        cls._milvus_patch.stop()
        llm_module.reset_model()

    # ------------------------------------------------------------------ 健康

    def test_health(self):
        resp = client.get("/health")

        self.assertEqual(200, resp.status_code)
        body = resp.json()
        self.assertEqual("ok", body["status"])
        self.assertEqual("witpath-ai", body["service"])
        self.assertIn("vector", body)
        self.assertEqual("memory", body["vector"]["backend"])
        self.assertEqual("rule-engine", body["engine"])

    # ------------------------------------------------------------------ 对话

    def test_chat_returns_rule_reply(self):
        # 检索打桩：让用例不依赖 Embedding / 向量库是否可用
        with mock.patch(
            "app.routers.ai._retrieve",
            return_value=[{"source": "[1] note.pdf", "snippet": "三角函数", "score": 0.78}],
        ):
            resp = client.post(
                "/api/ai/chat",
                json={"userId": 1, "nickname": "小明", "message": "怎么安排复习？", "useKnowledge": True},
            )

        self.assertEqual(200, resp.status_code)
        body = resp.json()
        self.assertIn("怎么安排复习", body["reply"])
        self.assertIn("rule-engine", body["model"])
        self.assertEqual(1, len(body["referenced"]))
        self.assertEqual("[1] note.pdf", body["referenced"][0]["source"])

    def test_chat_requires_message(self):
        resp = client.post("/api/ai/chat", json={"userId": 1})

        self.assertEqual(422, resp.status_code)

    # ------------------------------------------------------------------ 分析

    def test_analyze_maps_graph_state_to_response(self):
        with mock.patch("app.routers.ai.run_analysis", return_value=_analysis_state()), \
                mock.patch("app.routers.ai.get_model", return_value=_StubModel()):
            resp = client.post(
                "/api/ai/analyze",
                json={
                    "userId": 1,
                    "taskNo": "T-UNIT-1",
                    "question": "学习效率如何？",
                    "summary": {"rangeDays": 14, "totalMinutes": 720, "from": "2026-10-01", "to": "2026-10-15"},
                },
            )

        self.assertEqual(200, resp.status_code)
        body = resp.json()
        self.assertEqual("T-UNIT-1", body["task_no"])
        self.assertEqual("succeeded", body["status"])
        self.assertEqual("近 14 天累计学习 720 分钟，记录 12 条。", body["summary"])
        self.assertEqual("三角函数专题", body["plan"][0]["title"])
        self.assertEqual(20, body["stages"][0]["progress"])
        self.assertEqual(40, body["stages"][1]["progress"])
        self.assertTrue(body["stages"][1]["message"], "阶段文案应由 STAGE_LABELS 兜底")

    def test_analyze_failure_is_reported_not_raised(self):
        with mock.patch("app.routers.ai.run_analysis", side_effect=RuntimeError("graph boom")), \
                mock.patch("app.routers.ai.get_model", return_value=_StubModel()):
            resp = client.post("/api/ai/analyze", json={"userId": 1, "taskNo": "T-UNIT-2"})

        self.assertEqual(200, resp.status_code)
        body = resp.json()
        self.assertEqual("failed", body["status"])
        self.assertIn("graph boom", body["error"])

    # ------------------------------------------------------------------ 资料

    def test_document_ingest_success(self):
        with mock.patch("app.routers.documents.ingest.index_document", return_value=(3, "memory")):
            resp = client.post(
                "/api/ai/documents/ingest",
                json={"userId": 1, "materialId": 55, "fileName": "note.pdf",
                      "fileType": "pdf", "storageType": "local", "localPath": "/tmp/note.pdf"},
            )

        self.assertEqual(200, resp.status_code)
        body = resp.json()
        self.assertEqual("succeeded", body["status"])
        self.assertEqual(3, body["chunk_count"])
        self.assertEqual(55, body["material_id"])

    def test_document_ingest_failure_returns_failed_status(self):
        with mock.patch("app.routers.documents.ingest.index_document", side_effect=RuntimeError("parse failed")):
            resp = client.post(
                "/api/ai/documents/ingest",
                json={"userId": 1, "materialId": 56, "fileName": "bad.pdf", "storageType": "local"},
            )

        self.assertEqual(200, resp.status_code)
        body = resp.json()
        self.assertEqual("failed", body["status"])
        self.assertIn("parse failed", body["error"])

    def test_document_search_and_delete(self):
        with mock.patch("app.routers.documents.retriever") as retriever_mock:
            retriever_mock.backend_name.return_value = "memory"
            retriever_mock.search_similar.return_value = [
                {"source": "[55] note.pdf", "snippet": "三角函数", "score": 0.78}
            ]
            resp = client.get("/api/ai/documents/search", params={"user_id": 1, "query": "三角函数"})
            self.assertEqual(200, resp.status_code)
            self.assertEqual(1, len(resp.json()["results"]))

            retriever_mock.delete_by_source.return_value = 2
            resp = client.delete("/api/ai/documents/55", params={"user_id": 1})
            self.assertEqual(200, resp.status_code)
            self.assertEqual(2, resp.json()["removed"])
            retriever_mock.delete_by_source.assert_called_once_with(user_id=1, source_prefix="[55]")


if __name__ == "__main__":
    unittest.main()

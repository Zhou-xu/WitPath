"""服务层测试：向量降级、记忆库隔离、进度回调契约。

这些用例全部离线可跑（不连接 Milvus / PostgreSQL / 大模型），
覆盖的是"外部依赖不可用时的降级路径"与"跨服务调用契约"。
"""

from __future__ import annotations

import unittest
from unittest import mock

from app.config import settings
from app.services import callback as callback_service
from app.services import retriever


def _hash_embedding():
    """把 embed_texts 固定为确定性哈希向量，避免用例依赖外部 Embedding 服务。

    签名需与生产代码一致：embed_texts(texts, override=None)，
    override 为用户个人向量模型配置，未配置时为 None。
    """
    return mock.patch.object(
        retriever,
        "embed_texts",
        side_effect=lambda texts, override=None: [retriever._hash_vector(t) for t in texts],
    )


class HashEmbeddingTest(unittest.TestCase):
    """无 Embedding 服务时的哈希向量降级。"""

    def test_deterministic_and_normalized(self):
        a = retriever._hash_vector("三角函数和角公式")
        b = retriever._hash_vector("三角函数和角公式")

        self.assertEqual(a, b, "同一文本必须得到同一向量，否则检索结果不稳定")
        self.assertEqual(retriever.VECTOR_DIM_FALLBACK, len(a))
        norm = sum(v * v for v in a) ** 0.5
        self.assertAlmostEqual(1.0, norm, places=5, msg="哈希向量应为单位向量")

    def test_different_text_produces_different_vector(self):
        self.assertNotEqual(
            retriever._hash_vector("三角函数"),
            retriever._hash_vector("立体几何"),
        )

    def test_cosine_self_is_one(self):
        v = retriever._hash_vector("英语阅读")
        self.assertAlmostEqual(1.0, retriever._cosine(v, v), places=5)

    def test_cosine_dimension_mismatch_is_zero(self):
        self.assertEqual(0.0, retriever._cosine([1.0, 0.0], [1.0, 0.0, 0.0]))

    def test_embed_texts_falls_back_to_hash(self):
        # 打桩 embed_texts：既验证降级路径，也保证用例不会发起真实网络请求
        with _hash_embedding():
            vectors = retriever.embed_texts(["第一段", "第二段"])
        self.assertEqual(2, len(vectors))
        self.assertEqual(retriever.VECTOR_DIM_FALLBACK, len(vectors[0]))


class MemoryStoreTest(unittest.TestCase):
    """内存向量库的写入、用户隔离与删除。"""

    def setUp(self):
        self.store = retriever._MemoryStore()

    def test_upsert_and_search(self):
        self.store.upsert(
            [
                {"user_id": 1, "source": "[1] a.pdf", "content": "三角函数", "vector": [1.0, 0.0]},
                {"user_id": 1, "source": "[1] a.pdf", "content": "立体几何", "vector": [0.0, 1.0]},
            ]
        )

        hits = self.store.search(1, [1.0, 0.0], top_k=2)
        self.assertEqual(2, len(hits))
        self.assertEqual("三角函数", hits[0]["content"], "最相似的应排在最前")

    def test_user_isolation(self):
        self.store.upsert([{"user_id": 1, "source": "s", "content": "c", "vector": [1.0]}])
        self.store.upsert([{"user_id": 2, "source": "s", "content": "c", "vector": [1.0]}])

        self.assertEqual(1, len(self.store.search(1, [1.0], top_k=5)))
        self.assertEqual(2, len(self.store.search(None, [1.0], top_k=5)))

    def test_delete_by_source(self):
        self.store.upsert(
            [
                {"user_id": 1, "source": "[1] a.pdf", "content": "c", "vector": [1.0]},
                {"user_id": 1, "source": "[2] b.pdf", "content": "c", "vector": [1.0]},
            ]
        )
        self.store.delete_by_source(1, "[1] a.pdf")

        self.assertEqual(1, self.store.count())
        self.assertEqual("[2] b.pdf", self.store.search(1, [1.0], top_k=5)[0]["source"])


class RetrieverFallbackTest(unittest.TestCase):
    """Milvus 关闭时的整体降级路径。"""

    def test_add_and_delete_use_memory_when_milvus_disabled(self):
        with mock.patch.object(retriever.settings, "milvus_enabled", False), _hash_embedding():
            written = retriever.add_documents(1, "[unit-test] doc.md", ["内容一", "内容二"])
            hits = retriever.search_similar(1, "内容一", top_k=2)
            removed = retriever.delete_by_source(1, "[unit-test] doc.md")
            # 删除后的复查必须同样在降级环境内执行，否则会真的连 Milvus / Ollama
            remaining = retriever.search_similar(1, "内容一", top_k=2)

        self.assertEqual(2, written)
        self.assertTrue(hits, "关闭 Milvus 时仍应能从内存库检索到内容")
        self.assertEqual("[unit-test] doc.md", hits[0]["source"])
        self.assertEqual(2, removed)
        self.assertEqual(0, len(remaining))

    def test_backend_name_is_memory_when_disabled(self):
        with mock.patch.object(retriever.settings, "milvus_enabled", False):
            self.assertEqual("memory", retriever.backend_name())

    def test_delete_requires_prefix(self):
        self.assertEqual(0, retriever.delete_by_source(1, ""))


class ProgressCallbackTest(unittest.TestCase):
    """进度回调：契约正确 + 必须绕过系统代理（trust_env=False）。"""

    def test_payload_and_headers(self):
        captured = {}

        class _FakeResponse:
            status_code = 200
            text = "ok"

        class _FakeClient:
            def __init__(self, **kwargs):
                captured["kwargs"] = kwargs

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def post(self, url, json=None, headers=None):
                captured["url"] = url
                captured["json"] = json
                captured["headers"] = headers
                return _FakeResponse()

        with mock.patch.object(callback_service.httpx, "Client", _FakeClient):
            callback_service.push_progress(
                callback_url="http://127.0.0.1:8080/api/ai/callback",
                callback_token="unit-token",
                task_no="T1",
                stage="analysis",
                stage_message="分析学习态势",
                progress=40,
            )

        self.assertEqual("http://127.0.0.1:8080/api/ai/callback", captured["url"])
        self.assertEqual(
            {"taskNo": "T1", "stage": "analysis", "stageMessage": "分析学习态势", "progress": 40},
            captured["json"],
        )
        self.assertEqual("unit-token", captured["headers"].get("X-WitPath-AI-Token"))
        self.assertFalse(captured["kwargs"]["trust_env"], "内部回调不能走系统代理，否则会超时")

    def test_missing_url_or_task_no_is_noop(self):
        with mock.patch.object(callback_service.httpx, "Client") as client:
            callback_service.push_progress(None, "token", "T1", "queued")
            callback_service.push_progress("http://x/api", "token", None, "queued")
            client.assert_not_called()

    def test_network_error_is_swallowed(self):
        """回调失败不能影响主流程。"""
        with mock.patch.object(callback_service.httpx, "Client", side_effect=RuntimeError("boom")):
            callback_service.push_progress("http://x/api", "token", "T1", "queued")


class SettingsTest(unittest.TestCase):
    """配置可用性的判定逻辑。"""

    def test_llm_available_depends_on_key(self):
        original = settings.ark_api_key
        try:
            settings.ark_api_key = ""
            self.assertFalse(settings.llm_available)
            settings.ark_api_key = "ark-test-key"
            self.assertTrue(settings.llm_available)
        finally:
            settings.ark_api_key = original

    def test_ollama_embedding_needs_no_key(self):
        with mock.patch.object(settings, "embedding_provider", "ollama"), \
                mock.patch.object(settings, "ark_api_key", ""):
            self.assertTrue(settings.embedding_available)


if __name__ == "__main__":
    unittest.main()

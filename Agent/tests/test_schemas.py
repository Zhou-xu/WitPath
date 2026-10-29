"""数据契约测试：Spring Boot（camelCase）与 FastAPI（snake_case）的字段映射。

这些用例锁住跨语言调用最容易出问题的一环：Java 序列化出来的字段名
必须能被 pydantic 正确接收，否则 AI 侧会静默丢失上下文。
"""

from __future__ import annotations

import unittest

from app.schemas import (
    AiAnalyzeRequest,
    AiAnalyzeResponse,
    ChatRequest,
    ChatResponse,
    DocIngestRequest,
    PlanItem,
    StageItem,
    _to_camel,
)


def java_style_payload() -> dict:
    """模拟 Spring Boot Jackson 序列化 AiContextPayload 后的 JSON。"""
    return {
        "userId": 10086,
        "taskNo": "AI20261018001",
        "taskType": "STUDY_ANALYSIS",
        "question": "最近两周数学学习效率怎么样？",
        "callbackUrl": "http://127.0.0.1:8080/api/ai/callback",
        "callbackToken": "witpath-internal-token",
        "profile": {"nickname": "小明", "studyStage": "高二", "studyGoal": "期末提分", "preferredTags": ["数学"]},
        "summary": {
            "rangeDays": 14,
            "totalMinutes": 720,
            "recordCount": 12,
            "streakDays": 5,
            "avgFocus": 4.2,
            "from": "2026-10-01",
            "to": "2026-10-15",
        },
        "goals": [
            {
                "id": 1,
                "title": "期末数学冲刺",
                "subject": "数学",
                "status": 1,
                "startDate": "2026-10-01",
                "endDate": "2026-10-31",
                "targetHours": 30.0,
                "actualHours": 12.0,
                "progress": 40.0,
                "taskTotal": 4,
                "taskDone": 2,
            }
        ],
        "tasks": [
            {
                "id": 11,
                "goalId": 1,
                "title": "完成三角函数专题",
                "status": 3,
                "priority": 1,
                "dueDate": "2026-10-20",
                "estimatedMinutes": 120,
                "overdue": False,
            }
        ],
        "records": [
            {
                "studyDate": "2026-10-05",
                "title": "三角函数练习",
                "subject": "数学",
                "durationMinutes": 90,
                "focusScore": 4,
                "outcome": "掌握和角公式",
            }
        ],
        "materialSnippets": ["三角函数和角公式：sin(a+b)=..."],
    }


class CamelCaseAliasTest(unittest.TestCase):
    """camelCase 别名生成与双向解析。"""

    def test_to_camel(self):
        self.assertEqual("userId", _to_camel("user_id"))
        self.assertEqual("taskNo", _to_camel("task_no"))
        self.assertEqual("materialSnippets", _to_camel("material_snippets"))
        self.assertEqual("id", _to_camel("id"))
        self.assertEqual("_", _to_camel("_"))

    def test_java_payload_maps_to_snake_fields(self):
        req = AiAnalyzeRequest(**java_style_payload())

        self.assertEqual(10086, req.user_id)
        self.assertEqual("AI20261018001", req.task_no)
        self.assertEqual("http://127.0.0.1:8080/api/ai/callback", req.callback_url)
        self.assertEqual("witpath-internal-token", req.callback_token)
        self.assertEqual("小明", req.profile.nickname)
        self.assertEqual(["数学"], req.profile.preferred_tags)

    def test_summary_dates_and_from_alias(self):
        req = AiAnalyzeRequest(**java_style_payload())

        self.assertEqual(14, req.summary.range_days)
        self.assertEqual(720, req.summary.total_minutes)
        self.assertEqual("2026-10-01", req.summary.from_)
        self.assertEqual("2026-10-15", req.summary.to)

    def test_nested_lists_mapped(self):
        req = AiAnalyzeRequest(**java_style_payload())

        self.assertEqual(1, len(req.goals))
        self.assertEqual("期末数学冲刺", req.goals[0].title)
        self.assertEqual(30.0, req.goals[0].target_hours)
        self.assertEqual("2026-10-01", req.goals[0].start_date)
        self.assertEqual(1, req.tasks[0].goal_id)
        self.assertEqual("2026-10-20", req.tasks[0].due_date)
        self.assertEqual(90, req.records[0].duration_minutes)
        self.assertEqual("2026-10-05", req.records[0].study_date)
        self.assertEqual(["三角函数和角公式：sin(a+b)=..."], req.material_snippets)

    def test_snake_case_still_accepted(self):
        """Python 侧自测常用 snake_case，必须同样可用（populate_by_name）。"""
        req = AiAnalyzeRequest(
            user_id=7,
            task_no="T1",
            summary={"range_days": 7, "total_minutes": 60, "from": "2026-10-01", "to": "2026-10-08"},
            material_snippets=["a"],
        )

        self.assertEqual(7, req.user_id)
        self.assertEqual(7, req.summary.range_days)
        self.assertEqual("2026-10-01", req.summary.from_)

    def test_defaults_when_fields_missing(self):
        req = AiAnalyzeRequest()

        self.assertEqual("STUDY_ANALYSIS", req.task_type)
        self.assertEqual(14, req.summary.range_days)
        self.assertEqual([], req.goals)
        self.assertEqual([], req.material_snippets)

    def test_unknown_fields_tolerated(self):
        """Java 侧新增字段不应让 AI 服务直接 422。"""
        payload = java_style_payload()
        payload["futureFieldFromJava"] = {"any": "thing"}

        req = AiAnalyzeRequest(**payload)
        self.assertEqual(10086, req.user_id)

    def test_chat_request_camel_fields(self):
        req = ChatRequest(
            **{
                "userId": 1,
                "nickname": "小明",
                "message": "帮我安排复习",
                "history": [{"role": "user", "content": "上次说到哪了"}],
                "useKnowledge": True,
            }
        )

        self.assertEqual(1, req.user_id)
        self.assertEqual("帮我安排复习", req.message)
        self.assertTrue(req.use_knowledge)
        self.assertEqual("user", req.history[0].role)

    def test_doc_ingest_request_camel_fields(self):
        req = DocIngestRequest(
            **{
                "userId": 1,
                "materialId": 99,
                "fileName": "note.pdf",
                "fileType": "pdf",
                "storageType": "local",
                "localPath": "/tmp/note.pdf",
                "ossObjectKey": None,
                "subject": "数学",
            }
        )

        self.assertEqual(99, req.material_id)
        self.assertEqual("note.pdf", req.file_name)
        self.assertEqual("local", req.storage_type)
        self.assertEqual("/tmp/note.pdf", req.local_path)


class ResponseSchemaTest(unittest.TestCase):
    """响应模型：字段名与默认值需要与 Spring Boot 侧 DTO 对齐。"""

    def test_analyze_response_defaults(self):
        resp = AiAnalyzeResponse(task_no="T1")

        self.assertEqual("STUDY_ANALYSIS", resp.type)
        self.assertEqual("succeeded", resp.status)
        self.assertEqual([], resp.plan)
        self.assertIsNone(resp.error)

    def test_analyze_response_serialization(self):
        resp = AiAnalyzeResponse(
            task_no="T1",
            summary="近两周学习 720 分钟",
            suggestion="保持节奏",
            risks=["数学投入不足"],
            plan=[PlanItem(title="三角函数专题", detail="30 分钟/天", due_date="2026-10-20", estimated_minutes=120)],
            stages=[StageItem(stage="analysis", message="分析学习态势", progress=40)],
        )

        data = resp.model_dump()
        self.assertEqual("T1", data["task_no"])
        self.assertEqual("三角函数专题", data["plan"][0]["title"])
        self.assertEqual(40, data["stages"][0]["progress"])

    def test_chat_response_defaults(self):
        resp = ChatResponse(reply="收到")

        self.assertEqual("rule-engine", resp.model)
        self.assertEqual([], resp.referenced)
        self.assertIsNone(resp.error)


if __name__ == "__main__":
    unittest.main()

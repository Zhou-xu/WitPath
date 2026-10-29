"""LangGraph 工作流状态定义。

State 是各 Agent 节点之间唯一的通信载体，所有节点只读自己关心的字段、
只写自己负责的字段，避免节点之间的隐式耦合。
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional, TypedDict

STAGE_ORDER = [
    "data_collect",
    "analysis",
    "planning",
    "retrieval",
    "advice",
    "summary",
]

STAGE_LABELS = {
    "queued": "已排队",
    "data_collect": "整理学习数据",
    "analysis": "分析学习态势",
    "planning": "生成学习计划",
    "retrieval": "检索知识资料",
    "advice": "生成改进建议",
    "summary": "整合结论",
}

STAGE_PROGRESS = {
    "queued": 0,
    "data_collect": 20,
    "analysis": 40,
    "planning": 60,
    "retrieval": 75,
    "advice": 90,
    "summary": 100,
}


class AnalysisState(TypedDict, total=False):
    """学习分析工作流状态。"""

    # ---- 输入 ----
    task_no: str
    task_type: str
    question: str
    payload: Dict[str, Any]
    callback_url: Optional[str]
    callback_token: Optional[str]

    # ---- 各 Agent 产出 ----
    brief: str
    analysis: str
    plan_raw: str
    plan: List[Dict[str, Any]]
    knowledge: List[Dict[str, Any]]
    advice: str
    risks: List[str]

    # ---- 汇总 ----
    summary: str
    suggestion: str
    stages: List[Dict[str, Any]]
    model: str
    tokens_used: int
    error: Optional[str]

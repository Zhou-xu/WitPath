"""AI 服务数据契约。

与 Spring Boot 侧 AiContextPayload / AiAnalyzeResponse 严格对应，
字段采用 snake_case 命名，便于跨语言对齐。
"""

from __future__ import annotations

from typing import Any, List, Optional

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(name: str) -> str:
    """snake_case -> camelCase，保证 Spring Boot 传来的字段名可直接映射。"""
    parts = [part for part in name.split("_") if part]
    if not parts:
        return name
    return parts[0] + "".join(part[:1].upper() + part[1:] for part in parts[1:])


class CamelModel(BaseModel):
    """入参模型基类：同时接受 camelCase（Java）与 snake_case（Python）字段名。"""

    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True, extra="allow")


class UserModelConfig(CamelModel):
    """用户个人模型配置（可选覆盖）。

    字段名不能用 model_config —— 那是 pydantic 的保留属性名。
    任何字段留空都表示该能力回落服务端 .env 中的默认配置，
    这样用户只配一半（例如只配大模型不配向量模型）也能正常工作。
    """

    llm_provider: Optional[str] = None
    llm_api_key: Optional[str] = None
    llm_base_url: Optional[str] = None
    llm_model: Optional[str] = None

    embedding_provider: Optional[str] = None
    embedding_model: Optional[str] = None
    embedding_api_key: Optional[str] = None
    embedding_base_url: Optional[str] = None

    @property
    def has_llm(self) -> bool:
        return bool(self.llm_api_key and self.llm_base_url and self.llm_model)

    @property
    def has_embedding(self) -> bool:
        return bool(self.embedding_api_key and self.embedding_model)


class UserProfile(CamelModel):
    nickname: Optional[str] = None
    study_stage: Optional[str] = None
    study_goal: Optional[str] = None
    preferred_tags: List[str] = Field(default_factory=list)


class Summary(CamelModel):
    range_days: int = 14
    total_minutes: int = 0
    record_count: int = 0
    streak_days: int = 0
    avg_focus: float = 0.0
    from_: Optional[str] = None
    to: Optional[str] = None


class GoalItem(CamelModel):
    id: Optional[int] = None
    title: Optional[str] = None
    subject: Optional[str] = None
    status: Optional[int] = None
    start_date: Optional[str] = None
    end_date: Optional[str] = None
    target_hours: float = 0.0
    actual_hours: float = 0.0
    progress: float = 0.0
    task_total: int = 0
    task_done: int = 0


class TaskItem(CamelModel):
    id: Optional[int] = None
    goal_id: Optional[int] = None
    title: Optional[str] = None
    status: Optional[int] = None
    priority: Optional[int] = None
    due_date: Optional[str] = None
    estimated_minutes: int = 0
    overdue: bool = False


class RecordItem(CamelModel):
    study_date: Optional[str] = None
    title: Optional[str] = None
    subject: Optional[str] = None
    duration_minutes: int = 0
    focus_score: int = 0
    outcome: Optional[str] = None


class AiAnalyzeRequest(CamelModel):
    """学习分析任务请求。"""

    user_id: Optional[int] = None
    task_no: Optional[str] = None
    task_type: str = "STUDY_ANALYSIS"
    question: Optional[str] = None
    callback_url: Optional[str] = None
    callback_token: Optional[str] = None
    profile: UserProfile = Field(default_factory=UserProfile)
    summary: Summary = Field(default_factory=Summary)
    goals: List[GoalItem] = Field(default_factory=list)
    tasks: List[TaskItem] = Field(default_factory=list)
    records: List[RecordItem] = Field(default_factory=list)
    material_snippets: List[str] = Field(default_factory=list)

    # 用户个人模型配置（后端解密后随请求传入），为空则使用服务端默认配置
    user_model_config: Optional[UserModelConfig] = None


class PlanItem(BaseModel):
    title: str
    detail: str = ""
    due_date: Optional[str] = None
    estimated_minutes: int = 0
    priority: str = "中"


class StageItem(BaseModel):
    stage: str
    message: str = ""
    progress: int = 0


class AiAnalyzeResponse(BaseModel):
    task_no: Optional[str] = None
    type: str = "STUDY_ANALYSIS"
    status: str = "succeeded"
    model: str = "rule-engine"
    tokens_used: int = 0
    latency_ms: int = 0
    summary: str = ""
    suggestion: str = ""
    risks: List[str] = Field(default_factory=list)
    plan: List[PlanItem] = Field(default_factory=list)
    stages: List[StageItem] = Field(default_factory=list)
    error: Optional[str] = None


class HistoryItem(BaseModel):
    role: str
    content: str


class ChatRequest(CamelModel):
    user_id: Optional[int] = None
    nickname: Optional[str] = None
    message: str
    history: List[HistoryItem] = Field(default_factory=list)
    use_knowledge: bool = True
    context: Optional[Any] = None
    user_model_config: Optional[UserModelConfig] = None


class Reference(BaseModel):
    source: str = ""
    snippet: str = ""
    score: float = 0.0


class ChatResponse(BaseModel):
    reply: str
    tokens_used: int = 0
    model: str = "rule-engine"
    referenced: List[Reference] = Field(default_factory=list)
    error: Optional[str] = None


class DocIngestRequest(CamelModel):
    user_id: Optional[int] = None
    material_id: Optional[int] = None
    file_name: str = ""
    file_type: str = ""
    storage_type: str = "local"
    local_path: Optional[str] = None
    oss_bucket: Optional[str] = None
    oss_object_key: Optional[str] = None
    subject: Optional[str] = None
    user_model_config: Optional[UserModelConfig] = None


class SearchRequest(CamelModel):
    """知识检索请求（POST body，便于携带用户个人向量模型配置）。"""

    user_id: Optional[int] = None
    query: str = ""
    top_k: int = 5
    user_model_config: Optional[UserModelConfig] = None


class DocIngestResponse(BaseModel):
    material_id: Optional[int] = None
    status: str = "succeeded"
    chunk_count: int = 0
    vector_backend: str = "memory"
    error: Optional[str] = None

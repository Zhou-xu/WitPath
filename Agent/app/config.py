"""AI 服务配置。

所有敏感信息均从环境变量（或 .env）读取，禁止硬编码到代码中。
"""

from __future__ import annotations

from functools import lru_cache
from typing import Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """服务配置：先读 .env，再读系统环境变量。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # --------- 大模型（火山方舟 / OpenAI 兼容） ---------
    ark_api_key: str = Field(default="", alias="ARK_API_KEY")
    ark_base_url: str = Field(
        default="https://ark.cn-beijing.volces.com/api/v3", alias="ARK_BASE_URL"
    )
    ark_model: str = Field(default="deepseek-v4-pro-ga-260813", alias="ARK_MODEL")

    # --------- 服务自身 ---------
    ai_service_host: str = Field(default="0.0.0.0", alias="AI_SERVICE_HOST")
    ai_service_port: int = Field(default=8000, alias="AI_SERVICE_PORT")
    spring_callback_token: str = Field(
        default="witpath-internal-token", alias="SPRING_CALLBACK_TOKEN"
    )

    # --------- PostgreSQL（LangGraph 检查点） ---------
    pg_enabled: bool = Field(default=False, alias="PG_ENABLED")
    pg_dsn: str = Field(
        default="postgresql://postgres:postgres@127.0.0.1:5432/witpath_ai",
        alias="PG_DSN",
    )

    # --------- Milvus ---------
    milvus_enabled: bool = Field(default=False, alias="MILVUS_ENABLED")
    milvus_host: str = Field(default="127.0.0.1", alias="MILVUS_HOST")
    milvus_port: int = Field(default=19530, alias="MILVUS_PORT")
    milvus_collection: str = Field(default="witpath_docs", alias="MILVUS_COLLECTION")

    # --------- 阿里云 OSS ---------
    oss_enabled: bool = Field(default=False, alias="OSS_ENABLED")
    oss_endpoint: str = Field(default="", alias="OSS_ENDPOINT")
    oss_access_key_id: str = Field(default="", alias="OSS_ACCESS_KEY_ID")
    oss_access_key_secret: str = Field(default="", alias="OSS_ACCESS_KEY_SECRET")
    oss_bucket_name: str = Field(default="", alias="OSS_BUCKET_NAME")

    # --------- Embedding ---------
    # provider: dashscope（阿里云百炼线上向量模型）/ ollama（本地）/ ark（火山方舟）
    embedding_provider: str = Field(default="dashscope", alias="EMBEDDING_PROVIDER")
    embedding_model: str = Field(default="qwen3.7-text-embedding", alias="EMBEDDING_MODEL")
    ollama_host: str = Field(default="http://127.0.0.1:11434", alias="OLLAMA_HOST")
    dashscope_api_key: str = Field(default="", alias="DASHSCOPE_API_KEY")
    dashscope_embedding_base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1",
        alias="DASHSCOPE_EMBEDDING_BASE_URL",
    )

    @property
    def llm_available(self) -> bool:
        """是否配置了可用的大模型密钥。"""
        return bool(self.ark_api_key and self.ark_api_key.strip())

    @property
    def embedding_available(self) -> bool:
        """百炼/方舟在线向量模型需要密钥；本地 Ollama 无需密钥（但需服务在线）。"""
        provider = self.embedding_provider
        if provider == "dashscope":
            return bool(self.dashscope_api_key and self.dashscope_api_key.strip())
        if provider == "ollama":
            return True
        return self.llm_available

    @property
    def model_name(self) -> str:
        return self.ark_model


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()


settings: Settings = get_settings()
OPTIONAL: Optional[str] = None

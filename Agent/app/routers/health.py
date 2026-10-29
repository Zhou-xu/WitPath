"""健康检查与运行时状态。"""

from __future__ import annotations

import platform

from fastapi import APIRouter

from app.agents.llm import get_model
from app.config import settings
from app.services import ingest, retriever
from app.services.memory import backend_name as checkpoint_backend

router = APIRouter(tags=["health"])


@router.get("/health")
def health():
    """返回服务及其依赖的可用状态，供 Spring Boot /public/health 聚合。"""
    model = get_model()
    return {
        "status": "ok",
        "service": "witpath-ai",
        "python": platform.python_version(),
        "engine": model.engine,
        "model": model.model_name,
        "llm_enabled": settings.llm_available,
        "checkpoint": checkpoint_backend(),
        "vector": retriever.store_stats(),
        "oss_enabled": settings.oss_enabled,
        "storage_dir": ingest.BASE_STORAGE_DIR,
    }

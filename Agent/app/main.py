"""FastAPI 应用入口。

服务职责边界：
- 本服务只负责 AI 能力（模型推理、Agent 编排、知识检索）；
- 业务数据由 Spring Boot 提供，本服务不直连 MySQL。
"""

from __future__ import annotations

import logging
import time

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import ai as ai_router
from app.routers import documents as documents_router
from app.routers import health as health_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("witpath.ai")

app = FastAPI(
    title="WitPath AI Service",
    description="学途知行 AI 协作平台 - FastAPI + LangChain + LangGraph 多 Agent 服务",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def access_log(request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    cost = (time.perf_counter() - start) * 1000
    logger.info("%s %s -> %s (%.1f ms)", request.method, request.url.path, response.status_code, cost)
    return response


app.include_router(health_router.router)
app.include_router(ai_router.router)
app.include_router(documents_router.router)


@app.on_event("startup")
async def on_startup() -> None:
    logger.info(
        "AI 服务启动完成：model=%s llm=%s",
        settings.model_name,
        "enabled" if settings.llm_available else "disabled(rule-engine)",
    )

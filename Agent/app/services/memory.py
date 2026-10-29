"""LangGraph 检查点持久化（PostgreSQL）。

PostgreSQL 可用时启用官方 checkpointer；不可用时返回内存实现，
使工作流状态在同一进程内的多轮调用之间仍可复用。
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from app.config import settings

logger = logging.getLogger(__name__)


def build_checkpointer() -> Optional[Any]:
    """返回可用的 checkpointer（PostgreSQL / 内存 / None）。"""
    if settings.pg_enabled:
        try:
            from langgraph.checkpoint.postgres import PostgresSaver
            from psycopg_pool import ConnectionPool

            pool = ConnectionPool(
                conninfo=settings.pg_dsn,
                max_size=5,
                kwargs={"autocommit": True, "prepare_threshold": 0},
                open=True,
            )
            saver = PostgresSaver(pool)
            saver.setup()
            logger.info("LangGraph 检查点已启用 PostgreSQL 持久化")
            return saver
        except Exception as exc:  # noqa: BLE001
            logger.warning("PostgreSQL 检查点不可用，回退内存模式：%s", exc)

    try:
        from langgraph.checkpoint.memory import InMemorySaver

        logger.info("LangGraph 检查点使用内存模式（进程重启后丢失）")
        return InMemorySaver()
    except ImportError:
        logger.warning("langgraph 检查点模块缺失，禁用持久化")
        return None


def backend_name() -> str:
    return "postgresql" if settings.pg_enabled else "memory"

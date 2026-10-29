"""PostgreSQL 连接信息。

不保存任何明文口令：优先读取环境变量/WITPATH 的 PG_DSN，缺失时按
环境变量拼装（POSTGRES_PASSWORD 等），全部为空则返回 None 由调用方降级。
"""

from __future__ import annotations

import os
from typing import Optional

PG_HOST = os.getenv("PG_HOST", "127.0.0.1")
PG_PORT = os.getenv("PG_PORT", "5432")
PG_DB = os.getenv("PG_DB", "witpath_ai")
PG_USER = os.getenv("PG_USER", "postgres")
PG_PASSWORD = os.getenv("POSTGRES_PASSWORD", "")


def dsn() -> Optional[str]:
    """返回可用的连接串，口令缺失时返回 None。"""
    env_dsn = os.getenv("PG_DSN")
    if env_dsn:
        return env_dsn
    if not PG_PASSWORD:
        return None
    return f"postgresql://{PG_USER}:{PG_PASSWORD}@{PG_HOST}:{PG_PORT}/{PG_DB}"

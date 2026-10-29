"""向 Spring Boot 回推 AI 任务阶段性进度。

AI 服务在处理过程中主动回调业务后端，后端据此更新 ai_task 表的 stage / progress，
前端轮询任务详情即可看到实时进度。回调失败不影响主流程。
"""

from __future__ import annotations

import logging
from typing import Optional

import httpx

logger = logging.getLogger(__name__)

_TIMEOUT = 3.0


def push_progress(
    callback_url: Optional[str],
    callback_token: Optional[str],
    task_no: Optional[str],
    stage: str,
    stage_message: str = "",
    progress: int = 0,
) -> None:
    """发送一次进度回调（best-effort，失败仅记录日志）。"""
    if not callback_url or not task_no:
        return
    payload = {
        "taskNo": task_no,
        "stage": stage,
        "stageMessage": stage_message,
        "progress": progress,
    }
    headers = {"Content-Type": "application/json"}
    if callback_token:
        headers["X-WitPath-AI-Token"] = callback_token

    try:
        # trust_env=False：内部回调必须绕过 HTTP_PROXY，否则本机请求会被代理拦截而超时
        with httpx.Client(timeout=_TIMEOUT, trust_env=False) as client:
            resp = client.post(callback_url, json=payload, headers=headers)
            if resp.status_code >= 400:
                logger.warning(
                    "进度回调失败：taskNo=%s stage=%s status=%s body=%s",
                    task_no, stage, resp.status_code, resp.text[:200],
                )
    except Exception as exc:  # noqa: BLE001
        logger.warning("进度回调异常：taskNo=%s stage=%s err=%s", task_no, stage, exc)

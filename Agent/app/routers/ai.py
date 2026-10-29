"""AI 分析与助手对话接口。"""

from __future__ import annotations

import logging
import time
from typing import Any, Dict

from fastapi import APIRouter

from app.agents.graph import run_analysis
from app.agents.llm import get_model
from app.agents.prompts import CHAT_PROMPT, CHAT_SYSTEM_PROMPT
from app.agents.state import STAGE_LABELS, STAGE_PROGRESS
from app.schemas import AiAnalyzeRequest, AiAnalyzeResponse, ChatRequest, ChatResponse, PlanItem, StageItem

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/ai", tags=["ai"])


@router.post("/analyze", response_model=AiAnalyzeResponse)
def analyze(request: AiAnalyzeRequest) -> AiAnalyzeResponse:
    """执行学习分析工作流（LangGraph 多 Agent）。"""
    started = time.perf_counter()
    # 用户配置了个人模型时优先使用，否则回落服务端 .env 中的默认配置
    model = get_model(request.user_model_config)

    initial_state = {
        "task_no": request.task_no or "",
        "task_type": request.task_type,
        "question": request.question or "",
        "payload": request.model_dump(by_alias=False, exclude_none=False),
        "callback_url": request.callback_url,
        "callback_token": request.callback_token,
        "brief": "",
        "analysis": "",
        "plan_raw": "",
        "plan": [],
        "knowledge": [],
        "advice": "",
        "risks": [],
        "summary": "",
        "suggestion": "",
        "stages": [],
        "model": model.model_name,
        "tokens_used": 0,
        "error": None,
    }

    try:
        state = run_analysis(initial_state, thread_id=request.task_no)
    except Exception as exc:  # noqa: BLE001
        logger.exception("AI 分析工作流执行失败")
        return AiAnalyzeResponse(
            task_no=request.task_no,
            type=request.task_type,
            status="failed",
            model=model.model_name,
            error=str(exc),
        )

    latency_ms = int((time.perf_counter() - started) * 1000)
    stages = [
        StageItem(
            stage=item.get("stage", ""),
            message=item.get("message", STAGE_LABELS.get(item.get("stage", ""), "")),
            progress=item.get("progress", STAGE_PROGRESS.get(item.get("stage", ""), 0)),
        )
        for item in (state.get("stages") or [])
    ]
    plan = [PlanItem(**item) for item in (state.get("plan") or [])]

    return AiAnalyzeResponse(
        task_no=request.task_no,
        type=request.task_type,
        status="succeeded",
        model=state.get("model") or model.model_name,
        tokens_used=int(state.get("tokens_used") or 0),
        latency_ms=latency_ms,
        summary=state.get("summary") or "",
        suggestion=state.get("suggestion") or "",
        risks=[str(r) for r in (state.get("risks") or [])],
        plan=plan,
        stages=stages,
    )


@router.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    """AI 助手多轮对话。"""
    model = get_model(request.user_model_config)
    history_text = "\n".join(
        f"{'用户' if h.role == 'user' else '助手'}：{h.content}" for h in (request.history or [])
    ) or "（无历史）"

    ctx_text = _format_context(request.context)
    prompt = CHAT_PROMPT.format(context=ctx_text, history=history_text, message=request.message)

    if getattr(model, "engine", "rule-engine") == "llm":
        try:
            reply = model.invoke(prompt)
            tokens = getattr(model, "total_tokens", 0)
            model_name = model.model_name
        except Exception as exc:  # noqa: BLE001
            logger.warning("模型调用失败，改用规则回复：%s", exc)
            reply = _rule_reply(request.message)
            tokens, model_name = 0, f"{model.model_name}(fallback)"
    else:
        reply = _rule_reply(request.message)
        tokens, model_name = 0, model.model_name

    referenced: list[dict[str, Any]] = []
    if request.use_knowledge:
        referenced = _retrieve(request.user_id, request.message)

    return ChatResponse(reply=reply or "我暂时无法回答这个问题，请稍后再试。",
                        tokens_used=tokens,
                        model=model_name,
                        referenced=referenced)


# ---------------------------------------------------------------------------


def _retrieve(user_id, message: str) -> list[dict[str, Any]]:
    try:
        from app.services.retriever import search_similar

        return [
            {
                "source": item.get("source", ""),
                "snippet": item.get("snippet", ""),
                "score": float(item.get("score", 0)),
            }
            for item in search_similar(user_id=user_id, query=message, top_k=3)
        ]
    except Exception as exc:  # noqa: BLE001
        logger.warning("对话知识检索失败：%s", exc)
        return []


def _format_context(context: Any) -> str:
    """把 Spring Boot 传来的概览数据压缩成模型可读文本。"""
    if not context:
        return "（本次未提供学习上下文）"
    if isinstance(context, dict):
        overview = context.get("overview") or {}
        trend = (context.get("trend") or [])[-7:]
        parts = [
            f"总学习时长 {overview.get('totalMinutes', 0)} 分钟",
            f"近 7 天 {overview.get('last7DaysMinutes', 0)} 分钟",
            f"今日 {overview.get('todayMinutes', 0)} 分钟",
            f"连续学习 {overview.get('streakDays', 0)} 天",
            f"记录数 {overview.get('recordCount', 0)}",
            f"进行中目标 {overview.get('activeGoalCount', 0)} 个",
            f"待办任务 {overview.get('todoTaskCount', 0)} 个，逾期 {overview.get('overdueTaskCount', 0)} 个",
            f"平均专注度 {overview.get('avgFocus', 0)} / 5",
        ]
        goals = context.get("goals") or []
        if goals:
            parts.append(
                "目标进度：" + "；".join(
                    f"{g.get('title')} {g.get('progress')}%" for g in goals[:5]
                )
            )
        if trend:
            parts.append(
                "近 7 天时长：" + ", ".join(f"{t.get('date')}={t.get('minutes')}分钟" for t in trend)
            )
        return "；".join(parts)
    return str(context)[:2000]


def _rule_reply(message: str) -> str:
    """无 LLM 时的兜底回复：给出可执行的通用学习建议。"""
    return (
        "我已收到你的问题：" + (message[:40] + ("..." if len(message) > 40 else "")) + "。\n\n"
        "当前 AI 服务未提供可用的大模型密钥，暂由本地策略引擎应答。通用建议如下：\n"
        "1. 先明确本週唯一的关键目标，把 70% 的学习时间投入其中；\n"
        "2. 每天固定两个 25 分钟的深度专注时段，避免碎片化学习；\n"
        "3. 每次学习结束后用 3 分钟复盘产出（笔记/错题/总结），形成闭环；\n"
        "4. 每周日做一次进度对齐，落后超过 20% 就调整计划而非硬扛。\n\n"
        "配置 ARK_API_KEY 后可获得基于你个人学习数据的个性化分析与规划。"
    )

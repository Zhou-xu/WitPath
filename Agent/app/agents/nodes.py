"""多 Agent 节点实现。

每一个函数对应 LangGraph 中的一个节点，职责单一：
- data_collect_agent : 整理学习数据（不调用模型）
- analysis_agent     : 学习态势分析
- planning_agent     : 生成学习计划
- retrieval_agent    : 检索知识资料
- advice_agent       : 生成改进建议
- summary_agent      : 整合最终结论

所有节点都会向 Spring Boot 回推阶段性进度，供前端展示实时状态。
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any, Dict, List

from app.agents import prompts
from app.agents.llm import ENGINE_RULE, get_model
from app.agents.state import AnalysisState, STAGE_LABELS, STAGE_PROGRESS
from app.services.callback import push_progress

logger = logging.getLogger(__name__)


def _emit(state: AnalysisState, stage: str, message: str = "") -> AnalysisState:
    """记录阶段并回调 Spring Boot。"""
    stages = list(state.get("stages") or [])
    stages.append(
        {
            "stage": stage,
            "message": message or STAGE_LABELS.get(stage, stage),
            "progress": STAGE_PROGRESS.get(stage, 0),
        }
    )
    state["stages"] = stages
    push_progress(
        callback_url=state.get("callback_url"),
        callback_token=state.get("callback_token"),
        task_no=state.get("task_no"),
        stage=stage,
        stage_message=message or STAGE_LABELS.get(stage, stage),
        progress=STAGE_PROGRESS.get(stage, 0),
    )
    return state


def data_collect_agent(state: AnalysisState) -> AnalysisState:
    """数据整理 Agent：把请求体压成一份可读的事实清单。"""
    payload = state.get("payload") or {}
    brief = prompts.build_data_brief(payload)
    state["brief"] = brief
    return _emit(state, "data_collect", "已完成学习数据整理")


def analysis_agent(state: AnalysisState) -> AnalysisState:
    """学习态势分析 Agent。"""
    model = get_model()
    if model.engine == ENGINE_RULE:
        state["analysis"] = _rule_analysis(state)
    else:
        prompt = prompts.ANALYSIS_PROMPT.format(
            brief=state.get("brief", ""), question=state.get("question") or "无"
        )
        content = model.invoke(prompt)
        state["analysis"] = content or _rule_analysis(state)
        state["tokens_used"] = state.get("tokens_used", 0) + getattr(model, "total_tokens", 0)
    state["model"] = model.model_name
    return _emit(state, "analysis", "已完成学习态势分析")


def planning_agent(state: AnalysisState) -> AnalysisState:
    """学习规划 Agent：输出结构化计划项。"""
    model = get_model()
    plan: List[Dict[str, Any]] = []
    raw = ""
    if model.engine != ENGINE_RULE:
        prompt = prompts.PLANNING_PROMPT.format(
            brief=state.get("brief", ""), analysis=state.get("analysis", "")
        )
        raw = model.invoke(prompt) or ""
        state["tokens_used"] = state.get("tokens_used", 0) + getattr(model, "total_tokens", 0)

    plan = _parse_plan(raw) or _rule_plan(state)
    state["plan_raw"] = raw
    state["plan"] = plan
    return _emit(state, "planning", f"已生成 {len(plan)} 项学习计划")


def retrieval_agent(state: AnalysisState) -> AnalysisState:
    """知识检索 Agent：从向量库中取与当前问题相关的资料片段。"""
    payload = state.get("payload") or {}
    query = state.get("question") or build_query_from_goals(payload)
    try:
        from app.services.retriever import search_similar

        hits = search_similar(
            user_id=payload.get("user_id"),
            query=query,
            top_k=4,
        )
    except Exception as exc:  # noqa: BLE001
        logger.warning("知识检索失败，跳过该阶段：%s", exc)
        hits = []

    state["knowledge"] = hits
    return _emit(state, "retrieval", f"检索到 {len(hits)} 条相关知识片段")


def advice_agent(state: AnalysisState) -> AnalysisState:
    """学习建议 Agent：综合分析与计划生成改进建议。"""
    model = get_model()
    knowledge_text = "\n".join(
        f"- {item.get('source', '')}：{item.get('snippet', '')}" for item in (state.get("knowledge") or [])
    ) or "（本次未检索到相关资料）"

    if model.engine == ENGINE_RULE:
        state["advice"] = _rule_advice(state)
    else:
        prompt = prompts.ADVICE_PROMPT.format(
            analysis=state.get("analysis", ""),
            plan=json.dumps(state.get("plan") or [], ensure_ascii=False),
            knowledge=knowledge_text,
        )
        content = model.invoke(prompt)
        state["advice"] = content or _rule_advice(state)
        state["tokens_used"] = state.get("tokens_used", 0) + getattr(model, "total_tokens", 0)

    state["risks"] = _extract_risks(state)
    return _emit(state, "advice", "已生成改进建议")


def summary_agent(state: AnalysisState) -> AnalysisState:
    """结果整合 Agent：产出摘要与 Markdown 建议文档。"""
    model = get_model()
    plan_titles = (
        "、".join(str(item.get("title")) for item in (state.get("plan") or [])) or "暂无"
    )
    if model.engine == ENGINE_RULE:
        state["summary"] = _rule_summary(state)
    else:
        prompt = prompts.SUMMARY_PROMPT.format(
            analysis=state.get("analysis", ""),
            advice=state.get("advice", ""),
            plan_title_list=plan_titles,
        )
        content = model.invoke(prompt)
        state["summary"] = content or _rule_summary(state)
        state["tokens_used"] = state.get("tokens_used", 0) + getattr(model, "total_tokens", 0)

    state["suggestion"] = _build_suggestion_markdown(state)
    return _emit(state, "summary", "已整合最终结论")


# ---------------------------------------------------------------------------
# 规则引擎产物（无 LLM 时的确定性输出）
# ---------------------------------------------------------------------------


def _rule_analysis(state: AnalysisState) -> str:
    payload = state.get("payload") or {}
    summary = payload.get("summary") or {}
    goals = payload.get("goals") or []
    tasks = payload.get("tasks") or []

    total_minutes = int(summary.get("total_minutes") or 0)
    days = int(summary.get("range_days") or 14)
    avg_per_day = round(total_minutes / days, 1) if days else 0.0
    avg_focus = float(summary.get("avg_focus") or 0)
    overdue = [t for t in tasks if t.get("overdue")]
    done_tasks = [t for t in tasks if t.get("status") == 3]

    lines = [
        f"窗口 {days} 天内累计学习 {total_minutes} 分钟，日均约 {avg_per_day} 分钟。",
        f"平均专注度 {avg_focus} / 5" + ("，处于偏低水平，建议缩短单次学习时长提升质量。" if avg_focus and avg_focus < 3 else "，专注质量良好。"),
        f"任务完成 {len(done_tasks)} / {len(tasks)}" + (f"，其中 {len(overdue)} 项已逾期，需要优先处理。" if overdue else "，暂无逾期任务。"),
    ]
    if goals:
        avg_progress = sum(float(g.get("progress") or 0) for g in goals) / len(goals)
        lines.append(f"进行中目标 {len(goals)} 个，平均进度 {round(avg_progress, 1)}%。")
    else:
        lines.append("尚未创建学习目标，建议先明确阶段性目标再安排任务。")
    return "\n".join(f"- {line}" for line in lines)


def _rule_plan(state: AnalysisState) -> List[Dict[str, Any]]:
    payload = state.get("payload") or {}
    tasks = payload.get("tasks") or []
    goals = payload.get("goals") or []
    plan: List[Dict[str, Any]] = []

    overdue = [t for t in tasks if t.get("overdue")][:3]
    for task in overdue:
        plan.append(
            {
                "title": f"补齐逾期任务：{task.get('title')}",
                "detail": "拆成 2 个 25 分钟专注时段完成，开始前先回顾原始要求。",
                "due_date": None,
                "estimated_minutes": int(task.get("estimated_minutes") or 50),
                "priority": "高",
            }
        )

    for goal in goals[:3]:
        total = int(goal.get("task_total") or 0)
        done = int(goal.get("task_done") or 0)
        remaining = max(total - done, 0)
        plan.append(
            {
                "title": f"推进目标：{goal.get('title')}",
                "detail": f"剩余 {remaining} 个任务，本周完成至少 {max(1, remaining // 2 if remaining else 1)} 个。",
                "due_date": goal.get("end_date"),
                "estimated_minutes": 90,
                "priority": "中",
            }
        )

    if not plan:
        plan.append(
            {
                "title": "建立学习节奏",
                "detail": "每天固定 60 分钟深度专注，先建立可持续的节奏再加大强度。",
                "due_date": None,
                "estimated_minutes": 60,
                "priority": "中",
            }
        )
    return plan


def _rule_advice(state: AnalysisState) -> str:
    payload = state.get("payload") or {}
    summary = payload.get("summary") or {}
    total_minutes = int(summary.get("total_minutes") or 0)
    days = int(summary.get("range_days") or 14)
    avg_per_day = total_minutes / days if days else 0
    advice_items: List[str] = []

    if avg_per_day < 45:
        advice_items.append(
            "- **提高投入密度**：当前日均学习不足 45 分钟，建议先固定每天 2 个 25 分钟番茄钟，"
            "把学习时间锚定在固定时段而非零散碎片。"
        )
    else:
        advice_items.append(
            f"- **保持稳定节奏**：当前日均 {round(avg_per_day)} 分钟，投入充足，建议把重心转向产出质量"
            "（错题复盘、输出笔记）而非单纯堆时长。"
        )

    overdue = [t for t in (payload.get("tasks") or []) if t.get("overdue")]
    if overdue:
        advice_items.append(
            f"- **清理逾期任务**：目前 {len(overdue)} 项任务逾期，逾期任务会持续占用心理带宽，"
            "建议优先结清或重新评估截止日期。"
        )

    goals = payload.get("goals") or []
    if not goals:
        advice_items.append(
            "- **先定目标再谈效率**：尚未创建学习目标，建议补充 1~3 个有明确截止日期的阶段目标，"
            "后续的所有统计才有衡量基准。"
        )
    elif goals:
        slowest = min(goals, key=lambda g: float(g.get("progress") or 0))
        advice_items.append(
            f"- **关注进度最慢的目标**：「{slowest.get('title')}」进度 {slowest.get('progress')}%，"
            "建议拆出本周可完成的最小里程碑。"
        )

    if float(summary.get("avg_focus") or 0) and float(summary.get("avg_focus")) < 3:
        advice_items.append(
            "- **降低干扰源**：平均专注度偏低，尝试把手机移出视线并使用番茄钟，"
            "单次学习控制在 45 分钟内。"
        )
    return "\n".join(advice_items)


def _rule_summary(state: AnalysisState) -> str:
    payload = state.get("payload") or {}
    summary = payload.get("summary") or {}
    lines = [
        f"近 {summary.get('range_days', 14)} 天累计学习 {summary.get('total_minutes', 0)} 分钟，"
        f"记录 {summary.get('record_count', 0)} 条，平均专注度 {summary.get('avg_focus', 0)} / 5。"
    ]
    risks = state.get("risks") or []
    if risks:
        lines.append("主要风险：" + "；".join(risks[:3]) + "。")
    plan = state.get("plan") or []
    if plan:
        lines.append("下一步优先做：" + "、".join(str(p.get("title")) for p in plan[:2]) + "。")
    return "".join(lines)


# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------


def build_query_from_goals(payload: Dict[str, Any]) -> str:
    goal_titles = [str(g.get("title")) for g in (payload.get("goals") or []) if g.get("title")]
    tags = (payload.get("profile") or {}).get("preferred_tags") or []
    parts = goal_titles[:2] + [str(t) for t in tags[:2]]
    return " ".join(parts) or "学习方法与效率提升"


def _parse_plan(raw: str) -> List[Dict[str, Any]]:
    """解析 `标题 | 说明 | 分钟` 格式的计划项。"""
    if not raw:
        return []
    plan: List[Dict[str, Any]] = []
    for line in raw.splitlines():
        line = line.strip().lstrip("-•* ").strip()
        if "|" not in line:
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 2:
            continue
        minutes = 60
        if len(parts) >= 3:
            digits = "".join(ch for ch in parts[2] if ch.isdigit())
            minutes = int(digits) if digits else 60
        plan.append(
            {
                "title": parts[0] or "未命名任务",
                "detail": parts[1],
                "due_date": None,
                "estimated_minutes": minutes,
                "priority": "中",
            }
        )
    return plan


def _extract_risks(state: AnalysisState) -> List[str]:
    payload = state.get("payload") or {}
    risks: List[str] = []
    tasks = payload.get("tasks") or []
    overdue = [t for t in tasks if t.get("overdue")]
    if overdue:
        risks.append(f"{len(overdue)} 项任务已逾期")
    summary = payload.get("summary") or {}
    days = int(summary.get("range_days") or 14)
    avg_per_day = (int(summary.get("total_minutes") or 0) / days) if days else 0
    if avg_per_day < 30:
        risks.append("日均学习时长偏低，目标达成存在延期风险")
    for goal in payload.get("goals") or []:
        if goal.get("end_date") and float(goal.get("progress") or 0) < 30:
            risks.append(f"目标「{goal.get('title')}」剩余时间不足且进度偏低")
    if float(summary.get("avg_focus") or 0) and float(summary.get("avg_focus")) < 3:
        risks.append("平均专注度偏低，学习效果可能打折")
    return risks


def _build_suggestion_markdown(state: AnalysisState) -> str:
    sections = ["### 学习态势分析", state.get("analysis") or "（无）", ""]
    plan = state.get("plan") or []
    if plan:
        sections.append("### 接下来 7 天的安排")
        for item in plan:
            sections.append(
                f"- **{item.get('title')}**：{item.get('detail')} "
                f"（建议 {item.get('estimated_minutes', 0)} 分钟）"
            )
        sections.append("")
    knowledge = state.get("knowledge") or []
    if knowledge:
        sections.append("### 可参考的资料")
        for item in knowledge:
            sections.append(f"- {item.get('source')}：{item.get('snippet')}")
        sections.append("")
    sections.append("### 改进建议")
    sections.append(state.get("advice") or "（无）")
    return "\n".join(sections)


def timed(func):
    """记录节点耗时，便于排查性能瓶颈。"""

    def wrapper(state: AnalysisState) -> AnalysisState:
        start = time.perf_counter()
        try:
            return func(state)
        finally:
            cost_ms = int((time.perf_counter() - start) * 1000)
            logger.debug("节点 %s 耗时 %d ms", func.__name__, cost_ms)

    wrapper.__name__ = func.__name__
    return wrapper

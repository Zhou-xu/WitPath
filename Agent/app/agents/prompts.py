"""各 Agent 节点使用的提示词模板与规则化文本生成。

当 LLM 可用时使用 LLM_PROMPTS；不可用时调用对应的 rule_* 函数生成
确定性、可解释的分析内容，保证服务始终有真实输出。
"""

from __future__ import annotations

from typing import Any, Dict, List

NICKNAME_PLACEHOLDER = "同学"


def _fmt_number(value: float) -> str:
    return f"{value:.1f}".rstrip("0").rstrip(".") if isinstance(value, float) else str(value)


def build_data_brief(payload: Dict[str, Any]) -> str:
    """把结构化数据整理成供模型/规则阅读的简要事实清单。"""
    summary = payload.get("summary") or {}
    goals = payload.get("goals") or []
    tasks = payload.get("tasks") or []
    records = payload.get("records") or []
    profile = payload.get("profile") or {}

    lines: List[str] = []
    lines.append(f"学习者：{profile.get('nickname') or NICKNAME_PLACEHOLDER}"
                 f"（阶段：{profile.get('study_stage') or '未填写'}）")
    lines.append(f"当前目标：{profile.get('study_goal') or '未填写'}")
    lines.append(f"偏好标签：{', '.join(profile.get('preferred_tags') or []) or '无'}")
    lines.append("")
    lines.append(f"统计窗口：{summary.get('range_days', 14)} 天"
                 f"（{summary.get('from')} ~ {summary.get('to')}）")
    total_minutes = int(summary.get("total_minutes") or 0)
    lines.append(f"总学习时长：{total_minutes} 分钟（约 {_fmt_number(total_minutes / 60)} 小时）")
    lines.append(f"学习记录：{summary.get('record_count', 0)} 条；平均专注度：{summary.get('avg_focus', 0)} / 5")
    lines.append("")
    if goals:
        lines.append("进行中的目标：")
        for goal in goals[:8]:
            lines.append(
                f"  - {goal.get('title')}：目标 {goal.get('target_hours', 0)} 小时，"
                f"已完成 {goal.get('actual_hours', 0)} 小时，进度 {goal.get('progress', 0)}%，"
                f"任务 {goal.get('task_done', 0)}/{goal.get('task_total', 0)}，"
                f"截止 {goal.get('end_date') or '未设置'}"
            )
    else:
        lines.append("进行中的目标：暂无")
    if tasks:
        overdue = [t for t in tasks if t.get("overdue")]
        lines.append("")
        lines.append(f"任务总数 {len(tasks)}，其中已逾期 {len(overdue)} 项：")
        for task in overdue[:8]:
            lines.append(f"  - {task.get('title')}（截止 {task.get('due_date')}）")
    if records:
        lines.append("")
        lines.append("近期学习记录（最多 12 条）：")
        for record in records[:12]:
            lines.append(
                f"  - {record.get('study_date')} {record.get('title')} "
                f"{record.get('duration_minutes', 0)} 分钟 专注度 {record.get('focus_score', 0)}"
                f"（{record.get('subject') or '未分类'}）"
            )
    materials = payload.get("material_snippets") or []
    if materials:
        lines.append("")
        lines.append("已有学习资料：" + "、".join(str(m) for m in materials[:10]))
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# LLM 提示词模板
# ---------------------------------------------------------------------------

ANALYSIS_PROMPT = """你是学习数据分析 Agent。请基于以下学习事实，给出 3~5 条客观结论，每条不超过 30 字，"
要求：只描述数据反映的事实与趋势，不要给出建议，不要输出 Markdown 标题。

学习事实：
{brief}

用户补充问题：{question}

请以 "- " 开头的列表输出结论。"""

PLANNING_PROMPT = """你是学习规划 Agent。请基于学习事实，为接下来 7 天安排学习计划，输出 3~5 条。
每条严格使用格式：标题 | 具体动作说明 | 建议用时(分钟)
示例：完成线性代数习题 | 每天两小节，配合错题本复盘 | 90

学习事实：
{brief}

已有阶段性分析：
{analysis}"""

ADVICE_PROMPT = """你是学习策略建议 Agent。请结合分析与计划，输出 2~4 条可执行改进建议，"
每条包含：建议点 + 具体做法，控制在 60 字内，用 Markdown 无序列表输出。

分析：
{analysis}

计划：
{plan}

检索到的资料要点：
{knowledge}"""

SUMMARY_PROMPT = """你是结果整合 Agent。请把以下内容整合为一段不超过 150 字的中文总结，
语气客观克制，突出最关键的学习态势与下一步重点。

分析：{analysis}
建议：{advice}
计划：{plan_title_list}"""

CHAT_SYSTEM_PROMPT = """你是学途知行平台的 AI 学习助手。请用中文回答，简洁、具体、可执行。
优先结合用户提供的学习上下文；若上下文不足，则给出通用但可落地的方法。
检索到的资料片段若有帮助，需自然引用其来源。"""

CHAT_PROMPT = """学习上下文摘要：
{context}

历史对话：
{history}

用户提问：
{message}"""

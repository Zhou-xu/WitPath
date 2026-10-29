"""LangGraph 多 Agent 工作流编排。

节点流转：
    data_collect -> analysis -> planning -> retrieval -> advice -> summary

其中 retrieval 为条件节点：当用户没有任何学习资料时跳过，
避免无效调用向量库。
"""

from __future__ import annotations

import logging
from typing import Any, Optional

from app.agents.nodes import (
    advice_agent,
    analysis_agent,
    data_collect_agent,
    planning_agent,
    retrieval_agent,
    summary_agent,
)
from app.agents.state import AnalysisState
from app.services.memory import build_checkpointer

logger = logging.getLogger(__name__)

_graph_cache: Any = None


def _should_skip_retrieval(state: AnalysisState) -> str:
    """没有资料时跳过检索节点。"""
    payload = state.get("payload") or {}
    materials = payload.get("material_snippets") or []
    if not materials:
        return "skip"
    return "run"


def build_graph():
    """编译并返回 LangGraph 工作流（不可用时返回 None，由调用方降级）。"""
    try:
        from langgraph.graph import END, StateGraph
    except ImportError:
        logger.warning("langgraph 未安装，AI 服务将以线性降级模式运行")
        return None

    workflow = StateGraph(AnalysisState)
    workflow.add_node("data_collect", data_collect_agent)
    workflow.add_node("analysis", analysis_agent)
    workflow.add_node("planning", planning_agent)
    workflow.add_node("retrieval", retrieval_agent)
    workflow.add_node("advice", advice_agent)
    workflow.add_node("summary", summary_agent)

    workflow.set_entry_point("data_collect")
    workflow.add_edge("data_collect", "analysis")
    workflow.add_edge("analysis", "planning")
    workflow.add_conditional_edges(
        "planning",
        _should_skip_retrieval,
        {"run": "retrieval", "skip": "advice"},
    )
    workflow.add_edge("retrieval", "advice")
    workflow.add_edge("advice", "summary")
    workflow.add_edge("summary", END)

    checkpointer = build_checkpointer()
    return workflow.compile(checkpointer=checkpointer)


def get_graph():
    global _graph_cache
    if _graph_cache is None:
        _graph_cache = build_graph()
    return _graph_cache


def run_fallback(state: AnalysisState) -> AnalysisState:
    """LangGraph 不可用时的线性等价实现，保证节点逻辑仍然被执行。"""
    state = data_collect_agent(state)
    state = analysis_agent(state)
    state = planning_agent(state)
    if _should_skip_retrieval(state) == "run":
        state = retrieval_agent(state)
    state = advice_agent(state)
    state = summary_agent(state)
    return state


def run_analysis(initial_state: AnalysisState, thread_id: Optional[str] = None) -> AnalysisState:
    """执行完整分析工作流。"""
    graph = get_graph()
    config = {"configurable": {"thread_id": thread_id or (initial_state.get("task_no") or "default")}}
    if graph is None:
        return run_fallback(initial_state)

    final_state = initial_state
    for chunk in graph.stream(initial_state, config, stream_mode="values"):
        final_state = chunk
    return final_state

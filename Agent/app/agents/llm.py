"""大模型工厂：火山方舟（Ark）OpenAI 兼容接口 + 无密钥时的规则引擎兜底。

设计取舍：
- 首选方案：langchain_openai.ChatOpenAI 指向火山方舟 /api/v3，
  优点是与 LangChain / LangGraph 生态完全打通，可直接作为 Agent 的推理核心。
- 兜底方案：当未配置 ARK_API_KEY 或依赖缺失时，返回一个本地规则推理器，
  保证服务可用、可演示，且失败原因透明可见（model 字段会标注 engine 类型）。
"""

from __future__ import annotations

import logging
from typing import Any, List, Optional

from app.config import settings

logger = logging.getLogger(__name__)

ENGINE_LLM = "llm"
ENGINE_RULE = "rule-engine"


class _BaseModel:
    """统一的模型接口：invoke(prompt) -> str。"""

    engine: str = ENGINE_RULE
    model_name: str = "rule-engine"

    def invoke(self, prompt: str) -> str:  # pragma: no cover - 接口定义
        raise NotImplementedError


class RuleBasedModel(_BaseModel):
    """无 LLM 时的确定性兜底推理器。

    它不是"假的 AI"，而是把规则写在服务内的可解释分析器：
    在密钥缺失环境下依然返回真实可用的结论，并在 model 字段中标注来源。
    """

    engine = ENGINE_RULE

    def __init__(self, model_name: str = "rule-engine"):
        self.model_name = model_name

    def invoke(self, prompt: str) -> str:
        # 规则引擎不消费 prompt，交由各节点的模板逻辑生成内容
        return ""


class LangChainModel(_BaseModel):
    """基于 LangChain ChatOpenAI 的真实大模型调用。"""

    engine = ENGINE_LLM

    def __init__(self, chat_model: Any, model_name: str):
        self._chat_model = chat_model
        self.model_name = model_name
        self.tokens_used = 0

    def invoke(self, prompt: str) -> str:
        from langchain_core.messages import HumanMessage, SystemMessage

        messages = [
            SystemMessage(content="你是学途知行平台的学习分析助手，回答请简洁、结构化、可执行。"),
            HumanMessage(content=prompt),
        ]
        result = self._chat_model.invoke(messages)
        content = getattr(result, "content", "")
        # 部分平台返回嵌套结构，做一次归一化
        if isinstance(content, list):
            text_parts: List[str] = []
            for part in content:
                if isinstance(part, dict):
                    text_parts.append(str(part.get("text", "")))
                else:
                    text_parts.append(str(part))
            content = "\n".join(text_parts)
        usage = getattr(result, "response_metadata", {}) or {}
        token_usage = usage.get("token_usage") or usage.get("usage") or {}
        if isinstance(token_usage, dict):
            self.tokens_used += int(token_usage.get("total_tokens") or 0)
        return str(content)

    @property
    def total_tokens(self) -> int:
        return self.tokens_used


def build_model(api_key: Optional[str] = None, base_url: Optional[str] = None,
                model_name: Optional[str] = None) -> _BaseModel:
    """按配置构建可用的推理模型（失败自动降级，不抛异常）。

    参数为空时回落到服务端 .env 中的 ARK_* 配置，保证原有部署方式不受影响。
    """
    api_key = (api_key or settings.ark_api_key or "").strip()
    base_url = (base_url or settings.ark_base_url or "").strip()
    model_name = (model_name or settings.ark_model or "").strip()

    if not api_key:
        logger.warning("未配置大模型密钥（ARK_API_KEY 或用户个人配置），AI 服务将使用本地规则引擎运行")
        return RuleBasedModel()

    try:
        from langchain_openai import ChatOpenAI
    except ImportError:
        logger.warning("langchain-openai 未安装，降级为规则引擎；请执行 pip install -r requirements.txt")
        return RuleBasedModel()

    try:
        chat_model = ChatOpenAI(
            model=model_name,
            base_url=base_url,
            api_key=api_key,
            temperature=0.4,
            timeout=90,
            max_retries=1,
        )
        logger.info("已加载大模型：model=%s base=%s", model_name, base_url)
        return LangChainModel(chat_model, model_name)
    except Exception as exc:  # noqa: BLE001
        logger.warning("大模型初始化失败，降级为规则引擎：%s", exc)
        return RuleBasedModel()


_model_instance: Any = None
# 用户个人模型实例缓存：key = (api_key, base_url, model)，避免每个请求重复建客户端
_override_instances: dict = {}


def get_model(override: Any = None) -> Any:
    """获取推理模型。

    override 为用户个人配置（UserModelConfig）时优先使用其 key / 地址 / 模型；
    未传或配置不完整时使用服务端默认配置（单例）。
    """
    global _model_instance

    if override is not None and getattr(override, "has_llm", False):
        cache_key = (override.llm_api_key, override.llm_base_url, override.llm_model)
        model = _override_instances.get(cache_key)
        if model is None:
            model = build_model(override.llm_api_key, override.llm_base_url, override.llm_model)
            _override_instances[cache_key] = model
        return model

    if _model_instance is None:
        _model_instance = build_model()
    return _model_instance


def override_cache_size() -> int:
    """测试与排障用：当前缓存的用户模型实例数量。"""
    return len(_override_instances)


def reset_model() -> None:
    """测试或配置变更后重建模型实例。"""
    global _model_instance
    _model_instance = None
    _override_instances.clear()

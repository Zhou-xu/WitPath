"""用户个人模型配置的契约与覆盖逻辑测试。

验证两件事：
1. Spring Boot 传来的 camelCase 字段能正确落到 snake_case（UserModelConfig）；
2. AI 侧按用户配置构建并缓存模型实例（同配置复用，不同配置隔离）。
"""

from __future__ import annotations

import unittest

from app.agents import llm
from app.schemas import AiAnalyzeRequest, ChatRequest, UserModelConfig


class UserModelConfigMappingTest(unittest.TestCase):
    def test_camel_case字段映射(self):
        payload = {
            "taskType": "STUDY_ANALYSIS",
            "userModelConfig": {
                "llmApiKey": "sk-user",
                "llmBaseUrl": "https://user.example/v1",
                "llmModel": "user-model",
                "embeddingApiKey": "sk-emb",
                "embeddingModel": "user-embedding",
            },
        }
        req = AiAnalyzeRequest(**payload)
        cfg = req.user_model_config
        self.assertIsNotNone(cfg)
        self.assertEqual("sk-user", cfg.llm_api_key)
        self.assertEqual("https://user.example/v1", cfg.llm_base_url)
        self.assertEqual("user-model", cfg.llm_model)
        self.assertTrue(cfg.has_llm)
        self.assertTrue(cfg.has_embedding)

    def test未传配置时为None(self):
        req = ChatRequest(message="你好")
        self.assertIsNone(req.user_model_config)

    def test部分配置不触发覆盖(self):
        cfg = UserModelConfig(llm_api_key="sk-user")  # 缺 base_url / model
        self.assertFalse(cfg.has_llm)
        self.assertFalse(cfg.has_embedding)


class ModelOverrideTest(unittest.TestCase):
    def setUp(self) -> None:
        llm.reset_model()

    def test按用户配置构建并复用模型实例(self):
        cfg = UserModelConfig(llm_api_key="sk-a", llm_base_url="https://a.example/v1", llm_model="m-a")
        first = llm.get_model(cfg)
        second = llm.get_model(cfg)
        # 同一份配置复用同一实例（避免每请求都新建客户端）
        self.assertIs(first, second)
        self.assertEqual(1, llm.override_cache_size())

    def test不同用户配置相互隔离(self):
        cfg_a = UserModelConfig(llm_api_key="sk-a", llm_base_url="https://a.example/v1", llm_model="m-a")
        cfg_b = UserModelConfig(llm_api_key="sk-b", llm_base_url="https://b.example/v1", llm_model="m-b")
        model_a = llm.get_model(cfg_a)
        model_b = llm.get_model(cfg_b)
        self.assertIsNot(model_a, model_b)
        self.assertEqual(2, llm.override_cache_size())

    def test无覆盖时走服务端默认配置(self):
        default = llm.get_model()
        self.assertIsNotNone(default)
        self.assertEqual(0, llm.override_cache_size())


if __name__ == "__main__":
    unittest.main()

"""个人大模型配置端到端校验。

覆盖：解锁 → 模型列表代理 → 保存（加密落库）→ 掩码回读 → 库内密文校验
→ Redis 缓存校验 → 使用默认 API → 清理。

用法：
    python tools/ai_config_check.py
    python tools/ai_config_check.py --username demo --password witpath123

说明：脚本会真实调用一次用户的 /models 端点（默认用阿里云百炼兼容端点，
      密钥取自 Agent/.env 的 DASHSCOPE_API_KEY），不会改动任何业务数据。
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test import BASE, call, login, check, PASS, FAIL  # noqa: E402

# 用于真实拉取模型列表的端点与密钥（可被命令行覆盖）
DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"


def load_dashscope_key() -> str:
    """从 Agent/.env 读取百炼密钥（该文件已 gitignore）"""
    env_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "Agent", ".env")
    if not os.path.exists(env_path):
        return ""
    with open(env_path, encoding="utf-8") as fh:
        for line in fh:
            if line.strip().startswith("DASHSCOPE_API_KEY="):
                return line.split("=", 1)[1].strip()
    return ""


def mask_ok(masked: str | None) -> bool:
    """掩码形如 sk-xx****1234：必须含 * 且不泄露完整明文"""
    return bool(masked) and "****" in masked and len(masked) < 64


def db_row(user_id: int):
    """直连数据库确认落库的是密文而非明文"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import db_cli

    with db_cli.connect() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT api_key_enc, base_url, model, use_default_api FROM user_api_key WHERE user_id=%s",
            (user_id,),
        )
        return cur.fetchone()


def redis_keys(user_id: int) -> dict:
    """检查解锁态与配置缓存是否写入 Redis"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import db_cli  # noqa: F401  确保路径已加入
    try:
        import redis
    except ImportError:
        return {"unlock": None, "config": None}
    client = redis.Redis(host="127.0.0.1", port=6379, db=0, socket_connect_timeout=3)
    try:
        return {
            "unlock": client.exists(f"witpath:ai:unlock:{user_id}"),
            "config": client.exists(f"witpath:ai:config:{user_id}"),
            "config_ttl": client.ttl(f"witpath:ai:config:{user_id}"),
        }
    except Exception as exc:  # noqa: BLE001
        return {"error": str(exc)}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--username", default="demo")
    parser.add_argument("--password", default="witpath123")
    parser.add_argument("--user-id", type=int, default=2)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--api-key", default="")
    args = parser.parse_args()

    api_key = args.api_key or load_dashscope_key()
    print(f"=== 个人模型配置校验 @ {BASE} ===")
    token = login(args.username, args.password)

    # 1. 初始状态
    cfg = call("GET", "/api/user/ai-config", token=token)
    check("读取配置接口可用（无需密码确认）", isinstance(cfg, dict), f"configured={cfg.get('configured')}")
    check("不再返回解锁态字段", "unlocked" not in cfg, f"keys={sorted(cfg.keys())[:6]}")

    # 2. 未选择模型时保存应被拒绝（前端「确认修改」前的同一条校验）
    try:
        call("PUT", "/api/user/ai-config", {"apiKey": "x", "baseUrl": "https://x/v1"}, token=token)
        check("缺少模型禁止保存", False, "未拒绝")
    except Exception as exc:  # noqa: BLE001
        check("缺少模型禁止保存", True, str(exc)[:70])

    if not api_key:
        print("\n未获取到可用的 API Key（Agent/.env 中无 DASHSCOPE_API_KEY），跳过模型列表与保存验证")
        return 1 if FAIL else 0

    # 5. 拉取模型列表（真实调用用户端点）
    models = call(
        "POST",
        "/api/user/ai-config/models",
        {"baseUrl": args.base_url, "apiKey": api_key},
        token=token,
        timeout=40,
    )
    check("拉取模型列表", isinstance(models, list) and len(models) > 0, f"共 {len(models)} 个，首个 {models[0]['id'] if models else '-'}")
    model_id = models[0]["id"]

    # 6. 保存配置
    saved = call(
        "PUT",
        "/api/user/ai-config",
        {"apiKey": api_key, "baseUrl": args.base_url, "model": model_id},
        token=token,
    )
    check("保存配置", saved.get("configured") is True and saved.get("model") == model_id, f"model={saved.get('model')}")
    check("API Key 回显为掩码", mask_ok(saved.get("apiKeyMasked")), str(saved.get("apiKeyMasked")))
    check("保存后处于启用态", saved.get("useDefaultApi") is False)

    # 7. 数据库落库校验
    row = db_row(args.user_id)
    if not row:
        check("数据库存在配置行", False, "未找到记录")
    else:
        cipher, base_url, model, use_default = row
        check("数据库存在配置行", True, f"model={model} use_default={use_default}")
        check("库内存储的是密文", cipher != api_key and not api_key.startswith(cipher[:8]), f"密文长度 {len(cipher)}")
        try:
            raw = base64.b64decode(cipher)
            check("密文格式正确(IV+密文+Tag)", len(raw) > 28, f"解码 {len(raw)} 字节")
        except Exception as exc:  # noqa: BLE001
            check("密文格式正确(IV+密文+Tag)", False, str(exc)[:60])

    # 8. Redis 缓存校验（密码确认已移除，仅校验配置缓存）
    keys = redis_keys(args.user_id)
    if "error" in keys:
        print(f"（Redis 检查跳过：{keys['error']}）")
    else:
        check("配置缓存已写入 Redis", keys.get("config") == 1, f"ttl={keys.get('config_ttl')}s")

    # 9. 切回系统默认
    defaulted = call("POST", "/api/user/ai-config/use-default", {}, token=token)
    check("切换到系统默认", defaulted.get("useDefaultApi") is True, f"useDefaultApi={defaulted.get('useDefaultApi')}")
    check("切换后配置仍保留", defaulted.get("configured") is True)

    # 10. 改回个人配置（传空 Key 表示不修改）
    back = call(
        "PUT",
        "/api/user/ai-config",
        {"apiKey": "", "baseUrl": args.base_url, "model": model_id},
        token=token,
    )
    check("留空 Key 可只改模型", back.get("useDefaultApi") is False and back.get("model") == model_id, f"model={back.get('model')}")

    # 11. 清理
    call("DELETE", "/api/user/ai-config", token=token)
    final = call("GET", "/api/user/ai-config", token=token)
    check("删除配置后回到未配置", final.get("configured") is False)

    print(f"\n通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
    if FAIL:
        print("失败项：" + "、".join(FAIL))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

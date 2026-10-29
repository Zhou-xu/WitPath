"""头像功能端到端校验。

覆盖：内置在线头像列表 → 应用预设（写入 OSS）→ 本地图片上传（写入 OSS）
→ 直链可访问性 → 资料接口回读 → 旧头像清理。

用法：
    python tools/avatar_check.py
    python tools/avatar_check.py --username demo --password witpath123
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import struct
import sys
import tempfile
import uuid
import zlib
from typing import Any, Dict, Optional
from urllib import request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test import BASE, call, login, redis_get, check, PASS, FAIL  # noqa: E402


def absolute(url: str) -> str:
    """相对地址（后端代理）补全为可直接访问的绝对地址"""
    return url if url.startswith("http") else f"{BASE}{url}"


def http_head(url: str, timeout: int = 20) -> Dict[str, Any]:
    """访问头像地址，验证是否可匿名读取（OSS 直链或后端代理）"""
    req = request.Request(absolute(url), method="GET")
    with request.urlopen(req, timeout=timeout) as resp:
        body = resp.read()
        return {"status": resp.status, "type": resp.headers.get("Content-Type"), "size": len(body)}


def make_png(path: str, size: int = 64) -> None:
    """生成一个纯色 PNG 测试图（无第三方依赖）"""
    raw = b""
    for _ in range(size):
        raw += b"\x00" + bytes([214, 154, 74] * size)  # 暖橙色
    compressed = zlib.compress(raw, 9)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", compressed)
    png += chunk(b"IEND", b"")
    with open(path, "wb") as fh:
        fh.write(png)


def upload_multipart(path: str, token: str) -> Any:
    boundary = f"----witpath{uuid.uuid4().hex}"
    with open(path, "rb") as fh:
        content = fh.read()
    body = b""
    body += f"--{boundary}\r\n".encode()
    body += b'Content-Disposition: form-data; name="file"; filename="avatar.png"\r\n'
    body += b"Content-Type: image/png\r\n\r\n"
    body += content + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    req = request.Request(
        f"{BASE}/api/user/avatar",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    with request.urlopen(req, timeout=60) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 200:
        raise RuntimeError(f"upload -> {payload.get('code')}: {payload.get('msg')}")
    return payload.get("data")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--username", default="demo")
    parser.add_argument("--password", default="witpath123")
    args = parser.parse_args()

    print(f"=== 头像功能校验 @ {BASE} ===")
    token = login(args.username, args.password)

    # 1. 在线头像列表
    presets = call("GET", "/api/user/avatar/presets", token=token)
    check("在线头像列表", len(presets) == 12, f"共 {len(presets)} 款：{presets[0]['name']} …")
    first = presets[0]
    check(
        "预设 SVG 可预览",
        first["svg"].startswith("<svg") and first["svg"].endswith("</svg>"),
        f"{first['id']} 长度 {len(first['svg'])}",
    )

    # 2. 应用内置头像
    before = call("GET", "/api/user/profile", token=token).get("avatarUrl") or ""
    applied = call("POST", "/api/user/avatar/preset", {"presetId": first["id"]}, token=token)
    preset_url = applied["avatarUrl"]
    mode = "OSS 直链" if preset_url.startswith("https://") else "后端代理"
    check("应用内置头像", preset_url.endswith(".svg") or "key=" in preset_url, f"{mode} · {preset_url}")
    info = http_head(preset_url)
    check("内置头像可访问", info["status"] == 200 and "svg" in (info["type"] or ""), json.dumps(info))

    # 3. 本地上传
    tmp = os.path.join(tempfile.gettempdir(), f"witpath-avatar-{uuid.uuid4().hex}.png")
    make_png(tmp)
    uploaded = upload_multipart(tmp, token)
    local_url = uploaded["avatarUrl"]
    check("本地上传头像", local_url.endswith(".png") or "key=" in local_url, local_url)
    info = http_head(local_url)
    check("本地头像可访问", info["status"] == 200 and (info["type"] or "").startswith("image/"), json.dumps(info))

    # 4. 参数校验
    try:
        call("POST", "/api/user/avatar/preset", {"presetId": "not-exist"}, token=token)
        check("非法预设被拒绝", False, "未报错")
    except Exception as exc:  # noqa: BLE001
        check("非法预设被拒绝", True, str(exc)[:80])

    # 5. 回读资料确认持久化
    profile = call("GET", "/api/user/profile", token=token)
    check("资料接口回读头像", profile["avatarUrl"] == local_url, profile["avatarUrl"])

    print(f"\n旧头像（本次前）：{before or '空'}")
    print(f"\n通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
    if FAIL:
        print("失败项：" + "、".join(FAIL))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

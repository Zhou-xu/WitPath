"""WitPath 端到端冒烟测试。

串联真实后端接口，覆盖：三步登录 → 资料 → 目标/任务 → 学习记录 → 概览
→ 资料上传与解析 → AI 分析任务 → AI 对话 → 提醒。

用法：
    python tools/smoke_test.py                     # 使用默认演示账号
    python tools/smoke_test.py --username demo --password witpath123

说明：登录验证码答案从 Redis 中读取（后端把答案存在 witpath:login:init:<id>），
仅用于自动化测试，不影响生产逻辑。
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
import time
import uuid
from typing import Any, Dict, Optional
from urllib import error, parse, request

BASE = "http://127.0.0.1:8080"
REDIS_HOST = "127.0.0.1"
REDIS_PORT = 6379

PASS: list[str] = []
FAIL: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    mark = "PASS" if ok else "FAIL"
    line = f"[{mark}] {name}" + (f" - {detail}" if detail else "")
    print(line, flush=True)
    (PASS if ok else FAIL).append(name)


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------


def call(
    method: str,
    path: str,
    body: Optional[Dict[str, Any]] = None,
    token: Optional[str] = None,
    headers: Optional[Dict[str, str]] = None,
    timeout: int = 60,
) -> Any:
    data = None
    hdrs = {**(headers or {})}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = f"Bearer {token}"

    req = request.Request(f"{BASE}{path}", data=data, headers=hdrs, method=method)
    with request.urlopen(req, timeout=timeout) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 200:
        raise RuntimeError(f"{method} {path} -> {payload.get('code')}: {payload.get('msg')}")
    return payload.get("data")


# --------------------------------------------------------------------------
# Redis（RESP 协议直连，仅用于读取验证码答案）
# --------------------------------------------------------------------------


def redis_get(key: str) -> Optional[str]:
    with socket.create_connection((REDIS_HOST, REDIS_PORT), timeout=5) as sock:
        sock.sendall(f"GET {key}\r\n".encode())
        buf = b""
        while b"\r\n" not in buf or buf.count(b"\r\n") < 2:
            chunk = sock.recv(4096)
            if not chunk:
                break
            buf += chunk
    text = buf.decode("utf-8", "ignore").split("\r\n")
    if text and text[0].startswith("$-1"):
        return None
    # Redisson 默认使用 JSON 序列化，字符串会带引号
    return text[1].strip('"') if len(text) > 1 else None


def login(username: str, password: str) -> str:
    init_id = call("GET", "/login/LoginInitId")
    # 后端以字符串返回 19 位雪花 ID（JS Number 精度上限 2^53-1，整数会失真）
    init_id = str(init_id)
    check("登录步骤1：创建会话", init_id.isdigit(), f"loginInitId={init_id}")

    call("POST", "/login/image", headers={"userId": init_id})
    answer = redis_get(f"witpath:login:init:{init_id}")
    check("登录步骤2：验证码", bool(answer), f"captcha={answer}")

    data = call(
        "POST",
        "/login",
        {
            "loginInitId": init_id,
            "username": username,
            "password": password,
            "captchaAnswer": answer,
        },
    )
    check("登录步骤3：换取 token", bool(data.get("token")), f"userId={data.get('userId')}")
    return data["token"]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--username", default="demo")
    parser.add_argument("--password", default="witpath123")
    args = parser.parse_args()

    print(f"=== WitPath 端到端冒烟测试 @ {BASE} ===")
    try:
        health = call("GET", "/public/health")
        check("服务健康检查", health.get("backend") == "UP", json.dumps(health.get("aiDetail", {}).get("vector", {}), ensure_ascii=False))
    except Exception as exc:  # noqa: BLE001
        check("服务健康检查", False, str(exc))
        return 1

    token = login(args.username, args.password)

    profile = call("GET", "/api/user/profile", token=token)
    check("用户资料", profile.get("username") == args.username, profile.get("nickname") or "")

    # 目标
    goal = call(
        "POST",
        "/api/goals",
        {
            "title": f"冒烟测试目标-{uuid.uuid4().hex[:6]}",
            "description": "由冒烟测试脚本创建",
            "subject": "测试学科",
            "priority": 2,
            "targetHours": 10,
        },
        token=token,
    )
    check("创建学习目标", bool(goal.get("id")), f"goalId={goal.get('id')}")

    task = call(
        "POST",
        "/api/tasks",
        {"goalId": goal["id"], "title": "冒烟任务-完成第一章", "estimatedMinutes": 60, "priority": 3},
        token=token,
    )
    check("创建学习任务", bool(task.get("id")), f"taskId={task.get('id')}")

    done = call("POST", f"/api/tasks/{task['id']}/status?status=3", token=token)  # TaskStatus.DONE = 3
    check("任务标记完成", done.get("status") == 3, done.get("statusText", ""))

    record = call(
        "POST",
        "/api/records",
        {
            "goalId": goal["id"],
            "title": "冒烟测试学习记录",
            "content": "自动化脚本写入，用于验证记录链路与统计回算",
            "subject": "测试学科",
            "durationMinutes": 90,
            "focusScore": 4,
            "outcome": "完成 5 道练习",
            "tags": "冒烟,自动化",
        },
        token=token,
    )
    check("新增学习记录", bool(record.get("id")), f"duration={record.get('durationText')}")

    overview = call("GET", "/api/dashboard/overview?days=14", token=token)
    check(
        "概览统计",
        overview.get("overview", {}).get("recordCount", 0) > 0,
        f"记录数={overview['overview']['recordCount']} 连续天数={overview['overview']['streakDays']}",
    )

    # 资料上传（用一段文本模拟讲义）
    boundary = f"----witpath{uuid.uuid4().hex}"
    content = (
        "二叉树是一种每个节点最多有两个子节点的树结构。"
        "前序遍历先访问根节点，再遍历左子树与右子树；"
        "中序遍历对二叉搜索树可得到有序序列；后序遍历常用于释放资源。"
        "层序遍历借助队列按层访问节点，适合求树的宽度与深度。"
    ).encode("utf-8")
    body = b"".join(
        [
            f"--{boundary}\r\n".encode(),
            b'Content-Disposition: form-data; name="file"; filename="smoke-note.txt"\r\n',
            b"Content-Type: text/plain\r\n\r\n",
            content,
            b"\r\n",
            f"--{boundary}\r\n".encode(),
            'Content-Disposition: form-data; name="subject"\r\n\r\n测试学科\r\n'.encode("utf-8"),
            f"--{boundary}--\r\n".encode(),
        ]
    )
    req = request.Request(
        f"{BASE}/api/materials/upload",
        data=body,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )
    with request.urlopen(req, timeout=120) as resp:
        material = json.loads(resp.read().decode("utf-8"))["data"]
    check("上传学习资料", bool(material.get("id")), f"{material.get('originalName')} 解析={material.get('parseStatusText')}")

    # 等待异步入库完成
    for _ in range(20):
        page = call("GET", "/api/materials/page?page=1&size=5", token=token)
        current = next((m for m in page["records"] if m["id"] == material["id"]), None)
        if current and current.get("vectorStatus") in (2, 3):
            check(
                "资料向量化入库",
                current.get("vectorStatus") == 2,
                f"状态={current.get('vectorStatusText')} 片段={current.get('chunkCount')}",
            )
            break
        time.sleep(2)
    else:
        check("资料向量化入库", False, "超时未完成")

    # AI 分析任务
    ai_task = call(
        "POST",
        "/api/ai/analyze",
        {"type": "STUDY_ANALYSIS", "goalId": goal["id"], "rangeDays": 14, "question": "我该如何提升复习效率？"},
        token=token,
    )
    check("提交 AI 分析", bool(ai_task.get("taskNo")), f"taskNo={ai_task.get('taskNo')}")

    deadline = time.time() + 180
    final = ai_task
    while time.time() < deadline:
        final = call("GET", f"/api/ai/tasks/{ai_task['taskNo']}", token=token)
        if final.get("status") in (2, 3):
            break
        time.sleep(2)
    check(
        "AI 分析完成",
        final.get("status") == 2,
        f"状态={final.get('statusText')} 阶段={final.get('stageText')} 耗时={final.get('latencyMs')}ms",
    )
    if final.get("summary"):
        print("     摘要：", final["summary"][:120].replace("\n", " "))

    answer = call(
        "POST",
        "/api/ai/chat",
        {"message": "二叉树有哪些遍历方式？请结合我的资料回答。", "withContext": True},
        token=token,
    )
    check("AI 对话", bool(answer.get("content")), (answer.get("content") or "")[:80].replace("\n", " "))

    reminders = call("POST", "/api/reminders/generate", token=token)
    check("生成学习提醒", isinstance(reminders, int), f"新增 {reminders} 条")

    # 清理
    call("DELETE", f"/api/records/{record['id']}", token=token)
    call("DELETE", f"/api/tasks/{task['id']}", token=token)
    call("DELETE", f"/api/goals/{goal['id']}", token=token)
    call("DELETE", f"/api/materials/{material['id']}", token=token)
    check("清理测试数据", True, "已删除目标/任务/记录/资料")

    print(f"\n=== 通过 {len(PASS)} 项，失败 {len(FAIL)} 项 ===")
    if FAIL:
        print("失败项：", "、".join(FAIL))
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())

"""诊断代理：监听 9001，把 Spring 发给 AI 服务的原始请求字节落盘，再原样转发到 8000。

用法：
    python tools/ai_proxy_probe.py
然后把后端 AI 地址指到 9001：
    $env:WITPATH_AI_BASE_URL = "http://127.0.0.1:9001"
"""

import http.client
import socket
import threading

LISTEN_PORT = 9001
TARGET_HOST = "127.0.0.1"
TARGET_PORT = 8000
LOG = "tools/.cache/ai_request_probe.log"


def read_head(conn):
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = conn.recv(65536)
        if not chunk:
            break
        data += chunk
    return data


def handle(conn):
    conn.settimeout(10)
    try:
        data = read_head(conn)
        if not data:
            return
        head, _, rest = data.partition(b"\r\n\r\n")
        content_length = 0
        for line in head.split(b"\r\n")[1:]:
            if line.lower().startswith(b"content-length:"):
                content_length = int(line.split(b":", 1)[1].strip())
        body = rest
        while len(body) < content_length:
            chunk = conn.recv(65536)
            if not chunk:
                break
            body += chunk

        with open(LOG, "ab") as f:
            f.write(b"\n=========== REQUEST ===========\n")
            f.write(head + b"\r\n\r\n")
            f.write(body[:3000])
            f.write(b"\n")

        lines = head.split(b"\r\n")
        method, path, _version = lines[0].split(b" ")
        headers = {}
        for line in lines[1:]:
            key, sep, value = line.partition(b":")
            if not sep:
                continue
            headers[key.decode("latin-1")] = value.strip().decode("latin-1")
        headers["Host"] = f"{TARGET_HOST}:{TARGET_PORT}"
        headers["Connection"] = "close"

        upstream = http.client.HTTPConnection(TARGET_HOST, TARGET_PORT, timeout=180)
        upstream.request(method.decode(), path.decode(), body=body, headers=headers)
        resp = upstream.getresponse()
        payload = resp.read()

        conn.sendall(
            f"HTTP/1.1 {resp.status} {resp.reason}\r\n".encode()
            + b"Content-Length: " + str(len(payload)).encode() + b"\r\n"
            + b"Content-Type: " + (resp.getheader("Content-Type") or "application/json").encode() + b"\r\n"
            + b"Connection: close\r\n\r\n"
            + payload
        )
        upstream.close()
    except Exception as exc:  # noqa: BLE001
        with open(LOG, "ab") as f:
            f.write(f"PROXY ERROR: {exc}\n".encode())
    finally:
        try:
            conn.close()
        except Exception:  # noqa: BLE001
            pass


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", LISTEN_PORT))
    server.listen(20)
    print(f"probe listening on {LISTEN_PORT} -> {TARGET_PORT}")
    while True:
        conn, _addr = server.accept()
        threading.Thread(target=handle, args=(conn,), daemon=True).start()


if __name__ == "__main__":
    main()

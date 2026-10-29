"""轻量 MySQL 辅助脚本：查看表结构 / 执行 SQL 文件。

用法：
    python tools/db_cli.py desc
    python tools/db_cli.py exec database/WitPath.sql
    python tools/db_cli.py query "SELECT id, username FROM user LIMIT 5"

连接参数可用环境变量覆盖：WITPATH_DB_HOST / WITPATH_DB_PORT / WITPATH_DB_USER / WITPATH_DB_PASSWORD / WITPATH_DB_NAME
"""

from __future__ import annotations

import os
import sys

import pymysql

HOST = os.getenv("WITPATH_DB_HOST", "127.0.0.1")
PORT = int(os.getenv("WITPATH_DB_PORT", "3306"))
USER = os.getenv("WITPATH_DB_USER", "root")
PASSWORD = os.getenv("WITPATH_DB_PASSWORD", "123456")
NAME = os.getenv("WITPATH_DB_NAME", "WitPath")


def connect(use_db: bool = True):
    return pymysql.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        database=NAME if use_db else None,
        charset="utf8mb4",
        autocommit=True,
        connect_timeout=8,
    )


def desc() -> None:
    with connect() as conn, conn.cursor() as cur:
        cur.execute("SHOW TABLES")
        tables = [row[0] for row in cur.fetchall()]
        print(f"database={NAME} tables={len(tables)}")
        for table in tables:
            print("-" * 72)
            print(f"### {table}")
            cur.execute("SHOW CREATE TABLE `%s`" % table)
            print(cur.fetchone()[1])
            cur.execute("SELECT COUNT(*) FROM `%s`" % table)
            print("rows:", cur.fetchone()[0])


def query(sql: str) -> None:
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql)
        for row in cur.fetchall():
            print(row)


def exec_file(path: str) -> None:
    with open(path, "r", encoding="utf-8") as fh:
        sql = fh.read()
    statements = [s.strip() for s in sql.split(";") if s.strip()]
    with connect(use_db=False) as conn, conn.cursor() as cur:
        for stmt in statements:
            body = "\n".join(
                line for line in stmt.splitlines() if not line.strip().startswith("--")
            ).strip()
            if not body:
                continue
            try:
                cur.execute(body)
            except Exception as exc:  # noqa: BLE001
                print("WARN:", type(exc).__name__, str(exc)[:200])
            else:
                first_line = body.splitlines()[0][:80]
                print("OK :", first_line)
    print("done:", path)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "desc"
    if cmd == "desc":
        desc()
    elif cmd == "query":
        query(sys.argv[2])
    elif cmd == "exec":
        exec_file(sys.argv[2])
    else:
        print(__doc__)

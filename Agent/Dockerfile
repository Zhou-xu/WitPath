# syntax=docker/dockerfile:1
# =============================================================================
# WitPath AI 服务（FastAPI Agent）镜像
# 构建上下文 = Agent/
# 密钥不进镜像：运行时把 Agent/.env 只读挂载到 /app/.env（pydantic 自动加载）
# =============================================================================

FROM python:3.11-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TZ=Asia/Shanghai

# 先装依赖（利用层缓存）：requirements.txt 含可选项（PG/Milvus/OSS），全量安装
COPY requirements.txt ./
RUN pip install -r requirements.txt

COPY app ./app
COPY run.py ./

EXPOSE 8000
# healthcheck 依赖 python 标准库，slim 镜像没有 curl
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3)"

CMD ["python", "run.py"]

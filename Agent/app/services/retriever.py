"""知识检索服务：Milvus 优先，内存向量库兜底。

设计取舍：
- Milvus：正式环境方案，使用官方 MilvusClient（HTTP/HTTPS URI 连接），
  支持大规模向量、持久化与标量过滤（按 user_id 隔离不同用户的资料）。
- 内存向量库：本机调试与降级场景可用，进程重启后数据丢失，
  因此健康检查中会明确标注当前后端，避免误判。
"""

from __future__ import annotations

import hashlib
import logging
import math
import re
from typing import Any, Dict, List, Optional

from app.config import settings

logger = logging.getLogger(__name__)

VECTOR_DIM_FALLBACK = 64


# ---------------------------------------------------------------------------
# Embedding
# ---------------------------------------------------------------------------


def embed_texts(texts: List[str], override: Any = None) -> List[List[float]]:
    """生成文本向量：百炼 / Ollama / 在线向量模型，失败则用哈希向量兜底。

    override 为用户个人向量模型配置（UserModelConfig.has_embedding 为真时生效），
    未提供时使用服务端 .env 中的默认配置。
    """
    provider, api_key, base_url, model = _resolve_embedding(override)

    if not _embedding_available(provider, api_key):
        return [_hash_vector(text) for text in texts]

    if provider == "dashscope":
        vectors = _embed_dashscope(texts, api_key=api_key, base_url=base_url, model=model)
        if vectors:
            return vectors
    elif provider == "ollama":
        vectors = _embed_ollama(texts, model=model)
        if vectors:
            return vectors
    else:
        try:
            from langchain_openai import OpenAIEmbeddings

            embeddings = OpenAIEmbeddings(
                model=model,
                base_url=base_url or settings.ark_base_url,
                api_key=api_key,
            )
            vectors = embeddings.embed_documents(texts)
            if vectors:
                return [list(map(float, v)) for v in vectors]
        except Exception as exc:  # noqa: BLE001
            logger.warning("在线 Embedding 调用失败，改用哈希向量：%s", exc)

    return [_hash_vector(text) for text in texts]


def _resolve_embedding(override: Any) -> tuple:
    """确定本次实际使用的向量模型参数（用户配置优先，缺失回落服务端默认）。"""
    if override is not None and getattr(override, "has_embedding", False):
        provider = (override.embedding_provider or settings.embedding_provider or "").strip().lower()
        return (
            provider or "openai",
            override.embedding_api_key,
            override.embedding_base_url or settings.dashscope_embedding_base_url,
            override.embedding_model,
        )
    return (
        settings.embedding_provider,
        settings.dashscope_api_key if settings.embedding_provider == "dashscope" else settings.ark_api_key,
        settings.dashscope_embedding_base_url,
        settings.embedding_model,
    )


def _embedding_available(provider: str, api_key: Optional[str]) -> bool:
    """用户自带 key 时始终可用；否则沿用服务端可用性判断（Ollama 无需 key）。"""
    if provider == "ollama":
        return True
    if api_key and api_key.strip():
        return True
    return bool(settings.embedding_available)


def _embed_ollama(texts: List[str], model: Optional[str] = None) -> List[List[float]]:
    """调用本地 Ollama 的 /api/embed 生成向量（bge-m3 / nomic-embed-text 等）。"""
    try:
        import httpx

        # 本机 Ollama 直连，同样绕过代理环境变量
        with httpx.Client(timeout=30.0, trust_env=False) as client:
            resp = client.post(
                f"{settings.ollama_host.rstrip('/')}/api/embed",
                json={"model": model or settings.embedding_model, "input": texts},
            )
            resp.raise_for_status()
            data = resp.json()
        vectors = data.get("embeddings")
        if not vectors:
            # 兼容 /api/embeddings 旧接口（单条）
            single = data.get("embedding")
            vectors = [single] if single else None
        if vectors and len(vectors) == len(texts):
            return [list(map(float, v)) for v in vectors]
        logger.warning("Ollama 返回向量数量不匹配，改用哈希向量")
    except Exception as exc:  # noqa: BLE001
        logger.warning("Ollama Embedding 调用失败，改用哈希向量：%s", exc)
    return []


def _embed_dashscope(texts: List[str], api_key: Optional[str] = None, base_url: Optional[str] = None,
                     model: Optional[str] = None) -> List[List[float]]:
    """调用阿里云百炼（OpenAI 兼容）向量接口。

    百炼的 embeddings 与 OpenAI SDK 协议兼容，这里直接用 httpx 调用，
    避免走系统代理（trust_env=False）。
    """
    url = f"{(base_url or settings.dashscope_embedding_base_url).rstrip('/')}/embeddings"
    try:
        import httpx

        with httpx.Client(timeout=30.0, trust_env=False) as client:
            resp = client.post(
                url,
                headers={
                    "Authorization": f"Bearer {api_key or settings.dashscope_api_key}",
                    "Content-Type": "application/json",
                },
                json={"model": model or settings.embedding_model, "input": texts},
            )
            resp.raise_for_status()
            data = resp.json()
        # OpenAI 兼容协议按 index 升序返回
        vectors = [item["embedding"] for item in sorted(data.get("data", []), key=lambda x: x["index"])]
        if len(vectors) != len(texts):
            logger.warning("百炼返回向量数量不匹配，改用哈希向量")
            return []
        return [list(map(float, v)) for v in vectors]
    except Exception as exc:  # noqa: BLE001
        logger.warning("百炼 Embedding 调用失败，改用哈希向量：%s", exc)
    return []


def embed_query(query: str, override: Any = None) -> List[float]:
    vector = embed_texts([query], override=override)
    return vector[0] if vector else _hash_vector(query)


def _hash_vector(text: str, dim: int = VECTOR_DIM_FALLBACK) -> List[float]:
    """确定性伪向量：同文本恒定同向量，用于无 Embedding 服务时的降级检索。"""
    tokens = re.findall(r"[\u4e00-\u9fff]|[A-Za-z]+|\d+", text.lower()) or [text]
    vector = [0.0] * dim
    for token in tokens:
        digest = hashlib.md5(token.encode("utf-8")).hexdigest()
        vector[int(digest[:8], 16) % dim] += 1.0 if int(digest[8:10], 16) % 2 == 0 else -1.0
    norm = math.sqrt(sum(v * v for v in vector)) or 1.0
    return [v / norm for v in vector]


def _cosine(a: List[float], b: List[float]) -> float:
    if len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)) or 1.0
    nb = math.sqrt(sum(y * y for y in b)) or 1.0
    return dot / (na * nb)


# ---------------------------------------------------------------------------
# 内存存储（降级）
# ---------------------------------------------------------------------------


class _MemoryStore:
    def __init__(self) -> None:
        self._items: List[Dict[str, Any]] = []
        self._next_id = 1

    def upsert(self, docs: List[Dict[str, Any]]) -> int:
        for doc in docs:
            doc["pk"] = self._next_id
            self._next_id += 1
            self._items.append(doc)
        return len(docs)

    def delete_by_source(self, user_id: Optional[int], source: str) -> None:
        self._items = [
            item for item in self._items
            if not (item.get("source") == source and item.get("user_id") == user_id)
        ]

    def search(self, user_id: Optional[int], vector: List[float], top_k: int) -> List[Dict[str, Any]]:
        candidates = [i for i in self._items if user_id is None or i.get("user_id") == user_id]
        scored = [{**i, "score": _cosine(vector, i["vector"])} for i in candidates]
        scored.sort(key=lambda x: x["score"], reverse=True)
        return scored[:top_k]

    def count(self) -> int:
        return len(self._items)


_memory_store = _MemoryStore()
_client_cache: Any = None
_milvus_ok: Optional[bool] = None


# ---------------------------------------------------------------------------
# Milvus（MilvusClient）
# ---------------------------------------------------------------------------


def _client():
    """获取并缓存 MilvusClient，连接失败时返回 None。"""
    global _client_cache, _milvus_ok
    if _milvus_ok is False:
        return None
    if _client_cache is not None:
        return _client_cache
    try:
        from pymilvus import DataType, MilvusClient

        uri = f"http://{settings.milvus_host}:{settings.milvus_port}"
        client = MilvusClient(uri=uri)
        name = settings.milvus_collection
        dim = len(embed_query("warmup")) if settings.embedding_available else VECTOR_DIM_FALLBACK

        if not client.has_collection(collection_name=name):
            _create_collection(client, name, dim)
        else:
            # 维度对齐：切换 Embedding 模型后旧集合维度会不匹配，继续写入必然失败
            existing = _collection_dim(client, name)
            if existing is not None and existing != dim:
                logger.warning(
                    "Milvus 集合 %s 向量维度 %d 与当前模型 %d 不一致，重建集合",
                    name, existing, dim,
                )
                client.drop_collection(collection_name=name)
                _create_collection(client, name, dim)

        _client_cache = client
        _milvus_ok = True
        return client
    except Exception as exc:  # noqa: BLE001
        _milvus_ok = False
        logger.warning("Milvus 不可用，切换到内存向量库：%s", exc)
        return None


def _create_collection(client: Any, name: str, dim: int) -> None:
    """创建向量集合（主键 + user_id 标量过滤 + 余弦相似度索引）。"""
    from pymilvus import DataType

    schema = client.create_schema(auto_id=True, enable_dynamic_field=False)
    schema.add_field(field_name="id", datatype=DataType.INT64, is_primary=True)
    schema.add_field(field_name="user_id", datatype=DataType.INT64)
    schema.add_field(field_name="source", datatype=DataType.VARCHAR, max_length=512)
    schema.add_field(field_name="content", datatype=DataType.VARCHAR, max_length=4096)
    schema.add_field(field_name="vector", datatype=DataType.FLOAT_VECTOR, dim=dim)
    index_params = client.prepare_index_params()
    index_params.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
    client.create_collection(collection_name=name, schema=schema, index_params=index_params)
    logger.info("已创建 Milvus collection：%s (dim=%d)", name, dim)


def _collection_dim(client: Any, name: str) -> Optional[int]:
    """读取集合已有向量的维度，读取失败返回 None（交由调用方保守处理）。"""
    try:
        info = client.describe_collection(collection_name=name)
        fields = (info or {}).get("fields") or []
        for field in fields:
            if field.get("name") == "vector":
                params = field.get("params") or {}
                dim = params.get("dim")
                if dim is None and isinstance(field.get("type"), str):
                    # 兜底解析：FLOAT_VECTOR(1024)
                    import re

                    match = re.search(r"\((\d+)\)", field.get("type"))
                    dim = int(match.group(1)) if match else None
                return int(dim) if dim else None
    except Exception as exc:  # noqa: BLE001
        logger.warning("读取 Milvus 集合信息失败：%s", exc)
    return None


def ensure_backend() -> None:
    """主动探测一次 Milvus，避免健康检查在从未调用过时误报不可用。

    说明：_client() 内部会缓存结果，成功则写入 _milvus_ok=True，
    失败则置 False 并降级，不会每次请求都重新建连。
    """
    if settings.milvus_enabled and _milvus_ok is None:
        _client()


def backend_name() -> str:
    if not settings.milvus_enabled:
        return "memory"
    ensure_backend()
    return "milvus" if _milvus_ok else "memory(milvus-unavailable)"


def add_documents(user_id: Optional[int], source: str, contents: List[str],
                  override: Any = None) -> int:
    """写入文档向量，返回写入条数。"""
    if not contents:
        return 0
    vectors = embed_texts(contents, override=override)

    if settings.milvus_enabled:
        client = _client()
        if client is not None:
            try:
                rows = [
                    {
                        "user_id": int(user_id or 0),
                        "source": source,
                        "content": content[:4000],
                        "vector": vector,
                    }
                    for content, vector in zip(contents, vectors)
                ]
                client.insert(collection_name=settings.milvus_collection, data=rows)
                # 立即 flush：让数据与行数统计对新查询可见（默认会延迟落段）
                try:
                    client.flush(collection_name=settings.milvus_collection)
                except Exception as flush_exc:  # noqa: BLE001
                    logger.warning("Milvus flush 失败（不影响检索）：%s", flush_exc)
                return len(rows)
            except Exception as exc:  # noqa: BLE001
                logger.warning("Milvus 写入失败，改写入内存：%s", exc)

    _memory_store.delete_by_source(user_id, source)
    docs = [
        {"user_id": user_id, "source": source, "content": c[:4000], "vector": v}
        for c, v in zip(contents, vectors)
    ]
    return _memory_store.upsert(docs)


def search_similar(user_id: Optional[int], query: str, top_k: int = 4,
                   override: Any = None) -> List[Dict[str, Any]]:
    """语义检索，返回 [{source, snippet, score}]。"""
    vector = embed_query(query, override=override)

    if settings.milvus_enabled:
        client = _client()
        if client is not None:
            try:
                res = client.search(
                    collection_name=settings.milvus_collection,
                    data=[vector],
                    limit=top_k,
                    filter=f"user_id == {int(user_id or 0)}",
                    output_fields=["source", "content"],
                )
                hits: List[Dict[str, Any]] = []
                for group in res:
                    for hit in group:
                        entity = hit.get("entity") or {}
                        hits.append(
                            {
                                "source": entity.get("source"),
                                "snippet": (entity.get("content") or "")[:200],
                                "score": round(float(hit.get("distance") or 0), 4),
                            }
                        )
                return hits
            except Exception as exc:  # noqa: BLE001
                logger.warning("Milvus 检索失败，改用内存库：%s", exc)

    return [
        {
            "source": item["source"],
            "snippet": item["content"][:200],
            "score": round(item["score"], 4),
        }
        for item in _memory_store.search(user_id, vector, top_k)
    ]


def delete_by_source(user_id: Optional[int], source_prefix: str) -> int:
    """按 source 前缀删除向量（资料被删除时同步清理，避免幽灵检索结果）。"""
    if not source_prefix:
        return 0
    removed = 0

    if settings.milvus_enabled:
        client = _client()
        if client is not None:
            try:
                prefix = source_prefix.replace('"', '\\"')
                expr = f'user_id == {int(user_id or 0)} and source like "{prefix}%"'
                result = client.delete(collection_name=settings.milvus_collection, filter=expr)
                removed = int(result.get("delete_count") or 0) if isinstance(result, dict) else 0
                try:
                    client.flush(collection_name=settings.milvus_collection)
                except Exception as flush_exc:  # noqa: BLE001
                    logger.warning("Milvus flush 失败（不影响删除）：%s", flush_exc)
            except Exception as exc:  # noqa: BLE001
                logger.warning("Milvus 删除向量失败：%s", exc)

    before = _memory_store.count()
    _memory_store.delete_by_source(user_id, source_prefix)
    return max(removed, before - _memory_store.count())


def store_stats() -> Dict[str, Any]:
    backend = backend_name()
    stats: Dict[str, Any] = {
        "backend": backend,
        "embedding": (
            f"{settings.embedding_provider}:{settings.embedding_model}"
            if settings.embedding_available
            else "hash-fallback"
        ),
        "memory_items": _memory_store.count(),
    }
    if backend == "milvus":
        try:
            client = _client()
            if client is not None:
                stats["milvus_collection"] = settings.milvus_collection
                stats["milvus_rows"] = client.get_collection_stats(
                    collection_name=settings.milvus_collection
                ).get("row_count")
        except Exception as exc:  # noqa: BLE001
            stats["milvus_error"] = str(exc)[:200]
    return stats

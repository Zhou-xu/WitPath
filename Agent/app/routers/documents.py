"""文档摄取与知识检索接口。"""

from __future__ import annotations

import logging
from typing import Any, Dict

from fastapi import APIRouter, Query

from app.schemas import DocIngestRequest, DocIngestResponse, SearchRequest
from app.services import ingest, retriever

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/ai/documents", tags=["documents"])


@router.post("/ingest", response_model=DocIngestResponse)
def do_ingest(request: DocIngestRequest) -> DocIngestResponse:
    """解析文档 -> 分块 -> 向量化 -> 写入检索库。"""
    try:
        written, backend = ingest.index_document(
            user_id=request.user_id,
            # source 统一带 [materialId] 前缀，删除资料时可按前缀精确清理向量
            source=f"[{request.material_id}] {request.file_name or request.oss_object_key or ''}".strip(),
            file_name=request.file_name,
            file_type=request.file_type,
            storage_type=request.storage_type or "local",
            local_path=request.local_path,
            oss_bucket=request.oss_bucket,
            oss_object_key=request.oss_object_key,
            model_config=request.user_model_config,
        )
        return DocIngestResponse(
            material_id=request.material_id,
            status="succeeded",
            chunk_count=written,
            vector_backend=backend,
        )
    except Exception as exc:  # noqa: BLE001
        logger.warning("文档摄取失败：materialId=%s err=%s", request.material_id, exc)
        return DocIngestResponse(
            material_id=request.material_id,
            status="failed",
            chunk_count=0,
            vector_backend=retriever.backend_name(),
            error=str(exc),
        )


@router.delete("/{material_id}")
def delete_document(material_id: int, user_id: int = Query(..., description="所属用户 ID")):
    """删除某份资料对应的向量，避免资料移除后仍被检索到。"""
    removed = retriever.delete_by_source(user_id=user_id, source_prefix=f"[{material_id}]")
    return {"material_id": material_id, "removed": removed, "backend": retriever.backend_name()}


@router.get("/search")
def search(user_id: int = Query(..., description="用户 ID，用于隔离不同用户的资料"),
           query: str = Query(..., min_length=1),
           top_k: int = Query(5, ge=1, le=20)):
    """语义检索用户的知识库（使用服务端默认向量模型）。"""
    return {
        "query": query,
        "top_k": top_k,
        "backend": retriever.backend_name(),
        "results": retriever.search_similar(user_id=user_id, query=query, top_k=top_k),
    }


@router.post("/search")
def search_with_config(request: SearchRequest) -> Dict[str, Any]:
    """语义检索（POST）：可携带用户个人向量模型配置。

    与 GET /search 的区别是走 body 传参，避免 API Key 出现在 URL 与访问日志里。
    """
    top_k = max(1, min(int(request.top_k or 5), 20))
    return {
        "query": request.query,
        "top_k": top_k,
        "backend": retriever.backend_name(),
        "results": retriever.search_similar(user_id=request.user_id, query=request.query, top_k=top_k,
                                            override=request.user_model_config),
    }

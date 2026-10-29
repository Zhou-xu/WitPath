"""文档摄取：读取 -> 文本提取 -> 分块 -> 向量化 -> 写入检索库。

支持来源：
- local：Spring Boot 本地存储目录中的文件（相对 BASE_STORAGE_DIR）
- oss  ：阿里云 OSS 对象（需要配置并启用 OSS）
"""

from __future__ import annotations

import logging
import os
from typing import List, Optional, Tuple

from app.services import retriever

logger = logging.getLogger(__name__)

# Spring Boot 侧 witpath.storage.local-dir 的默认值（server 模块目录下 storage/materials）
DEFAULT_STORAGE_DIR = "../backend/WitPath/server/storage/materials"
BASE_STORAGE_DIR = os.getenv("WITPATH_STORAGE_DIR", DEFAULT_STORAGE_DIR)

# 兜底候选：兼容历史配置路径，避免迁移后读不到旧文件
EXTRA_STORAGE_DIRS = [
    os.getenv("WITPATH_STORAGE_DIR_EXTRA", "../backend/WitPath/storage/materials"),
]

SUPPORTED_TEXT_EXT = {"md", "markdown", "txt", "csv"}
SUPPORTED_PDF_EXT = {"pdf"}
SUPPORTED_DOCX_EXT = {"docx"}

CHUNK_SIZE = 600
CHUNK_OVERLAP = 80


def ingest_document(
    file_name: str,
    file_type: str,
    storage_type: str = "local",
    local_path: Optional[str] = None,
    oss_bucket: Optional[str] = None,
    oss_object_key: Optional[str] = None,
) -> Tuple[int, str]:
    """摄取单个文档，返回 (切分数量, 存储后端标识)。"""
    raw = _load_bytes(storage_type, local_path, oss_bucket, oss_object_key)
    if raw is None:
        raise FileNotFoundError(f"无法读取文件：{file_name}")

    text = _extract_text(raw, file_type)
    if not text.strip():
        raise ValueError(f"文件内容为空或暂不支持解析：{file_name}")

    chunks = split_text(text)
    return chunks, text


def split_text(text: str, size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP) -> List[str]:
    """按字符窗口切分，保留段落边界的语义完整度。"""
    if not text:
        return []
    chunks: List[str] = []
    start = 0
    length = len(text)
    while start < length:
        end = min(start + size, length)
        chunk = text[start:end]
        # 尽量在句号 / 换行处收尾
        for sep in ("。\n", "\n\n", "。", "\n"):
            idx = chunk.rfind(sep)
            if idx > size * 0.6:
                end = start + idx + len(sep)
                chunk = text[start:end]
                break
        chunks.append(chunk.strip())
        start = end - overlap if end - overlap > start else end
    return [c for c in chunks if c]


def index_document(user_id: Optional[int], source: str, file_name: str, file_type: str,
                   storage_type: str = "local", local_path: Optional[str] = None,
                   oss_bucket: Optional[str] = None, oss_object_key: Optional[str] = None,
                   model_config: Any = None) -> Tuple[int, str]:
    """读取文档并完成向量写入。"""
    chunks, _ = ingest_document(
        file_name=file_name,
        file_type=file_type,
        storage_type=storage_type,
        local_path=local_path,
        oss_bucket=oss_bucket,
        oss_object_key=oss_object_key,
    )
    written = retriever.add_documents(user_id=user_id, source=source, contents=chunks,
                                      override=model_config)
    return written, retriever.backend_name()


# ---------------------------------------------------------------------------
# 内部实现
# ---------------------------------------------------------------------------


def _load_bytes(storage_type: str, local_path: Optional[str], oss_bucket: Optional[str],
                oss_object_key: Optional[str]) -> Optional[bytes]:
    if storage_type == "oss" and oss_object_key:
        return _load_from_oss(oss_bucket, oss_object_key)
    if not local_path:
        return None
    candidates = [
        local_path,
        os.path.join(BASE_STORAGE_DIR, local_path),
        *[os.path.join(extra, local_path) for extra in EXTRA_STORAGE_DIRS if extra],
    ]
    for path in candidates:
        if path and os.path.isfile(path):
            with open(path, "rb") as fh:
                return fh.read()
    logger.warning("未找到本地文件，尝试路径：%s", candidates)
    return None


def _load_from_oss(bucket: Optional[str], object_key: str) -> Optional[bytes]:
    try:
        import oss2
        from app.config import settings

        auth = oss2.Auth(settings.oss_access_key_id, settings.oss_access_key_secret)
        bucket_name = bucket or settings.oss_bucket_name
        bucket_obj = oss2.Bucket(auth, settings.oss_endpoint, bucket_name)
        return bucket_obj.get_object(object_key).read()
    except Exception as exc:  # noqa: BLE001
        logger.warning("OSS 读取失败：%s", exc)
        return None


def _extract_text(raw: bytes, file_type: str) -> str:
    ext = (file_type or "").lower().lstrip(".")
    if ext in SUPPORTED_PDF_EXT:
        return _extract_pdf(raw)
    if ext in SUPPORTED_DOCX_EXT:
        return _extract_docx(raw)
    # 文本类：按多种编码尝试解码
    for encoding in ("utf-8", "gbk", "utf-16"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="ignore")


def _extract_pdf(raw: bytes) -> str:
    try:
        from pypdf import PdfReader
        import io

        reader = PdfReader(io.BytesIO(raw))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    except Exception as exc:  # noqa: BLE001
        logger.warning("PDF 解析失败：%s", exc)
        return ""


def _extract_docx(raw: bytes) -> str:
    try:
        import io

        from docx import Document

        document = Document(io.BytesIO(raw))
        return "\n".join(paragraph.text for paragraph in document.paragraphs)
    except Exception as exc:  # noqa: BLE001
        logger.warning("DOCX 解析失败：%s", exc)
        return ""

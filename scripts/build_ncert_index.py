#!/usr/bin/env python3
"""
Build NCERT embedding index for local RAG retrieval.

Input: PDFs under ../ncert_content (or custom path)
Output: JSONL index file with embeddings

Environment: set GOOGLE_GENAI_API_KEY, or put it in repo-root .env.local / .env
(loaded automatically if python-dotenv is installed).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from io import TextIOWrapper
from pathlib import Path
from typing import Iterable
from urllib import request

from pypdf import PdfReader

try:
    from dotenv import load_dotenv
except ImportError:
    load_dotenv = None  # type: ignore[assignment, misc]


EMBED_MODEL = "models/gemini-embedding-001"


def relax_pypdf_limits_for_trusted_pdfs() -> None:
    """
    pypdf caps decompressed stream size (default ~75MB) to mitigate zip bombs.
    Legitimate NCERT PDFs can exceed that on some pages (Flate/LZW), which
    produced: Limit reached while decompressing. … bytes remaining.
    Setting limits to 0 disables the cap (safe for local trusted content only).
    """
    try:
        import pypdf.filters as pdf_filters
    except ImportError:
        return
    for attr in (
        "ZLIB_MAX_OUTPUT_LENGTH",
        "LZW_MAX_OUTPUT_LENGTH",
        "JBIG2_MAX_OUTPUT_LENGTH",
        "RUN_LENGTH_MAX_OUTPUT_LENGTH",
    ):
        if hasattr(pdf_filters, attr):
            setattr(pdf_filters, attr, 0)


def load_repo_env() -> None:
    """Load repo-root .env, then .env.local (overrides .env). Existing shell exports win until .env.local."""
    if load_dotenv is None:
        return
    root = Path(__file__).resolve().parent.parent
    load_dotenv(root / ".env", override=False)
    load_dotenv(root / ".env.local", override=True)


@dataclass
class Chunk:
    chunk_id: str
    source_file: str
    subject: str
    page: int
    chunk_index: int
    text: str


def normalize_text(raw: str) -> str:
    text = raw.replace("\x00", " ")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def infer_subject(pdf_path: Path, pdf_root: Path) -> str:
    """First path segment under pdf_root (e.g. ncert_content/biology/foo.pdf -> biology)."""
    try:
        rel = pdf_path.resolve().relative_to(pdf_root.resolve())
    except ValueError:
        return ""
    parts = rel.parts
    if len(parts) < 2:
        return ""
    return parts[0].strip().lower()


def subject_filename_key(subject: str) -> str:
    s = (subject or "general").strip().lower()
    if not s:
        s = "general"
    return re.sub(r"[^a-z0-9_-]+", "_", s)


def extract_chunks_from_pdf(
    pdf_path: Path, pdf_root: Path, chunk_size: int, overlap: int
) -> Iterable[Chunk]:
    try:
        reader = PdfReader(str(pdf_path))
    except Exception as exc:
        print(f"WARN skipping unreadable PDF {pdf_path}: {exc}", file=sys.stderr)
        return
    file_key = str(pdf_path)
    subject = infer_subject(pdf_path, pdf_root)
    for page_idx, page in enumerate(reader.pages, start=1):
        try:
            text = normalize_text(page.extract_text() or "")
        except Exception as exc:
            print(f"WARN skipping page {page_idx} of {pdf_path}: {exc}", file=sys.stderr)
            continue
        if len(text) < 40:
            continue
        start = 0
        chunk_idx = 0
        while start < len(text):
            end = min(len(text), start + chunk_size)
            chunk_text = text[start:end].strip()
            if len(chunk_text) >= 80:
                yield Chunk(
                    chunk_id=f"{pdf_path.stem}-p{page_idx}-c{chunk_idx}",
                    source_file=file_key,
                    subject=subject,
                    page=page_idx,
                    chunk_index=chunk_idx,
                    text=chunk_text,
                )
            if end >= len(text):
                break
            start = end - overlap
            chunk_idx += 1


def embed_text(api_key: str, text: str) -> list[float]:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key={api_key}"
    payload = {
        "model": EMBED_MODEL,
        "content": {"parts": [{"text": text}]},
    }
    data = json.dumps(payload).encode("utf-8")
    req = request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with request.urlopen(req, timeout=60) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    emb = body.get("embedding", {}).get("values")
    if not emb:
        raise RuntimeError(f"empty embedding response: {body}")
    return emb


def iter_pdfs(root: Path) -> Iterable[Path]:
    for p in sorted(root.rglob("*.pdf")):
        yield p


def main() -> int:
    parser = argparse.ArgumentParser(description="Build NCERT embeddings index")
    parser.add_argument("--pdf-root", default="../ncert_content", help="Path to NCERT PDF root")
    parser.add_argument("--output", default="content/index/ncert_embeddings.jsonl", help="Output JSONL index")
    parser.add_argument("--chunk-size", type=int, default=1200)
    parser.add_argument("--overlap", type=int, default=200)
    parser.add_argument("--max-chunks", type=int, default=0, help="Limit chunks for quick testing (0 = no limit)")
    parser.add_argument("--sleep-ms", type=int, default=40, help="Sleep between embedding requests")
    parser.add_argument(
        "--subject",
        default="",
        help="Only index PDFs under this first-level folder name (e.g. biology, physics); case-insensitive",
    )
    parser.add_argument(
        "--per-subject-dir",
        default="",
        help="Also write one JSONL per subject under this directory (e.g. content/index/by-subject)",
    )
    args = parser.parse_args()

    relax_pypdf_limits_for_trusted_pdfs()
    load_repo_env()
    api_key = os.getenv("GOOGLE_GENAI_API_KEY", "").strip()
    if not api_key:
        print(
            "ERROR: GOOGLE_GENAI_API_KEY is required "
            "(export it or add to repo-root .env.local — see .env.example)",
            file=sys.stderr,
        )
        return 1

    pdf_root = Path(args.pdf_root).resolve()
    if not pdf_root.exists():
        print(f"ERROR: PDF root not found: {pdf_root}", file=sys.stderr)
        return 1

    out_path = Path(args.output).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    filter_subject = args.subject.strip().lower()
    per_subject_root = Path(args.per_subject_dir).resolve() if str(args.per_subject_dir).strip() else None
    if per_subject_root is not None:
        per_subject_root.mkdir(parents=True, exist_ok=True)

    per_subject_files: dict[str, TextIOWrapper] = {}

    def write_record(f_combined: TextIOWrapper, record: dict, subject_key: str) -> None:
        line = json.dumps(record, ensure_ascii=False) + "\n"
        f_combined.write(line)
        if per_subject_root is not None:
            key = subject_filename_key(subject_key)
            if key not in per_subject_files:
                spath = per_subject_root / f"ncert_{key}.jsonl"
                per_subject_files[key] = spath.open("w", encoding="utf-8")
            per_subject_files[key].write(line)

    total = 0
    written = 0
    try:
        with out_path.open("w", encoding="utf-8") as f_combined:
            for pdf in iter_pdfs(pdf_root):
                sub = infer_subject(pdf, pdf_root)
                if filter_subject and sub != filter_subject:
                    continue
                for chunk in extract_chunks_from_pdf(pdf, pdf_root, args.chunk_size, args.overlap):
                    total += 1
                    if args.max_chunks > 0 and written >= args.max_chunks:
                        break
                    try:
                        emb = embed_text(api_key, chunk.text)
                    except Exception as exc:  # noqa: BLE001
                        print(f"WARN embedding failed for {chunk.chunk_id}: {exc}", file=sys.stderr)
                        continue

                    record = {
                        "id": chunk.chunk_id,
                        "source_file": chunk.source_file,
                        "subject": chunk.subject,
                        "page": chunk.page,
                        "chunk_index": chunk.chunk_index,
                        "text": chunk.text,
                        "embedding": emb,
                    }
                    write_record(f_combined, record, chunk.subject)
                    written += 1
                    if written % 25 == 0:
                        print(f"Indexed {written} chunks...")
                    time.sleep(max(args.sleep_ms, 0) / 1000.0)

                if args.max_chunks > 0 and written >= args.max_chunks:
                    break
    finally:
        for fh in per_subject_files.values():
            fh.close()

    print(f"Done. Processed chunks: {total}, indexed: {written}")
    print(f"Output: {out_path}")
    if per_subject_root is not None:
        print(f"Per-subject dir: {per_subject_root} ({len(per_subject_files)} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

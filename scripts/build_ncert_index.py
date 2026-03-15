#!/usr/bin/env python3
"""
Build NCERT embedding index for local RAG retrieval.

Input: PDFs under ../ncert_content (or custom path)
Output: JSONL index file with embeddings
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from urllib import request

from pypdf import PdfReader


EMBED_MODEL = "models/gemini-embedding-001"


@dataclass
class Chunk:
    chunk_id: str
    source_file: str
    page: int
    chunk_index: int
    text: str


def normalize_text(raw: str) -> str:
    text = raw.replace("\x00", " ")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def extract_chunks_from_pdf(pdf_path: Path, chunk_size: int, overlap: int) -> Iterable[Chunk]:
    try:
        reader = PdfReader(str(pdf_path))
    except Exception as exc:
        print(f"WARN skipping unreadable PDF {pdf_path}: {exc}", file=sys.stderr)
        return
    file_key = str(pdf_path)
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
    args = parser.parse_args()

    api_key = os.getenv("GOOGLE_GENAI_API_KEY", "").strip()
    if not api_key:
        print("ERROR: GOOGLE_GENAI_API_KEY is required", file=sys.stderr)
        return 1

    pdf_root = Path(args.pdf_root).resolve()
    if not pdf_root.exists():
        print(f"ERROR: PDF root not found: {pdf_root}", file=sys.stderr)
        return 1

    out_path = Path(args.output).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    total = 0
    written = 0
    with out_path.open("w", encoding="utf-8") as f:
        for pdf in iter_pdfs(pdf_root):
            for chunk in extract_chunks_from_pdf(pdf, args.chunk_size, args.overlap):
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
                    "page": chunk.page,
                    "chunk_index": chunk.chunk_index,
                    "text": chunk.text,
                    "embedding": emb,
                }
                f.write(json.dumps(record, ensure_ascii=False) + "\n")
                written += 1
                if written % 25 == 0:
                    print(f"Indexed {written} chunks...")
                time.sleep(max(args.sleep_ms, 0) / 1000.0)

            if args.max_chunks > 0 and written >= args.max_chunks:
                break

    print(f"Done. Processed chunks: {total}, indexed: {written}")
    print(f"Output: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

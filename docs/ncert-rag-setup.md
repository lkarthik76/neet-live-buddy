# NCERT Vectorization and Gemini RAG Setup

This guide explains how to vectorize your PDFs from `../ncert_content` and use them in Gemini prompts through the Go tutor service.

## Architecture

1. Extract text from NCERT PDFs
2. Chunk text
3. Generate embeddings with Gemini (`text-embedding-004`)
4. Store vectors in `content/index/ncert_embeddings.jsonl`
5. At runtime, Go service retrieves top-k chunks and injects them into tutor prompt

## 1) Install prerequisites

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy
python3 -m venv .venv
source .venv/bin/activate
pip install -r scripts/requirements.txt
```

Set API key:

```bash
export GOOGLE_GENAI_API_KEY="your-genai-api-key"
```

## 2) Build a quick test index first

Use a small chunk count for sanity check:

```bash
python3 scripts/build_ncert_index.py \
  --pdf-root ../ncert_content \
  --output content/index/ncert_embeddings.jsonl \
  --max-chunks 200
```

## 3) Build full index

After quick test succeeds:

```bash
python3 scripts/build_ncert_index.py \
  --pdf-root ../ncert_content \
  --output content/index/ncert_embeddings.jsonl
```

Optional tuning:

- `--chunk-size 1200`
- `--overlap 200`
- `--sleep-ms 40` (rate-limit safety)

## 4) Enable RAG in Go service

Set env:

```bash
export CONTENT_DIR=../../content
export ENABLE_NCERT_RAG=true
export NCERT_VECTOR_INDEX_PATH=../../content/index/ncert_embeddings.jsonl
export NCERT_TOP_K=4
export GOOGLE_GENAI_API_KEY="your-genai-api-key"
export GO_TUTOR_MODEL="gemini-2.0-flash"
```

### Option A: Local index file (development)

Keep:

- `NCERT_VECTOR_INDEX_PATH=../../content/index/ncert_embeddings.jsonl`

### Option B: Pull index from GCS at startup (recommended on Cloud Run)

Upload index:

```bash
gsutil mb -l asia-south1 gs://smart-study-buddy-content
gsutil cp content/index/ncert_embeddings.jsonl gs://smart-study-buddy-content/index/ncert_embeddings.jsonl
```

Set service env:

```bash
export NCERT_VECTOR_INDEX_GCS_URI="gs://smart-study-buddy-content/index/ncert_embeddings.jsonl"
export NCERT_VECTOR_INDEX_PATH="/tmp/ncert_embeddings.jsonl"
```

Cloud Run service account must have:

- `roles/storage.objectViewer` on the bucket

Grant permission:

```bash
PROJECT_NUMBER=$(gcloud projects describe "$GOOGLE_CLOUD_PROJECT" --format='value(projectNumber)')
SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
gcloud storage buckets add-iam-policy-binding gs://smart-study-buddy-content \
  --member="serviceAccount:${SA}" \
  --role="roles/storage.objectViewer"
```

Run service:

```bash
cd services/tutor-go
go run .
```

Expected logs include retriever load line with chunk count.

## 5) Connect frontend to Go service

In `.env.local` (project root):

- `USE_GO_TUTOR_SERVICE=true`
- `TUTOR_SERVICE_URL=http://localhost:8081`

Start frontend:

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy
npm run dev
```

## 6) How Gemini uses vector content

Per tutor request:

1. Go service creates query embedding for student prompt (+ image text context if provided)
2. Compares against local NCERT index with cosine similarity
3. Picks top `NCERT_TOP_K` chunks
4. Adds retrieved snippets into prompt as "Retrieved NCERT references"
5. Gemini generates grounded JSON response

## Notes

- If index file is missing, service still works without RAG.
- Keep `NCERT_TOP_K` small (3-5) to control token usage.
- Rebuild index when PDF set changes.
- On Cloud Run, prefer `NCERT_VECTOR_INDEX_GCS_URI` so the latest index is pulled at startup.

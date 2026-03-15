# GCP Deployment (End-to-End, Step-by-Step)

This deploys:

1. Go tutor microservice (`services/tutor-go`) on Cloud Run
2. Next.js frontend on Cloud Run, configured to call the deployed Go service

## Prerequisites

- `gcloud` installed and authenticated
- Node.js 20+ installed
- Google Cloud project with billing enabled
- Gemini API key ready

## 1) Authenticate and select project

```bash
gcloud auth login
gcloud auth application-default login
export GOOGLE_CLOUD_PROJECT="your-gcp-project-id"
export GOOGLE_CLOUD_REGION="asia-south1"
gcloud config set project "${GOOGLE_CLOUD_PROJECT}"
```

## 2) Enable required APIs

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy
./infra/setup-gcp.sh
```

If Firestore is not initialized:

```bash
gcloud firestore databases create --location="${GOOGLE_CLOUD_REGION}" --type=firestore-native
```

## 3) Set secrets/env needed for deploy

```bash
export GOOGLE_GENAI_API_KEY="your-genai-api-key"
export GO_TUTOR_SERVICE_NAME="neet-live-buddy-go-tutor"
export SERVICE_NAME="neet-live-buddy-web"
export ENABLE_NCERT_RAG=true
export NCERT_TOP_K=4
```

If using GCS-hosted vector index (recommended), upload once:

```bash
gsutil mb -l "${GOOGLE_CLOUD_REGION}" gs://neet-live-buddy-content
gsutil cp content/index/ncert_embeddings.jsonl gs://neet-live-buddy-content/index/ncert_embeddings.jsonl
export NCERT_VECTOR_INDEX_GCS_URI="gs://neet-live-buddy-content/index/ncert_embeddings.jsonl"
export NCERT_VECTOR_INDEX_PATH="/tmp/ncert_embeddings.jsonl"
```

Grant Cloud Run service account read access:

```bash
PROJECT_NUMBER=$(gcloud projects describe "$GOOGLE_CLOUD_PROJECT" --format='value(projectNumber)')
SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
gcloud storage buckets add-iam-policy-binding gs://neet-live-buddy-content \
  --member="serviceAccount:${SA}" \
  --role="roles/storage.objectViewer"
```

## 4) One-command full stack deployment

```bash
./infra/deploy-full-stack.sh
```

This command:

- deploys Go tutor service
- reads Go service URL
- deploys frontend with:
  - `USE_GO_TUTOR_SERVICE=true`
  - `TUTOR_SERVICE_URL=<deployed-go-url>`

## 5) Verify both services

Health check:

```bash
GO_URL=$(gcloud run services describe "${GO_TUTOR_SERVICE_NAME}" --region "${GOOGLE_CLOUD_REGION}" --format 'value(status.url)')
curl "${GO_URL}/healthz"
```

Frontend URL:

```bash
WEB_URL=$(gcloud run services describe "${SERVICE_NAME}" --region "${GOOGLE_CLOUD_REGION}" --format 'value(status.url)')
echo "${WEB_URL}"
```

## 6) Smoke test tutor endpoint from frontend

```bash
curl -X POST "${WEB_URL}/api/tutor" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Explain osmosis for NEET","language":"english","subjectHint":"biology"}'
```

## 7) Update deploys after code changes

- Go service only:
  - `./infra/deploy-go-tutor.sh`
- Frontend only:
  - `./infra/deploy-cloud-run.sh` (ensure env vars are set correctly)
- Both:
  - `./infra/deploy-full-stack.sh`

## Notes

- Current app is MVP and not fully feature-complete for final hackathon demo.
- Core deploy path is ready and repeatable.

#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GOOGLE_CLOUD_PROJECT:-}" ]]; then
  echo "GOOGLE_CLOUD_PROJECT is required"
  exit 1
fi

if [[ -z "${GOOGLE_GENAI_API_KEY:-}" ]]; then
  echo "GOOGLE_GENAI_API_KEY is required"
  exit 1
fi

REGION="${GOOGLE_CLOUD_REGION:-asia-south1}"
SERVICE_NAME="${GO_TUTOR_SERVICE_NAME:-neet-live-buddy-go-tutor}"
MODEL="${GO_TUTOR_MODEL:-gemini-2.5-pro}"
ENABLE_RAG="${ENABLE_NCERT_RAG:-true}"
TOP_K="${NCERT_TOP_K:-4}"
INDEX_PATH="${NCERT_VECTOR_INDEX_PATH:-/app/content/index/ncert_embeddings.jsonl}"
INDEX_GCS_URI="${NCERT_VECTOR_INDEX_GCS_URI:-}"
INDEX_GCS_PUBLIC_URL="${NCERT_VECTOR_INDEX_GCS_PUBLIC_URL:-}"
IMAGE="gcr.io/${GOOGLE_CLOUD_PROJECT}/${SERVICE_NAME}:latest"

echo "Building Go tutor image ${IMAGE}"
gcloud builds submit \
  --config infra/cloudbuild-go-tutor.yaml \
  --substitutions "_IMAGE=${IMAGE}" \
  .

echo "Deploying ${SERVICE_NAME} to Cloud Run"
gcloud run deploy "${SERVICE_NAME}" \
  --image "${IMAGE}" \
  --region "${REGION}" \
  --platform managed \
  --allow-unauthenticated \
  --timeout=120 \
  --memory=512Mi \
  --set-env-vars "GOOGLE_GENAI_API_KEY=${GOOGLE_GENAI_API_KEY},GO_TUTOR_MODEL=${MODEL},ENABLE_NCERT_RAG=${ENABLE_RAG},NCERT_TOP_K=${TOP_K},NCERT_VECTOR_INDEX_PATH=${INDEX_PATH},NCERT_VECTOR_INDEX_GCS_URI=${INDEX_GCS_URI},NCERT_VECTOR_INDEX_GCS_PUBLIC_URL=${INDEX_GCS_PUBLIC_URL},CONTENT_DIR=/app/content"

echo "Go tutor deployed."

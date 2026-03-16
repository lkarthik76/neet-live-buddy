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
GO_SERVICE_NAME="${GO_TUTOR_SERVICE_NAME:-smart-study-buddy-go-tutor}"
WEB_SERVICE_NAME="${SERVICE_NAME:-smart-study-buddy-web}"

echo "Deploying Go tutor service..."
./infra/deploy-go-tutor.sh

GO_TUTOR_URL="$(gcloud run services describe "${GO_SERVICE_NAME}" --region "${REGION}" --format 'value(status.url)')"
if [[ -z "${GO_TUTOR_URL}" ]]; then
  echo "Failed to resolve Go tutor URL"
  exit 1
fi

echo "Go tutor URL: ${GO_TUTOR_URL}"
echo "Deploying frontend service with Go tutor binding..."

IMAGE="gcr.io/${GOOGLE_CLOUD_PROJECT}/${WEB_SERVICE_NAME}:latest"
gcloud builds submit --tag "${IMAGE}" .

gcloud run deploy "${WEB_SERVICE_NAME}" \
  --image "${IMAGE}" \
  --region "${REGION}" \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars "GOOGLE_CLOUD_PROJECT=${GOOGLE_CLOUD_PROJECT},GOOGLE_CLOUD_REGION=${REGION},USE_GO_TUTOR_SERVICE=true,TUTOR_SERVICE_URL=${GO_TUTOR_URL}"

WEB_URL="$(gcloud run services describe "${WEB_SERVICE_NAME}" --region "${REGION}" --format 'value(status.url)')"
echo "Full stack deployed."
echo "Frontend URL: ${WEB_URL}"

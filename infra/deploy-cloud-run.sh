#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GOOGLE_CLOUD_PROJECT:-}" ]]; then
  echo "GOOGLE_CLOUD_PROJECT is required"
  exit 1
fi

REGION="${GOOGLE_CLOUD_REGION:-asia-south1}"
SERVICE_NAME="${SERVICE_NAME:-neet-live-buddy}"
IMAGE="gcr.io/${GOOGLE_CLOUD_PROJECT}/${SERVICE_NAME}:latest"

echo "Building image ${IMAGE}"
gcloud builds submit --tag "${IMAGE}"

echo "Deploying ${SERVICE_NAME} to Cloud Run in ${REGION}"
gcloud run deploy "${SERVICE_NAME}" \
  --image "${IMAGE}" \
  --region "${REGION}" \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars "GOOGLE_CLOUD_PROJECT=${GOOGLE_CLOUD_PROJECT},GOOGLE_CLOUD_REGION=${REGION}"

echo "Deployment complete."

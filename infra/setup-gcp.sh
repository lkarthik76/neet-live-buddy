#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GOOGLE_CLOUD_PROJECT:-}" ]]; then
  echo "GOOGLE_CLOUD_PROJECT is required"
  echo "Example: export GOOGLE_CLOUD_PROJECT=my-neet-project"
  exit 1
fi

REGION="${GOOGLE_CLOUD_REGION:-asia-south1}"
SERVICE_NAME="${SERVICE_NAME:-smart-study-buddy}"

echo "Using project: ${GOOGLE_CLOUD_PROJECT}"
echo "Using region:  ${REGION}"
echo "Service name:  ${SERVICE_NAME}"

gcloud config set project "${GOOGLE_CLOUD_PROJECT}"
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  firestore.googleapis.com \
  artifactregistry.googleapis.com \
  logging.googleapis.com

echo ""
echo "GCP services enabled."
echo "Next manual steps:"
echo "1) Create Firestore database if not created:"
echo "   gcloud firestore databases create --location=${REGION} --type=firestore-native"
echo "2) Set your API key in .env.local as GOOGLE_GENAI_API_KEY"
echo "3) Deploy app:"
echo "   ./infra/deploy-cloud-run.sh"

# Infrastructure Needed

## Mandatory (Hackathon-Compliant)

1. **Google Cloud Project**
   - Billing enabled
   - Region selected (recommended: `asia-south1` for India)

2. **Cloud Run**
   - Host the backend/web app
   - Public HTTPS endpoint for judges

3. **Gemini API Access**
   - API key or service account path configured
   - Model access enabled in your project

4. **Firestore**
   - Session metadata
   - Revision cards
   - Basic analytics on weak topics

## Strongly Recommended

5. **Cloud Storage**
   - Optional storage for snapshots or demo artifacts

6. **Cloud Logging / Error Reporting**
   - Request trace + error visibility during live demo

## Local Build Requirements

- Node.js 20+ and npm
- Google Cloud CLI (`gcloud`)
- `.env.local` file from `.env.example`

## Environment Variables

- `GOOGLE_CLOUD_PROJECT`
- `GOOGLE_CLOUD_REGION`
- `GOOGLE_GENAI_API_KEY`
- `FIRESTORE_DATABASE`

## Deployment Flow

1. Authenticate: `gcloud auth login`
2. Set project: `gcloud config set project <PROJECT_ID>`
3. Deploy: `./infra/deploy-cloud-run.sh`

## Cost-Control Checklist

- Set budget alert in GCP Billing
- Limit Cloud Run max instances during demo stage
- Avoid storing large media files unless required

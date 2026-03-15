# Setup and Run Guide (Step-by-Step)

This guide is for the current KMP mobile + Go backend MVP in this repository.

For NCERT PDF vectorization and retrieval setup, use `docs/ncert-rag-setup.md`.

## 1) Frontend and Backend Decision

Frontend: **Kotlin Multiplatform (Compose Multiplatform)** in `kmp-app`.

Backend: **Go microservice** (`services/tutor-go`) as primary tutor engine.
Mobile app calls backend over HTTP via `/tutor`.

## 2) Install Local Prerequisites (macOS)

```bash
brew install node
brew install --cask google-cloud-sdk
```

Verify:

```bash
node -v
npm -v
gcloud version
```

## 3) Authenticate GCP

```bash
gcloud auth login
gcloud auth application-default login
```

## 4) Configure Project Variables

```bash
export GOOGLE_CLOUD_PROJECT="your-gcp-project-id"
export GOOGLE_CLOUD_REGION="asia-south1"
```

## 5) Enable Required GCP Services

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy
./infra/setup-gcp.sh
```

If Firestore is not created yet, run:

```bash
gcloud firestore databases create --location=asia-south1 --type=firestore-native
```

## 6) Configure App Environment

```bash
cp .env.example .env.local
```

Edit `.env.local` and set:

- `GOOGLE_CLOUD_PROJECT`
- `GOOGLE_CLOUD_REGION`
- `GOOGLE_GENAI_API_KEY`
- `FIRESTORE_DATABASE`

## 7) Install and Run Locally

```bash
npm install
npm run dev
```

Open:

- `http://localhost:3000`

## 8) Deploy to Cloud Run

```bash
export GOOGLE_CLOUD_PROJECT="your-gcp-project-id"
export GOOGLE_CLOUD_REGION="asia-south1"
./infra/deploy-cloud-run.sh
```

After deploy, Cloud Run returns a public URL.

## 9) Go Microservice Setup (Primary Backend)

Install Go:

```bash
brew install go
go version
```

Run Go service:

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy/services/tutor-go
export GOOGLE_GENAI_API_KEY="your-genai-api-key"
export GO_TUTOR_MODEL="gemini-2.0-flash"
go run .
```

Enable proxy from Next.js to Go:

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy
cp .env.example .env.local
```

Set these in `.env.local`:

- `USE_GO_TUTOR_SERVICE=true`
- `TUTOR_SERVICE_URL=http://localhost:8081`

Restart `npm run dev`.

Deploy Go service to Cloud Run (optional):

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy
export GOOGLE_CLOUD_PROJECT="your-gcp-project-id"
export GOOGLE_CLOUD_REGION="asia-south1"
export GOOGLE_GENAI_API_KEY="your-genai-api-key"
./infra/deploy-go-tutor.sh
```

Then set `TUTOR_SERVICE_URL` to deployed URL in Next.js service env.

## 10) Demo Readiness Check

- App opens and accepts prompt
- Tutor API responds via Go service
- Environment variables set in deployment
- Cloud Run URL works on phone + laptop browser

## 11) Next Technical Step

Expand from current MVP, then add:

- camera frame upload
- microphone transcript integration
- Firestore write for revision cards

## 12) Mobile App Run (KMP)

```bash
cd /Users/karthik/Developer/Google_Hackthon/neet-live-buddy/kmp-app
# open in Android Studio and run :composeApp on emulator/device
```

Open `kmp-app` in Android Studio and run Android target.

Important:

- Update backend URL in `kmp-app/composeApp/src/commonMain/kotlin/com/neetbuddy/app/App.kt`.
- For device testing, use Cloud Run URL or LAN IP (not localhost).

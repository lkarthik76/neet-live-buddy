# tutor-go (Optional Microservice)

This is an optional Go backend for the tutor inference endpoint.

Use it when you want to scale backend performance while keeping the same Next.js frontend.

The service loads grounding content from `content/` (chapters, prompts, localization).

## Run locally

```bash
cd services/tutor-go
export GOOGLE_GENAI_API_KEY="your-key"
export GO_TUTOR_MODEL="gemini-2.0-flash"
export CONTENT_DIR="../../content"
go run .
```

Service starts on `http://localhost:8081`.

## Endpoints

- `GET /healthz`
- `POST /tutor`

## Example request

```bash
curl -X POST http://localhost:8081/tutor \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Explain osmosis","language":"english","subjectHint":"biology"}'
```

## Next step

Wire this service URL in the Next.js app using:

- `USE_GO_TUTOR_SERVICE=true`
- `TUTOR_SERVICE_URL=http://localhost:8081`

## Deploy to Cloud Run

From repo root:

```bash
export GOOGLE_CLOUD_PROJECT="your-project-id"
export GOOGLE_CLOUD_REGION="asia-south1"
export GOOGLE_GENAI_API_KEY="your-key"
./infra/deploy-go-tutor.sh
```

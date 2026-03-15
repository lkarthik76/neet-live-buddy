# NEET Live Buddy

**An AI-powered live tutor for NEET exam preparation** — scan a question, speak your doubt, get instant step-by-step explanations in English, Hindi, or Tamil.

Built for the [Gemini Live Agent Challenge](https://geminiliveagentchallenge.devpost.com/).

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        NEET Live Buddy                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────┐       ┌────────────────────────────┐  │
│  │   KMP Mobile App     │       │   Go Tutor Microservice    │  │
│  │   (Android / iOS)    │       │   (Cloud Run)              │  │
│  │                      │ HTTPS │                            │  │
│  │  • Camera capture    ├──────►│  • Gemini 2.5 Flash        │  │
│  │  • Speech-to-text    │       │  • NCERT RAG retrieval     │  │
│  │  • Text-to-speech    │◄──────┤  • Chapter-aware prompts   │  │
│  │  • Tamil/Hindi/Eng   │  JSON │  • Revision card gen       │  │
│  │  • Compose UI        │       │  • Multilingual output     │  │
│  └──────────────────────┘       └──────────┬─────────────────┘  │
│                                            │                    │
│                                 ┌──────────▼─────────────────┐  │
│                                 │   Gemini API               │  │
│                                 │                            │  │
│                                 │  • generateContent         │  │
│                                 │    (gemini-2.5-pro)        │  │
│                                 │  • embedContent            │  │
│                                 │    (gemini-embedding-001)  │  │
│                                 └────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────┐       ┌────────────────────────────┐  │
│  │   NCERT Content      │       │   Vector Index             │  │
│  │                      │       │                            │  │
│  │  • Chapter summaries │       │  • 1600+ embedded chunks   │  │
│  │  • System prompts    │       │  • Cosine similarity       │  │
│  │  • Term localization │       │  • Top-K retrieval         │  │
│  │  • Demo questions    │       │  • Built from NCERT PDFs   │  │
│  └──────────────────────┘       └────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │   Infrastructure (automated)                             │   │
│  │                                                          │   │
│  │  • infra/setup-gcp.sh — enable APIs                      │   │
│  │  • infra/deploy-go-tutor.sh — build & deploy Cloud Run   │   │
│  │  • infra/cloudbuild-go-tutor.yaml — Cloud Build config   │   │
│  │  • scripts/build_ncert_index.py — vectorize NCERT PDFs   │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

1. **Student asks a doubt** — via camera (scan question), voice (speak in Tamil/Hindi/English), or text
2. **KMP app sends request** to Go backend on Cloud Run over HTTPS
3. **Go service builds a prompt** with NCERT chapter context, localization rules, and confusion-level policy
4. **RAG retrieval** — student's query is embedded via `gemini-embedding-001`, matched against 1600+ NCERT chunks using cosine similarity, top-K results injected into prompt
5. **Gemini 2.5 Flash** generates a structured JSON response: explanation + chapter tag + revision card
6. **App displays answer** and reads it aloud via text-to-speech in the selected language

## Features

- **Multimodal input** — camera capture, speech-to-text, typed text
- **Trilingual** — English, Hindi, Tamil (input and output)
- **NCERT-grounded RAG** — answers cite real textbook content
- **Confusion detection** — re-explains in simpler language when toggled
- **Revision cards** — concept, key point, common trap, practice question
- **Voice output** — answers spoken aloud in the selected language
- **Automated cloud deployment** — scripted GCP setup and Cloud Run deploy

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Mobile App | Kotlin Multiplatform + Compose Multiplatform |
| Backend | Go microservice |
| AI Model | Gemini 2.5 Pro (generation) + gemini-embedding-001 (RAG) |
| Deployment | Google Cloud Run + Cloud Build |
| Vectorization | Python + PyPDF + Gemini Embeddings |
| Content | NCERT Physics, Chemistry, Biology (PDF → vector index) |

## Project Structure

```
neet-live-buddy/
├── kmp-app/                  # Kotlin Multiplatform mobile app
│   └── composeApp/           # Shared Compose UI + Android platform code
├── services/
│   └── tutor-go/             # Go tutor microservice (Gemini + RAG)
├── content/                  # NCERT chapters, prompts, localization
│   ├── chapters/             # Subject-wise chapter summaries
│   ├── localization/         # Term translations (en/hi/ta)
│   ├── prompts/              # System prompt and policies
│   ├── index/                # Vector embeddings index (JSONL)
│   └── demo/                 # Sample questions for demo
├── scripts/                  # NCERT PDF vectorization script
├── infra/                    # GCP setup and deploy scripts
└── docs/                     # Detailed setup and architecture docs
```

## Quick Start

### Prerequisites

- Go 1.22+
- JDK 17 + Android Studio (for KMP app)
- Python 3.10+ (for NCERT vectorization)
- Google Cloud CLI (`gcloud`)
- Gemini API key from [AI Studio](https://aistudio.google.com/)

### 1. Run Go backend locally

```bash
cd services/tutor-go
export GOOGLE_GENAI_API_KEY="your-key"
export CONTENT_DIR=../../content
export ENABLE_NCERT_RAG=true
export NCERT_VECTOR_INDEX_PATH=../../content/index/ncert_embeddings.jsonl
go run .
```

### 2. Run KMP mobile app

Open `kmp-app/` in Android Studio and run the Android target on emulator or device.

### 3. Deploy to Cloud Run

```bash
export GOOGLE_CLOUD_PROJECT="your-project"
export GOOGLE_GENAI_API_KEY="your-key"
./infra/setup-gcp.sh
./infra/deploy-go-tutor.sh
```

### 4. Build NCERT vector index (optional rebuild)

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install pypdf requests
export GOOGLE_GENAI_API_KEY="your-key"
python3 scripts/build_ncert_index.py --pdf-root ../ncert_content --output content/index/ncert_embeddings.jsonl
```

## Cloud Deployment Automation

All infrastructure is automated via scripts in `infra/`:

- **`setup-gcp.sh`** — enables Cloud Run, Cloud Build, Artifact Registry, Firestore, and Logging APIs
- **`deploy-go-tutor.sh`** — builds Docker image with Cloud Build, deploys to Cloud Run with all env vars
- **`cloudbuild-go-tutor.yaml`** — Cloud Build configuration for multi-stage Docker build

See `docs/hackathon-cloud-automation-proof.md` for full details.

## Live Demo

- **Cloud Run endpoint**: `https://neet-live-buddy-go-tutor-1092451837072.asia-south1.run.app`
- **Health check**: `curl https://neet-live-buddy-go-tutor-1092451837072.asia-south1.run.app/`
- **Try the API (curl)**: `curl -X POST https://neet-live-buddy-go-tutor-1092451837072.asia-south1.run.app/tutor -H "Content-Type: application/json" -d '{"prompt":"What is mitosis?","language":"tamil","subjectHint":"biology"}'
`

## Documentation

- [Setup & Run Guide](docs/setup-and-run.md)
- [GCP Deployment Guide](docs/deploy-gcp-end-to-end.md)
- [NCERT RAG Setup](docs/ncert-rag-setup.md)
- [Content & Gemini Usage](docs/content-and-gemini-usage.md)
- [Cloud Automation Proof](docs/hackathon-cloud-automation-proof.md)

## License

MIT

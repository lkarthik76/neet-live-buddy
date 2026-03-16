# Devpost Submission — NEET Live Buddy

## Project Title
NEET Live Buddy — AI-Powered Multilingual NEET Exam Tutor

## Elevator Pitch
Scan any NEET question with your camera, speak your doubt in Tamil, Hindi, or English, and get instant NCERT-grounded explanations with option-by-option analysis — powered by Gemini 2.5 Pro.

## Thumbnail
`docs/images/thumbnail.png`

## Short Description
A mobile-first AI tutor that lets NEET students scan questions, speak doubts, and get instant NCERT-grounded explanations in English, Hindi, or Tamil — with option-by-option analysis, revision cards, and voice output.

---

## Text Description

### What it does
NEET Live Buddy is a multimodal AI tutor designed for students preparing for India's NEET medical entrance exam. Students can:

- **Scan printed questions** with the camera — Gemini Vision reads the question and diagrams, then explains the answer with option-by-option analysis
- **Speak their doubt** in Tamil, Hindi, or English — speech-to-text captures the question, Gemini responds in the same language
- **Type a question** and get a detailed NEET answer-key style response
- **Hear the answer spoken aloud** via text-to-speech in their chosen language
- **Get revision cards** with every answer — concept, key point, common trap, and a practice MCQ

Every response is grounded in NCERT textbook content through RAG (Retrieval Augmented Generation) over 1,600+ embedded chunks from Physics, Chemistry, and Biology PDFs.

### Problem it solves
NEET students (1.8M+ annually in India) struggle to get instant, reliable doubt-solving in their own language. Existing AI tutors are generic — they don't understand NEET-specific reasoning, don't reference NCERT textbooks, and don't support regional languages. Students waste time searching YouTube or waiting for coaching center sessions.

NEET Live Buddy provides **exam-focused, NCERT-grounded, multilingual** instant help — like having a personal tutor available 24/7 on your phone.

### How we built it

**Frontend — Kotlin Multiplatform + Compose Multiplatform**
- Shared UI across Android and iOS
- CameraX for question scanning with image compression
- Android SpeechRecognizer for voice input in Tamil/Hindi/English
- Android TextToSpeech for spoken answers
- Ktor HTTP client for backend communication

**Backend — Go microservice on Google Cloud Run**
- Receives multimodal requests (text + image + language preference)
- Builds NEET-focused prompts with chapter context, localization rules, and confusion-level policies
- Performs RAG: embeds the student's query via `gemini-embedding-001`, searches a cosine-similarity vector index of 1,600+ NCERT chunks, injects top-K results into the prompt
- Calls `gemini-2.5-pro` with multimodal input (text + inline image) to generate structured JSON responses
- Returns answer, option analysis, difficulty level, NCERT reference, and revision card

**RAG Pipeline — Python**
- Extracts text from NCERT PDFs (Physics, Chemistry, Biology) using PyPDF
- Chunks text with overlap for context preservation
- Generates embeddings via `gemini-embedding-001` API
- Stores as JSONL vector index (loaded at service startup)

**Infrastructure — Fully automated**
- `infra/setup-gcp.sh` — enables Cloud Run, Cloud Build, Artifact Registry APIs
- `infra/deploy-go-tutor.sh` — builds Docker image via Cloud Build, deploys to Cloud Run
- `infra/cloudbuild-go-tutor.yaml` — multi-stage Docker build config
- Content and vector index baked into the Docker image

### Technologies used
- **Gemini 2.5 Pro** — multimodal generation (text + vision)
- **gemini-embedding-001** — text embeddings for RAG
- **Google Cloud Run** — serverless backend hosting
- **Google Cloud Build** — CI/CD image building
- **Go** — backend microservice
- **Kotlin Multiplatform + Compose Multiplatform** — cross-platform mobile app
- **Python + PyPDF** — NCERT PDF processing and vectorization
- **CameraX** — Android camera capture
- **Android SpeechRecognizer** — speech-to-text
- **Android TextToSpeech** — voice output

### Data sources
- **NCERT textbooks** (Physics, Chemistry, Biology) — official free PDFs from ncert.nic.in
- Custom chapter summaries and NEET-weighted topic metadata
- Multilingual term localization (English ↔ Hindi ↔ Tamil) for scientific terminology

### Findings and learnings
1. **Image size matters for vision APIs** — camera captures at full resolution (12MP) caused timeouts. Resizing to 1024px max before base64 encoding was critical for reliable performance.
2. **RAG significantly improves answer quality** — without NCERT grounding, Gemini sometimes gave generic answers. With RAG, responses cite specific textbook content and are exam-relevant.
3. **Multilingual support works surprisingly well** — Gemini 2.5 Pro generates fluent Tamil and Hindi explanations with correct scientific terminology, especially with localization term references in the prompt.
4. **Structured JSON output is reliable** — asking Gemini for a specific JSON schema with option-by-option analysis produces consistent, parseable responses.
5. **Go + Cloud Run is an excellent combo** — cold starts are fast (~1-2s), the Go binary is tiny, and the free tier is generous enough for a hackathon demo.

---

## Public Code Repository
https://github.com/YOUR_USERNAME/neet-live-buddy

*(Update with your actual GitHub URL after pushing)*

## Google Cloud Deployment Proof
- **Live endpoint**: https://neet-live-buddy-go-tutor-1092451837072.asia-south1.run.app
- **Health check**: https://neet-live-buddy-go-tutor-1092451837072.asia-south1.run.app/healthz
- **Cloud deployment scripts**: `infra/setup-gcp.sh`, `infra/deploy-go-tutor.sh`, `infra/cloudbuild-go-tutor.yaml`
- **Separate recording**: Show GCP Console → Cloud Run → service running → logs showing Gemini API calls

## Architecture Diagram
See `docs/images/architecture-diagram.png` (also embedded in README.md)

## Demo Video
<4 min video showing:
1. Text question → Tamil answer with option analysis
2. Camera scan → Gemini Vision reads question → detailed explanation
3. Voice input in Hindi → spoken answer
4. Cloud Run console proof

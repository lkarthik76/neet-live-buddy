# Content Storage and Gemini Usage

This project uses repository content files to ground Gemini responses.

## Where content lives

- `content/neet/chapters.json`
  - chapter map and high-priority topics by subject
- `content/prompts/system_tutor_prompt.txt`
  - global tutor behavior and output schema constraints
- `content/prompts/language_policy_prompt.txt`
  - multilingual response policy (English/Hindi/Tamil)
- `content/prompts/confusion_prompt.txt`
  - simplified re-explain behavior
- `content/localization/neet_terms.json`
  - bilingual/trilingual glossary hints
- `content/demo/demo_questions.json`
  - demo/test prompts
- `content/index/ncert_embeddings.jsonl`
  - vector index generated from NCERT PDFs for RAG retrieval

## How Go service uses content

In `services/tutor-go/main.go`:

1. On startup, service loads content files into memory.
2. For each `/tutor` request, it builds a prompt using:
   - base system prompt
   - language policy (English, Hindi, Tamil, or auto)
   - confusion policy (if `confused=true`)
   - subject chapter grounding from `chapters.json`
   - term localization hints from `neet_terms.json`
3. If RAG is enabled, service retrieves top NCERT chunks from vector index.
4. Prompt is sent to Gemini `generateContent`.
5. Gemini must return strict JSON:
   - `answer`
   - `chapter`
   - `revisionCard.{concept,keyPoint,commonTrap,practiceQuestion}`

If content files are missing, the service falls back to built-in defaults.

## Local content loading

The Go service looks for content in:

1. `CONTENT_DIR` env var (highest priority)
2. `../../content`
3. `./content`
4. `/app/content` (container default)

## Deploy behavior

`infra/deploy-go-tutor.sh` builds with `infra/cloudbuild-go-tutor.yaml`, which includes:

- `services/tutor-go/`
- `content/`

So deployed Cloud Run service gets the same content and policies.

For large NCERT vector indexes, set:

- `NCERT_VECTOR_INDEX_GCS_URI=gs://<bucket>/index/ncert_embeddings.jsonl`
- `NCERT_VECTOR_INDEX_PATH=/tmp/ncert_embeddings.jsonl`

Then service downloads index from GCS at startup before loading retriever.

## NCERT Vectorization

Use `docs/ncert-rag-setup.md` for full PDF -> embeddings -> retrieval setup.

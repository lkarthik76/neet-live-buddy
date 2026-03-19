package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"google.golang.org/api/androidpublisher/v3"
	"google.golang.org/api/option"
)

type TutorRequest struct {
	DeviceId     string `json:"deviceId,omitempty"`
	Prompt       string `json:"prompt"`
	SubjectHint  string `json:"subjectHint,omitempty"`
	Language     string `json:"language"`
	Confused     bool   `json:"confused,omitempty"`
	ImageContext string `json:"imageContext,omitempty"`
	ImageBase64  string `json:"imageBase64,omitempty"`
}

type OptionAnalysis struct {
	Option      string `json:"option"`
	Text        string `json:"text"`
	Correct     bool   `json:"correct"`
	Explanation string `json:"explanation"`
}

type RevisionCard struct {
	Concept          string `json:"concept"`
	KeyPoint         string `json:"keyPoint"`
	CommonTrap       string `json:"commonTrap"`
	PracticeQuestion string `json:"practiceQuestion"`
}

type TutorResponse struct {
	Answer         string           `json:"answer"`
	Chapter        string           `json:"chapter"`
	CorrectOption  string           `json:"correctOption,omitempty"`
	Options        []OptionAnalysis `json:"options,omitempty"`
	Difficulty     string           `json:"difficulty,omitempty"`
	NcertReference string           `json:"ncertReference,omitempty"`
	RevisionCard   RevisionCard     `json:"revisionCard"`
	Usage          *UsageInfo       `json:"usage,omitempty"`
}

type ChapterEntry struct {
	Chapter    string   `json:"chapter"`
	NeetWeight string   `json:"neetWeight"`
	Topics     []string `json:"topics"`
}

type ChaptersFile struct {
	Subjects map[string][]ChapterEntry `json:"subjects"`
}

type TermEntry struct {
	English string `json:"english"`
	Hindi   string `json:"hindi"`
	Tamil   string `json:"tamil"`
}

type TermsFile struct {
	Terms []TermEntry `json:"terms"`
}

type ContentStore struct {
	SystemPrompt    string
	LanguagePolicy  string
	ConfusionPolicy string
	Chapters        ChaptersFile
	Terms           TermsFile
}

type GeminiClient struct {
	apiKey     string
	model      string
	httpClient *http.Client
	content    *ContentStore
	retriever  *Retriever
	ragEnabled bool
	ragTopK    int
}

func normalizeLanguage(input string) string {
	switch strings.ToLower(strings.TrimSpace(input)) {
	case "english", "en":
		return "english"
	case "hindi", "hi":
		return "hindi"
	case "tamil", "ta":
		return "tamil"
	case "auto":
		return "auto"
	default:
		return ""
	}
}

func NewGeminiClientFromEnv(content *ContentStore, retriever *Retriever) *GeminiClient {
	apiKey := strings.TrimSpace(os.Getenv("GOOGLE_GENAI_API_KEY"))
	model := strings.TrimSpace(os.Getenv("GO_TUTOR_MODEL"))
	if model == "" {
		model = "gemini-2.5-pro"
	}
	ragEnabled := strings.EqualFold(strings.TrimSpace(os.Getenv("ENABLE_NCERT_RAG")), "true")
	ragTopK := 4
	if v := strings.TrimSpace(os.Getenv("NCERT_TOP_K")); v != "" {
		if parsed, err := strconv.Atoi(v); err == nil && parsed > 0 {
			ragTopK = parsed
		}
	}

	return &GeminiClient{
		apiKey: apiKey,
		model:  model,
		httpClient: &http.Client{
			Timeout: 90 * time.Second,
		},
		content:    content,
		retriever:  retriever,
		ragEnabled: ragEnabled && retriever != nil,
		ragTopK:    ragTopK,
	}
}

func (c *GeminiClient) IsConfigured() bool {
	return c.apiKey != ""
}

func (c *GeminiClient) GenerateTutorResponse(ctx context.Context, req TutorRequest) (TutorResponse, error) {
	ragContext := ""
	if c.ragEnabled {
		queryText := strings.TrimSpace(req.Prompt + " " + req.ImageContext)
		if queryText != "" {
			queryEmbedding, err := c.embedQuery(ctx, queryText)
			if err != nil {
				log.Printf("RAG query embedding failed: %v", err)
			} else {
				hits := c.retriever.Search(queryEmbedding, c.ragTopK)
				ragContext = formatRagContext(hits)
			}
		}
	}

	parts := []any{
		map[string]string{
			"text": buildPrompt(req, c.content, ragContext),
		},
	}
	if strings.TrimSpace(req.ImageBase64) != "" {
		parts = append(parts, map[string]any{
			"inlineData": map[string]string{
				"mimeType": "image/jpeg",
				"data":     req.ImageBase64,
			},
		})
		log.Printf("image attached: %d chars base64", len(req.ImageBase64))
	}

	payload := map[string]any{
		"contents": []map[string]any{
			{
				"role":  "user",
				"parts": parts,
			},
		},
		"generationConfig": map[string]any{
			"temperature": 0.3,
		},
	}

	rawPayload, err := json.Marshal(payload)
	if err != nil {
		return TutorResponse{}, err
	}

	url := fmt.Sprintf(
		"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
		c.model,
		c.apiKey,
	)

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(rawPayload))
	if err != nil {
		return TutorResponse{}, err
	}
	httpReq.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return TutorResponse{}, err
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return TutorResponse{}, fmt.Errorf("gemini api error: %s", string(body))
	}

	parsed, err := parseGeminiResponse(body)
	if err != nil {
		return TutorResponse{}, err
	}
	return parsed, nil
}

func (c *GeminiClient) embedQuery(ctx context.Context, text string) ([]float64, error) {
	url := fmt.Sprintf(
		"https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=%s",
		c.apiKey,
	)
	payload := map[string]any{
		"model": "models/gemini-embedding-001",
		"content": map[string]any{
			"parts": []map[string]string{
				{"text": text},
			},
		},
	}
	rawPayload, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(rawPayload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("embedding api error: %s", string(body))
	}

	type embeddingResponse struct {
		Embedding struct {
			Values []float64 `json:"values"`
		} `json:"embedding"`
	}
	var parsed embeddingResponse
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, err
	}
	if len(parsed.Embedding.Values) == 0 {
		return nil, errors.New("empty query embedding")
	}
	return parsed.Embedding.Values, nil
}

func buildPrompt(req TutorRequest, content *ContentStore, ragContext string) string {
	language := req.Language
	if language == "auto" {
		language = "same language as student prompt"
	}
	levelRule := "Give NEET-focused step-by-step explanation with concise reasoning."
	if req.Confused {
		levelRule = content.ConfusionPolicy
	}

	subject := req.SubjectHint
	if subject == "" {
		subject = "biology"
	}

	subjectContext := summarizeSubject(content, subject)
	termContext := summarizeTerms(content)

	systemPrompt := content.SystemPrompt
	if strings.TrimSpace(systemPrompt) == "" {
		systemPrompt = "You are NEET Live Buddy, an exam-focused tutor for Physics, Chemistry, and Biology."
	}

	languagePolicy := content.LanguagePolicy
	if strings.TrimSpace(languagePolicy) == "" {
		languagePolicy = "Follow the requested language and keep science terms accurate."
	}

	imageSection := ""
	if strings.TrimSpace(req.ImageContext) != "" {
		imageSection = "\nImage context from OCR/screenshot:\n" + req.ImageContext + "\n"
	}
	ragSection := ""
	if strings.TrimSpace(ragContext) != "" {
		ragSection = "\nRetrieved NCERT references (use as grounding context):\n" + ragContext + "\n"
	}

	return fmt.Sprintf(`%s
Output must be valid JSON only with this exact schema:
{
  "answer": "string (detailed step-by-step explanation of the answer)",
  "chapter": "string (NCERT chapter name)",
  "correctOption": "string (e.g. 'A' or 'B' — only if the question has MCQ options, otherwise empty)",
  "options": [
    {"option": "A", "text": "option text", "correct": true/false, "explanation": "why this option is correct or wrong"}
  ],
  "difficulty": "string (Easy / Medium / Hard)",
  "ncertReference": "string (specific NCERT book, chapter, page or section reference)",
  "revisionCard": {
    "concept": "string (core concept being tested)",
    "keyPoint": "string (most important fact to remember for NEET)",
    "commonTrap": "string (common mistake students make in this topic)",
    "practiceQuestion": "string (one similar MCQ question with 4 options for practice)"
  }
}

IMPORTANT formatting rules:
- If the student's question is an MCQ (has options A/B/C/D), analyze EVERY option — explain why each is correct or incorrect.
- If the student's question is open-ended (no options), provide a thorough explanation and set options to empty array [].
- If an image is attached, carefully read the question/diagram from the image.
- The "answer" field should be a detailed NEET answer-key style explanation (like Shiksha/Allen answer keys).
- The "practiceQuestion" in revisionCard must include 4 options labeled (A), (B), (C), (D).

Rules:
- Teach in language: %s
- Subject hint: %s
- Subject grounding:
%s
- Term localization reference:
%s
- %s
- %s
- If uncertain, acknowledge uncertainty and ask for a clearer question.
- No markdown, no code fences, JSON only.

Student prompt:
%s
%s
%s`, systemPrompt, language, subject, subjectContext, termContext, levelRule, languagePolicy, req.Prompt, imageSection, ragSection)
}

func parseGeminiResponse(raw []byte) (TutorResponse, error) {
	type geminiResponse struct {
		Candidates []struct {
			Content struct {
				Parts []struct {
					Text string `json:"text"`
				} `json:"parts"`
			} `json:"content"`
		} `json:"candidates"`
	}

	var gResp geminiResponse
	if err := json.Unmarshal(raw, &gResp); err != nil {
		return TutorResponse{}, err
	}

	if len(gResp.Candidates) == 0 || len(gResp.Candidates[0].Content.Parts) == 0 {
		return TutorResponse{}, errors.New("empty gemini response")
	}

	text := gResp.Candidates[0].Content.Parts[0].Text
	text = strings.TrimSpace(text)
	text = strings.TrimPrefix(text, "```json")
	text = strings.TrimPrefix(text, "```")
	text = strings.TrimSuffix(text, "```")
	text = strings.TrimSpace(text)

	var output TutorResponse
	if err := json.Unmarshal([]byte(text), &output); err != nil {
		return TutorResponse{}, fmt.Errorf("invalid json payload from gemini: %w", err)
	}

	if output.Answer == "" || output.Chapter == "" {
		return TutorResponse{}, errors.New("incomplete tutor response")
	}
	return output, nil
}

func createFallbackResponse(req TutorRequest) TutorResponse {
	chapter := "Cell: The Unit of Life"
	switch req.SubjectHint {
	case "physics":
		chapter = "Current Electricity"
	case "chemistry":
		chapter = "Chemical Bonding"
	}

	return TutorResponse{
		Answer:     "⚠️ AI is temporarily unavailable. Please try again in a few seconds. Your question: " + req.Prompt,
		Chapter:    chapter,
		Difficulty: "",
		RevisionCard: RevisionCard{
			Concept:          "Retry in a moment",
			KeyPoint:         "The AI service may be rate-limited. Wait a few seconds and tap Ask again.",
			CommonTrap:       "",
			PracticeQuestion: "",
		},
	}
}

func healthHandler(w http.ResponseWriter, _ *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ok"))
}

func resolveContentDir() string {
	if dir := strings.TrimSpace(os.Getenv("CONTENT_DIR")); dir != "" {
		return dir
	}

	candidates := []string{
		"../../content",
		"./content",
		"/app/content",
	}
	for _, c := range candidates {
		if _, err := os.Stat(c); err == nil {
			return c
		}
	}
	return ""
}

func readTextFile(path string, fallback string) string {
	raw, err := os.ReadFile(path)
	if err != nil {
		return fallback
	}
	return strings.TrimSpace(string(raw))
}

func readJSONFile[T any](path string, target *T) error {
	raw, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	return json.Unmarshal(raw, target)
}

func loadContentStore() *ContentStore {
	store := &ContentStore{
		SystemPrompt:    "You are NEET Live Buddy, an exam-focused tutor for NEET UG.",
		LanguagePolicy:  "Respect requested language and preserve scientific terms.",
		ConfusionPolicy: "Student is confused. Re-explain in simpler language with 3 short steps and one analogy.",
		Chapters:        ChaptersFile{Subjects: map[string][]ChapterEntry{}},
		Terms:           TermsFile{},
	}

	contentDir := resolveContentDir()
	if contentDir == "" {
		log.Println("content directory not found, using built-in defaults")
		return store
	}

	store.SystemPrompt = readTextFile(filepath.Join(contentDir, "prompts", "system_tutor_prompt.txt"), store.SystemPrompt)
	store.LanguagePolicy = readTextFile(filepath.Join(contentDir, "prompts", "language_policy_prompt.txt"), store.LanguagePolicy)
	store.ConfusionPolicy = readTextFile(filepath.Join(contentDir, "prompts", "confusion_prompt.txt"), store.ConfusionPolicy)

	if err := readJSONFile(filepath.Join(contentDir, "neet", "chapters.json"), &store.Chapters); err != nil {
		log.Printf("failed to load chapters.json: %v", err)
	}
	if err := readJSONFile(filepath.Join(contentDir, "localization", "neet_terms.json"), &store.Terms); err != nil {
		log.Printf("failed to load neet_terms.json: %v", err)
	}

	log.Printf("content loaded from %s", contentDir)
	return store
}

func summarizeSubject(content *ContentStore, subject string) string {
	chapters, ok := content.Chapters.Subjects[subject]
	if !ok || len(chapters) == 0 {
		return "No chapter map available."
	}

	lines := make([]string, 0, len(chapters))
	for i, c := range chapters {
		if i >= 3 {
			break
		}
		lines = append(lines, fmt.Sprintf("- %s (%s): %s", c.Chapter, c.NeetWeight, strings.Join(c.Topics, ", ")))
	}
	return strings.Join(lines, "\n")
}

func summarizeTerms(content *ContentStore) string {
	if len(content.Terms.Terms) == 0 {
		return "No glossary available."
	}
	var lines []string
	for i, t := range content.Terms.Terms {
		if i >= 4 {
			break
		}
		lines = append(lines, fmt.Sprintf("- %s | Hindi: %s | Tamil: %s", t.English, t.Hindi, t.Tamil))
	}
	return strings.Join(lines, "\n")
}

func formatRagContext(hits []SearchHit) string {
	if len(hits) == 0 {
		return ""
	}
	var lines []string
	for i, hit := range hits {
		if i >= 5 {
			break
		}
		text := strings.TrimSpace(hit.Chunk.Text)
		if len(text) > 420 {
			text = text[:420] + "..."
		}
		lines = append(lines, fmt.Sprintf(
			"- [%s p.%d score=%.3f] %s",
			filepath.Base(hit.Chunk.SourceFile),
			hit.Chunk.Page,
			hit.Score,
			text,
		))
	}
	return strings.Join(lines, "\n")
}

func resolveIndexPath(contentDir string) string {
	if p := strings.TrimSpace(os.Getenv("NCERT_VECTOR_INDEX_PATH")); p != "" {
		return p
	}
	if contentDir != "" {
		return filepath.Join(contentDir, "index", "ncert_embeddings.jsonl")
	}
	return ""
}

func parseGCSURI(uri string) (bucket string, object string, err error) {
	if !strings.HasPrefix(uri, "gs://") {
		return "", "", fmt.Errorf("invalid GCS uri (must start with gs://): %s", uri)
	}
	trimmed := strings.TrimPrefix(uri, "gs://")
	parts := strings.SplitN(trimmed, "/", 2)
	if len(parts) != 2 || strings.TrimSpace(parts[0]) == "" || strings.TrimSpace(parts[1]) == "" {
		return "", "", fmt.Errorf("invalid GCS uri format: %s", uri)
	}
	return parts[0], parts[1], nil
}

func getMetadataAccessToken(client *http.Client) (string, error) {
	req, err := http.NewRequest(http.MethodGet, "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token", nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Metadata-Flavor", "Google")
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("metadata token request failed: %s", string(body))
	}
	var parsed struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return "", err
	}
	if strings.TrimSpace(parsed.AccessToken) == "" {
		return "", errors.New("empty access token from metadata server")
	}
	return parsed.AccessToken, nil
}

func downloadFile(url string, localPath string, headers map[string]string) error {
	client := &http.Client{Timeout: 90 * time.Second}
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("download failed: %s", string(body))
	}
	if err := os.MkdirAll(filepath.Dir(localPath), 0o755); err != nil {
		return err
	}
	out, err := os.Create(localPath)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, resp.Body)
	return err
}

func downloadIndexFromGCSIfConfigured(localPath string) string {
	gcsURI := strings.TrimSpace(os.Getenv("NCERT_VECTOR_INDEX_GCS_URI"))
	publicURL := strings.TrimSpace(os.Getenv("NCERT_VECTOR_INDEX_GCS_PUBLIC_URL"))
	if gcsURI == "" && publicURL == "" {
		return localPath
	}
	if localPath == "" {
		localPath = "/tmp/ncert_embeddings.jsonl"
	}

	// Public URL flow (for quick hackathon setup).
	if publicURL != "" {
		if err := downloadFile(publicURL, localPath, map[string]string{}); err != nil {
			log.Printf("RAG index public download failed, using local path if available: %v", err)
			return localPath
		}
		log.Printf("RAG index downloaded from public URL -> %s", localPath)
		return localPath
	}

	// Private GCS flow using Cloud Run metadata token.
	bucket, object, err := parseGCSURI(gcsURI)
	if err != nil {
		log.Printf("invalid NCERT_VECTOR_INDEX_GCS_URI: %v", err)
		return localPath
	}
	client := &http.Client{Timeout: 90 * time.Second}
	token, err := getMetadataAccessToken(client)
	if err != nil {
		log.Printf("metadata token unavailable; cannot pull private GCS index: %v", err)
		return localPath
	}

	encodedObject := url.QueryEscape(object)
	downloadURL := fmt.Sprintf("https://storage.googleapis.com/storage/v1/b/%s/o/%s?alt=media", bucket, encodedObject)
	headers := map[string]string{
		"Authorization": "Bearer " + token,
	}
	if err := downloadFile(downloadURL, localPath, headers); err != nil {
		log.Printf("RAG index private download failed, using local path if available: %v", err)
		return localPath
	}
	log.Printf("RAG index downloaded from %s -> %s", gcsURI, localPath)
	return localPath
}

func tutorHandler(gemini *GeminiClient, tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}

		var req TutorRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(req.Prompt) == "" || strings.TrimSpace(req.Language) == "" {
			http.Error(w, "prompt and language are required", http.StatusBadRequest)
			return
		}
		normalizedLang := normalizeLanguage(req.Language)
		if normalizedLang == "" {
			http.Error(w, "language must be one of: english, hindi, tamil, auto", http.StatusBadRequest)
			return
		}
		req.Language = normalizedLang

		deviceId := strings.TrimSpace(req.DeviceId)
		if deviceId == "" {
			deviceId = "anonymous"
		}

		usage, allowed := tracker.CheckAndIncrement(deviceId)
		if !allowed {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusTooManyRequests)
			_ = json.NewEncoder(w).Encode(map[string]any{
				"error":   "daily_limit_reached",
				"message": "You've used all your free questions for today. Upgrade to Pro for unlimited access!",
				"usage":   usage,
			})
			return
		}

		var res TutorResponse
		if gemini.IsConfigured() {
			ctx, cancel := context.WithTimeout(r.Context(), 90*time.Second)
			defer cancel()
			modelRes, err := gemini.GenerateTutorResponse(ctx, req)
			if err != nil {
				log.Printf("gemini call failed, using fallback: %v", err)
				res = createFallbackResponse(req)
			} else {
				res = modelRes
			}
		} else {
			log.Println("GOOGLE_GENAI_API_KEY missing; using fallback response")
			res = createFallbackResponse(req)
		}

		if usage.Tier == TierFree {
			res = StripForFreeTier(res)
		}

		res.Usage = &usage
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(res)
	}
}

func usageHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		deviceId := strings.TrimSpace(r.URL.Query().Get("deviceId"))
		if deviceId == "" {
			http.Error(w, "deviceId query parameter is required", http.StatusBadRequest)
			return
		}
		usage := tracker.GetUsage(deviceId)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(usage)
	}
}

type linkRequest struct {
	DeviceId string `json:"deviceId"`
	Email    string `json:"email"`
}

type tierRequest struct {
	DeviceId string `json:"deviceId"`
	Email    string `json:"email"`
	Tier     string `json:"tier"`
}

type googleVerifyRequest struct {
	DeviceId      string `json:"deviceId"`
	Email         string `json:"email"`
	ProductId     string `json:"productId"`
	PurchaseToken string `json:"purchaseToken"`
	PackageName   string `json:"packageName"`
}

type appleVerifyRequest struct {
	DeviceId    string `json:"deviceId"`
	Email       string `json:"email"`
	ProductId   string `json:"productId"`
	ReceiptData string `json:"receiptData"`
}

type saveProfileRequest struct {
	StudentName string `json:"studentName"`
	Email       string `json:"email"`
	PhoneNumber string `json:"phoneNumber"`
	ClassLevel  string `json:"classLevel"`
}

type authIdentity struct {
	Email         string
	FirebaseUID   string
	EmailVerified bool
}

func verifyFirebaseIDToken(ctx context.Context, idToken string) (authIdentity, error) {
	apiKey := strings.TrimSpace(os.Getenv("FIREBASE_WEB_API_KEY"))
	if apiKey == "" {
		return authIdentity{}, errors.New("FIREBASE_WEB_API_KEY is not configured")
	}
	if strings.TrimSpace(idToken) == "" {
		return authIdentity{}, errors.New("missing Firebase ID token")
	}

	reqBody := map[string]any{"idToken": idToken}
	raw, _ := json.Marshal(reqBody)
	url := "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + apiKey
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(raw))
	if err != nil {
		return authIdentity{}, err
	}
	req.Header.Set("Content-Type", "application/json")
	client := &http.Client{Timeout: 20 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return authIdentity{}, err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return authIdentity{}, fmt.Errorf("firebase token verify failed: %s", string(body))
	}

	var parsed struct {
		Users []struct {
			Email         string `json:"email"`
			LocalID       string `json:"localId"`
			EmailVerified bool   `json:"emailVerified"`
		} `json:"users"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return authIdentity{}, err
	}
	if len(parsed.Users) == 0 || strings.TrimSpace(parsed.Users[0].Email) == "" {
		return authIdentity{}, errors.New("firebase token has no email")
	}
	if !parsed.Users[0].EmailVerified {
		return authIdentity{}, errors.New("firebase email is not verified")
	}
	return authIdentity{
		Email:         strings.TrimSpace(parsed.Users[0].Email),
		FirebaseUID:   strings.TrimSpace(parsed.Users[0].LocalID),
		EmailVerified: parsed.Users[0].EmailVerified,
	}, nil
}

func authenticatedIdentityFromRequest(w http.ResponseWriter, r *http.Request) (authIdentity, bool) {
	authz := strings.TrimSpace(r.Header.Get("Authorization"))
	if !strings.HasPrefix(authz, "Bearer ") {
		http.Error(w, "Authorization Bearer token is required", http.StatusUnauthorized)
		return authIdentity{}, false
	}
	idToken := strings.TrimSpace(strings.TrimPrefix(authz, "Bearer "))
	identity, err := verifyFirebaseIDToken(r.Context(), idToken)
	if err != nil {
		http.Error(w, "Invalid auth token: "+err.Error(), http.StatusUnauthorized)
		return authIdentity{}, false
	}
	return identity, true
}

func linkEmailHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		var req linkRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(req.DeviceId) == "" {
			http.Error(w, "deviceId is required", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(req.Email) != "" && !strings.EqualFold(strings.TrimSpace(req.Email), identity.Email) {
			http.Error(w, "request email must match authenticated account", http.StatusForbidden)
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}
		usage := tracker.LinkEmail(req.DeviceId, identity.Email)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(usage)
	}
}

func restoreHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		var req linkRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(req.DeviceId) == "" {
			http.Error(w, "deviceId is required", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(req.Email) != "" && !strings.EqualFold(strings.TrimSpace(req.Email), identity.Email) {
			http.Error(w, "request email must match authenticated account", http.StatusForbidden)
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}
		usage, found := tracker.RestoreByEmail(req.DeviceId, identity.Email)
		if !found {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusNotFound)
			_ = json.NewEncoder(w).Encode(map[string]any{
				"error":   "account_not_found",
				"message": "No account found with this email. Sign in on your original device first.",
			})
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(usage)
	}
}

func tierHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !strings.EqualFold(strings.TrimSpace(os.Getenv("ALLOW_TIER_ENDPOINT")), "true") {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		var req tierRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}

		deviceId := strings.TrimSpace(req.DeviceId)
		email := strings.TrimSpace(req.Email)
		tierStr := strings.ToLower(strings.TrimSpace(req.Tier))
		if deviceId == "" {
			http.Error(w, "deviceId is required", http.StatusBadRequest)
			return
		}

		var tier Tier
		switch tierStr {
		case string(TierFree):
			tier = TierFree
		case string(TierPro):
			tier = TierPro
		case string(TierUltimate):
			tier = TierUltimate
		default:
			http.Error(w, "tier must be one of: free, pro, ultimate", http.StatusBadRequest)
			return
		}

		if email != "" && !strings.EqualFold(email, identity.Email) {
			http.Error(w, "request email must match authenticated account", http.StatusForbidden)
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}
		tracker.LinkEmail(deviceId, identity.Email)
		tracker.SetTierByEmail(identity.Email, tier)

		usage := tracker.GetUsage(deviceId)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(usage)
	}
}

func tierFromProductID(productID string) (Tier, bool) {
	switch strings.TrimSpace(productID) {
	case "neet_pro_monthly":
		return TierPro, true
	case "neet_ultimate_monthly":
		return TierUltimate, true
	default:
		return "", false
	}
}

func isGoogleSubscriptionActive(state string) bool {
	switch strings.TrimSpace(state) {
	case "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_ON_HOLD":
		return true
	default:
		return false
	}
}

func verifyGoogleSubscription(ctx context.Context, packageName, purchaseToken, expectedProductId string) error {
	if strings.TrimSpace(packageName) == "" || strings.TrimSpace(purchaseToken) == "" {
		return errors.New("packageName and purchaseToken are required")
	}

	apiKey := strings.TrimSpace(os.Getenv("GOOGLE_PLAY_API_KEY"))
	var (
		service *androidpublisher.Service
		err     error
	)
	if apiKey != "" {
		service, err = androidpublisher.NewService(ctx, option.WithAPIKey(apiKey))
	} else {
		service, err = androidpublisher.NewService(ctx)
	}
	if err != nil {
		return fmt.Errorf("google play service init failed: %w", err)
	}

	sub, err := service.Purchases.Subscriptionsv2.Get(packageName, purchaseToken).Do()
	if err != nil {
		return fmt.Errorf("play subscription lookup failed: %w", err)
	}

	if !isGoogleSubscriptionActive(sub.SubscriptionState) {
		return fmt.Errorf("subscription is not active: %s", sub.SubscriptionState)
	}

	foundProduct := false
	for _, item := range sub.LineItems {
		if strings.TrimSpace(item.ProductId) == strings.TrimSpace(expectedProductId) {
			foundProduct = true
			break
		}
	}
	if !foundProduct {
		return fmt.Errorf("product mismatch: expected %s", expectedProductId)
	}

	return nil
}

func googleBillingVerifyHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		var req googleVerifyRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}

		deviceID := strings.TrimSpace(req.DeviceId)
		email := strings.TrimSpace(req.Email)
		if deviceID == "" {
			http.Error(w, "deviceId is required", http.StatusBadRequest)
			return
		}
		if email != "" && !strings.EqualFold(email, identity.Email) {
			http.Error(w, "request email must match authenticated account", http.StatusForbidden)
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}

		tier, ok := tierFromProductID(req.ProductId)
		if !ok {
			http.Error(w, "unsupported productId", http.StatusBadRequest)
			return
		}

		packageName := strings.TrimSpace(req.PackageName)
		if packageName == "" {
			packageName = strings.TrimSpace(os.Getenv("GOOGLE_PLAY_PACKAGE_NAME"))
		}
		if packageName == "" {
			packageName = "com.smartstudybuddy.app"
		}

		verifyErr := verifyGoogleSubscription(
			r.Context(),
			packageName,
			strings.TrimSpace(req.PurchaseToken),
			strings.TrimSpace(req.ProductId),
		)
		if verifyErr != nil {
			tracker.SaveGoogleEntitlement(
				identity.Email,
				strings.TrimSpace(req.ProductId),
				strings.TrimSpace(req.PurchaseToken),
				packageName,
				"inactive",
				verifyErr.Error(),
			)
			log.Printf("google purchase verify failed: %v", verifyErr)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusPaymentRequired)
			_ = json.NewEncoder(w).Encode(map[string]any{
				"error":   "purchase_verification_failed",
				"message": verifyErr.Error(),
			})
			return
		}

		tracker.LinkEmail(deviceID, identity.Email)
		tracker.SetTierByEmail(identity.Email, tier)
		tracker.SaveGoogleEntitlement(
			identity.Email,
			strings.TrimSpace(req.ProductId),
			strings.TrimSpace(req.PurchaseToken),
			packageName,
			"active",
			"",
		)
		usage := tracker.GetUsage(deviceID)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(usage)
	}
}

func verifyAppleReceipt(receiptData, expectedProductID string) error {
	sharedSecret := strings.TrimSpace(os.Getenv("APPLE_SHARED_SECRET"))
	if sharedSecret == "" {
		return errors.New("APPLE_SHARED_SECRET is not configured")
	}
	if strings.TrimSpace(receiptData) == "" {
		return errors.New("receiptData is required")
	}

	payload := map[string]any{
		"receipt-data":             receiptData,
		"password":                 sharedSecret,
		"exclude-old-transactions": true,
	}

	callVerify := func(url string) (map[string]any, error) {
		raw, _ := json.Marshal(payload)
		req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(raw))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "application/json")
		client := &http.Client{Timeout: 30 * time.Second}
		resp, err := client.Do(req)
		if err != nil {
			return nil, err
		}
		defer resp.Body.Close()
		body, _ := io.ReadAll(resp.Body)
		if resp.StatusCode >= 400 {
			return nil, fmt.Errorf("apple verify http %d: %s", resp.StatusCode, string(body))
		}
		var parsed map[string]any
		if err := json.Unmarshal(body, &parsed); err != nil {
			return nil, err
		}
		return parsed, nil
	}

	parsed, err := callVerify("https://buy.itunes.apple.com/verifyReceipt")
	if err != nil {
		return err
	}
	statusAny, ok := parsed["status"]
	if !ok {
		return errors.New("apple verify response missing status")
	}
	status := int(statusAny.(float64))
	if status == 21007 {
		parsed, err = callVerify("https://sandbox.itunes.apple.com/verifyReceipt")
		if err != nil {
			return err
		}
		status = int(parsed["status"].(float64))
	}
	if status != 0 {
		return fmt.Errorf("apple verifyReceipt failed with status=%d", status)
	}

	nowMs := time.Now().UTC().UnixMilli()
	productMatched := false
	activeSubscription := false

	if latestAny, ok := parsed["latest_receipt_info"]; ok {
		if latest, ok := latestAny.([]any); ok {
			for _, itemAny := range latest {
				item, ok := itemAny.(map[string]any)
				if !ok {
					continue
				}
				pid := strings.TrimSpace(fmt.Sprint(item["product_id"]))
				if pid != expectedProductID {
					continue
				}
				productMatched = true
				expMsRaw := strings.TrimSpace(fmt.Sprint(item["expires_date_ms"]))
				if expMsRaw != "" {
					if expMs, err := strconv.ParseInt(expMsRaw, 10, 64); err == nil && expMs > nowMs {
						activeSubscription = true
					}
				}
			}
		}
	}

	if !productMatched {
		// Fallback for non-renewing/non-subscription receipts.
		if receiptAny, ok := parsed["receipt"]; ok {
			if receiptMap, ok := receiptAny.(map[string]any); ok {
				if inAppAny, ok := receiptMap["in_app"]; ok {
					if inApp, ok := inAppAny.([]any); ok {
						for _, itemAny := range inApp {
							item, ok := itemAny.(map[string]any)
							if !ok {
								continue
							}
							pid := strings.TrimSpace(fmt.Sprint(item["product_id"]))
							if pid == expectedProductID {
								productMatched = true
								activeSubscription = true
								break
							}
						}
					}
				}
			}
		}
	}

	if !productMatched {
		return fmt.Errorf("apple receipt product mismatch: expected %s", expectedProductID)
	}
	if !activeSubscription {
		return errors.New("apple subscription is not active")
	}
	return nil
}

func appleBillingVerifyHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		var req appleVerifyRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		deviceID := strings.TrimSpace(req.DeviceId)
		email := strings.TrimSpace(req.Email)
		if deviceID == "" {
			http.Error(w, "deviceId is required", http.StatusBadRequest)
			return
		}
		if email != "" && !strings.EqualFold(email, identity.Email) {
			http.Error(w, "request email must match authenticated account", http.StatusForbidden)
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}
		tier, ok := tierFromProductID(req.ProductId)
		if !ok {
			http.Error(w, "unsupported productId", http.StatusBadRequest)
			return
		}

		if err := verifyAppleReceipt(strings.TrimSpace(req.ReceiptData), strings.TrimSpace(req.ProductId)); err != nil {
			log.Printf("apple purchase verify failed: %v", err)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusPaymentRequired)
			_ = json.NewEncoder(w).Encode(map[string]any{
				"error":   "purchase_verification_failed",
				"message": err.Error(),
			})
			return
		}

		tracker.LinkEmail(deviceID, identity.Email)
		tracker.SetTierByEmail(identity.Email, tier)
		usage := tracker.GetUsage(deviceID)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(usage)
	}
}

func getProfileHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}
		profile, found := tracker.GetProfileByEmail(identity.Email)
		if !found {
			profile = StudentProfile{Email: identity.Email}
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(profile)
	}
}

func getBillingStatusHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}

		status, found := tracker.GetBillingStatusByEmail(identity.Email)
		if !found {
			status = BillingStatus{
				Email:           identity.Email,
				Tier:            TierFree,
				LinkedDeviceIds: []string{},
			}
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(status)
	}
}

func saveProfileHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		identity, ok := authenticatedIdentityFromRequest(w, r)
		if !ok {
			return
		}

		var req saveProfileRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(req.Email) != "" && !strings.EqualFold(strings.TrimSpace(req.Email), identity.Email) {
			http.Error(w, "request email must match authenticated account", http.StatusForbidden)
			return
		}
		if err := tracker.BindOrValidateAccountOwner(identity.Email, identity.FirebaseUID); err != nil {
			http.Error(w, "account ownership validation failed", http.StatusForbidden)
			return
		}

		saved, err := tracker.SaveProfileByEmail(identity.Email, StudentProfile{
			StudentName: strings.TrimSpace(req.StudentName),
			Email:       identity.Email,
			PhoneNumber: strings.TrimSpace(req.PhoneNumber),
			ClassLevel:  strings.TrimSpace(req.ClassLevel),
		})
		if err != nil {
			http.Error(w, "could not save profile", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(saved)
	}
}

func requireBearerFromEnv(w http.ResponseWriter, r *http.Request, envKey string) bool {
	expected := strings.TrimSpace(os.Getenv(envKey))
	if expected == "" {
		http.Error(w, envKey+" is not configured", http.StatusServiceUnavailable)
		return false
	}
	authz := strings.TrimSpace(r.Header.Get("Authorization"))
	if authz != "Bearer "+expected {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return false
	}
	return true
}

func googleRTDNHandler(tracker *UsageTracker) http.HandlerFunc {
	type pushEnvelope struct {
		Message struct {
			Data string `json:"data"`
		} `json:"message"`
	}
	type rtdnMessage struct {
		PackageName              string `json:"packageName"`
		SubscriptionNotification struct {
			NotificationType int    `json:"notificationType"`
			PurchaseToken    string `json:"purchaseToken"`
			SubscriptionID   string `json:"subscriptionId"`
		} `json:"subscriptionNotification"`
	}

	return func(w http.ResponseWriter, r *http.Request) {
		if !requireBearerFromEnv(w, r, "GOOGLE_RTDN_BEARER_TOKEN") {
			return
		}
		var env pushEnvelope
		if err := json.NewDecoder(r.Body).Decode(&env); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		if strings.TrimSpace(env.Message.Data) == "" {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{"status":"ignored","reason":"empty_message"}`))
			return
		}
		raw, err := base64.StdEncoding.DecodeString(env.Message.Data)
		if err != nil {
			http.Error(w, "invalid pubsub message data", http.StatusBadRequest)
			return
		}
		var msg rtdnMessage
		if err := json.Unmarshal(raw, &msg); err != nil {
			http.Error(w, "invalid rtdn payload", http.StatusBadRequest)
			return
		}

		purchaseToken := strings.TrimSpace(msg.SubscriptionNotification.PurchaseToken)
		productID := strings.TrimSpace(msg.SubscriptionNotification.SubscriptionID)
		if purchaseToken == "" || productID == "" {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{"status":"ignored","reason":"missing_subscription_payload"}`))
			return
		}

		ent, found := tracker.GetGoogleEntitlementByToken(purchaseToken)
		packageName := strings.TrimSpace(msg.PackageName)
		if packageName == "" {
			packageName = strings.TrimSpace(ent.PackageName)
		}
		if packageName == "" {
			packageName = strings.TrimSpace(os.Getenv("GOOGLE_PLAY_PACKAGE_NAME"))
		}
		if packageName == "" {
			packageName = "com.smartstudybuddy.app"
		}

		if !found || strings.TrimSpace(ent.Email) == "" {
			log.Printf("RTDN token not mapped yet: product=%s token=%s", productID, purchaseToken)
			w.WriteHeader(http.StatusAccepted)
			_, _ = w.Write([]byte(`{"status":"accepted","reason":"token_not_mapped"}`))
			return
		}

		if err := verifyGoogleSubscription(r.Context(), packageName, purchaseToken, productID); err != nil {
			log.Printf("RTDN subscription inactive: %v", err)
			tracker.SaveGoogleEntitlement(ent.Email, productID, purchaseToken, packageName, "inactive", err.Error())
			_ = tracker.SetTierByEmail(ent.Email, TierFree)
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{"status":"processed","result":"downgraded"}`))
			return
		}

		tier, ok := tierFromProductID(productID)
		if ok {
			_ = tracker.SetTierByEmail(ent.Email, tier)
		}
		tracker.SaveGoogleEntitlement(ent.Email, productID, purchaseToken, packageName, "active", "")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"processed","result":"active"}`))
	}
}

func appleServerNotificationHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !requireBearerFromEnv(w, r, "APPLE_SERVER_NOTIFICATIONS_BEARER_TOKEN") {
			return
		}
		var payload map[string]any
		if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
			http.Error(w, "invalid request body", http.StatusBadRequest)
			return
		}
		notificationType := strings.TrimSpace(fmt.Sprint(payload["notificationType"]))
		subtype := strings.TrimSpace(fmt.Sprint(payload["subtype"]))
		log.Printf("apple notification received: type=%s subtype=%s", notificationType, subtype)
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"processed"}`))
	}
}

func reconcileGoogleEntitlementsHandler(tracker *UsageTracker) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !requireBearerFromEnv(w, r, "RECONCILE_BEARER_TOKEN") {
			return
		}
		activeDocs, err := tracker.ListGoogleEntitlementsByStatus("active")
		if err != nil {
			http.Error(w, "failed to load entitlements", http.StatusInternalServerError)
			return
		}
		type reconcileResult struct {
			Checked     int `json:"checked"`
			StillActive int `json:"stillActive"`
			Downgraded  int `json:"downgraded"`
		}
		result := reconcileResult{}
		for _, doc := range activeDocs {
			result.Checked++
			packageName := strings.TrimSpace(doc.PackageName)
			if packageName == "" {
				packageName = strings.TrimSpace(os.Getenv("GOOGLE_PLAY_PACKAGE_NAME"))
			}
			if packageName == "" {
				packageName = "com.smartstudybuddy.app"
			}
			err := verifyGoogleSubscription(r.Context(), packageName, doc.PurchaseToken, doc.ProductID)
			if err != nil {
				tracker.SaveGoogleEntitlement(doc.Email, doc.ProductID, doc.PurchaseToken, packageName, "inactive", err.Error())
				_ = tracker.SetTierByEmail(doc.Email, TierFree)
				result.Downgraded++
				continue
			}
			tier, ok := tierFromProductID(doc.ProductID)
			if ok {
				_ = tracker.SetTierByEmail(doc.Email, tier)
			}
			tracker.SaveGoogleEntitlement(doc.Email, doc.ProductID, doc.PurchaseToken, packageName, "active", "")
			result.StillActive++
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(result)
	}
}

func main() {
	content := loadContentStore()
	contentDir := resolveContentDir()
	indexPath := resolveIndexPath(contentDir)
	indexPath = downloadIndexFromGCSIfConfigured(indexPath)
	retriever := LoadRetriever(indexPath)
	gemini := NewGeminiClientFromEnv(content, retriever)
	tracker := NewUsageTracker()
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", healthHandler)
	mux.HandleFunc("GET /", healthHandler)
	mux.HandleFunc("GET /usage", usageHandler(tracker))
	mux.HandleFunc("POST /tutor", tutorHandler(gemini, tracker))
	mux.HandleFunc("POST /auth/link", linkEmailHandler(tracker))
	mux.HandleFunc("POST /auth/restore", restoreHandler(tracker))
	mux.HandleFunc("GET /profile", getProfileHandler(tracker))
	mux.HandleFunc("POST /profile", saveProfileHandler(tracker))
	mux.HandleFunc("GET /billing/status", getBillingStatusHandler(tracker))
	mux.HandleFunc("POST /tier", tierHandler(tracker))
	mux.HandleFunc("POST /billing/google/verify", googleBillingVerifyHandler(tracker))
	mux.HandleFunc("POST /billing/apple/verify", appleBillingVerifyHandler(tracker))
	mux.HandleFunc("POST /billing/google/rtdn", googleRTDNHandler(tracker))
	mux.HandleFunc("POST /billing/apple/notifications", appleServerNotificationHandler())
	mux.HandleFunc("POST /billing/reconcile/google", reconcileGoogleEntitlementsHandler(tracker))

	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}

	log.Printf("tutor-go service listening on :%s", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatal(err)
	}
}

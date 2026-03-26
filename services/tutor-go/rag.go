package main

import (
	"bufio"
	"encoding/json"
	"log"
	"math"
	"os"
	"sort"
	"strings"
)

type VectorChunk struct {
	ID         string    `json:"id"`
	SourceFile string    `json:"source_file"`
	Subject    string    `json:"subject,omitempty"`
	Page       int       `json:"page"`
	ChunkIndex int       `json:"chunk_index"`
	Text       string    `json:"text"`
	Embedding  []float64 `json:"embedding"`
}

type SearchHit struct {
	Chunk VectorChunk
	Score float64
}

type Retriever struct {
	chunks []VectorChunk
}

func LoadRetriever(path string) *Retriever {
	file, err := os.Open(path)
	if err != nil {
		log.Printf("RAG disabled: unable to open index file %s: %v", path, err)
		return nil
	}
	defer file.Close()

	var chunks []VectorChunk
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		var c VectorChunk
		if err := json.Unmarshal([]byte(line), &c); err != nil {
			continue
		}
		if len(c.Embedding) == 0 || c.Text == "" {
			continue
		}
		chunks = append(chunks, c)
	}
	if err := scanner.Err(); err != nil {
		log.Printf("RAG index read error: %v", err)
		return nil
	}
	if len(chunks) == 0 {
		log.Printf("RAG disabled: empty index at %s", path)
		return nil
	}
	log.Printf("RAG retriever loaded: %d chunks from %s", len(chunks), path)
	return &Retriever{chunks: chunks}
}

func CosineSimilarity(a, b []float64) float64 {
	if len(a) == 0 || len(b) == 0 || len(a) != len(b) {
		return 0
	}
	var dot, na, nb float64
	for i := range a {
		dot += a[i] * b[i]
		na += a[i] * a[i]
		nb += b[i] * b[i]
	}
	if na == 0 || nb == 0 {
		return 0
	}
	return dot / (math.Sqrt(na) * math.Sqrt(nb))
}

func normalizeRAGSubject(s string) string {
	return strings.ToLower(strings.TrimSpace(s))
}

// filterChunksBySubject returns chunks matching subjectHint: same subject (case-insensitive) or
// unspecified subject on the chunk (legacy rows). If nothing matches, returns nil so the caller
// can fall back to the full index.
func filterChunksBySubject(chunks []VectorChunk, subjectHint string) []VectorChunk {
	sub := normalizeRAGSubject(subjectHint)
	if sub == "" {
		return nil
	}
	var out []VectorChunk
	for _, c := range chunks {
		cs := normalizeRAGSubject(c.Subject)
		if cs == "" || cs == sub {
			out = append(out, c)
		}
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

func (r *Retriever) Search(queryEmbedding []float64, topK int, subjectHint string) []SearchHit {
	if r == nil || len(r.chunks) == 0 || len(queryEmbedding) == 0 {
		return nil
	}
	if topK <= 0 {
		topK = 3
	}

	corpus := r.chunks
	if narrowed := filterChunksBySubject(r.chunks, subjectHint); narrowed != nil {
		corpus = narrowed
	}

	hits := make([]SearchHit, 0, len(corpus))
	for _, c := range corpus {
		score := CosineSimilarity(queryEmbedding, c.Embedding)
		hits = append(hits, SearchHit{Chunk: c, Score: score})
	}

	sort.Slice(hits, func(i, j int) bool {
		return hits[i].Score > hits[j].Score
	})

	if len(hits) > topK {
		return hits[:topK]
	}
	return hits
}

package main

import (
	"sync"
	"time"
)

type Tier string

const (
	TierFree     Tier = "free"
	TierPro      Tier = "pro"
	TierUltimate Tier = "ultimate"
)

const (
	FreeDailyLimit     = 10
	ProDailyLimit      = -1 // unlimited
	UltimateDailyLimit = -1
)

type UsageInfo struct {
	Used     int    `json:"used"`
	Limit    int    `json:"limit"`
	Tier     Tier   `json:"tier"`
	ResetsAt string `json:"resetsAt"`
}

type deviceRecord struct {
	tier  Tier
	count int
	date  string // YYYY-MM-DD
}

type UsageTracker struct {
	mu      sync.RWMutex
	devices map[string]*deviceRecord
}

func NewUsageTracker() *UsageTracker {
	return &UsageTracker{
		devices: make(map[string]*deviceRecord),
	}
}

func todayUTC() string {
	return time.Now().UTC().Format("2006-01-02")
}

func tomorrowMidnightUTC() string {
	now := time.Now().UTC()
	tomorrow := time.Date(now.Year(), now.Month(), now.Day()+1, 0, 0, 0, 0, time.UTC)
	return tomorrow.Format(time.RFC3339)
}

func limitForTier(tier Tier) int {
	switch tier {
	case TierPro, TierUltimate:
		return -1
	default:
		return FreeDailyLimit
	}
}

func (u *UsageTracker) getOrCreate(deviceId string) *deviceRecord {
	today := todayUTC()
	rec, ok := u.devices[deviceId]
	if !ok {
		rec = &deviceRecord{tier: TierFree, count: 0, date: today}
		u.devices[deviceId] = rec
		return rec
	}
	if rec.date != today {
		rec.count = 0
		rec.date = today
	}
	return rec
}

func (u *UsageTracker) GetUsage(deviceId string) UsageInfo {
	u.mu.RLock()
	defer u.mu.RUnlock()
	rec := u.getOrCreateReadOnly(deviceId)
	limit := limitForTier(rec.tier)
	return UsageInfo{
		Used:     rec.count,
		Limit:    limit,
		Tier:     rec.tier,
		ResetsAt: tomorrowMidnightUTC(),
	}
}

func (u *UsageTracker) getOrCreateReadOnly(deviceId string) deviceRecord {
	today := todayUTC()
	rec, ok := u.devices[deviceId]
	if !ok {
		return deviceRecord{tier: TierFree, count: 0, date: today}
	}
	if rec.date != today {
		return deviceRecord{tier: rec.tier, count: 0, date: today}
	}
	return *rec
}

// CheckAndIncrement returns the usage info. allowed=false if quota exceeded.
func (u *UsageTracker) CheckAndIncrement(deviceId string) (UsageInfo, bool) {
	u.mu.Lock()
	defer u.mu.Unlock()

	rec := u.getOrCreate(deviceId)
	limit := limitForTier(rec.tier)

	if limit > 0 && rec.count >= limit {
		return UsageInfo{
			Used:     rec.count,
			Limit:    limit,
			Tier:     rec.tier,
			ResetsAt: tomorrowMidnightUTC(),
		}, false
	}

	rec.count++
	return UsageInfo{
		Used:     rec.count,
		Limit:    limit,
		Tier:     rec.tier,
		ResetsAt: tomorrowMidnightUTC(),
	}, true
}

func (u *UsageTracker) SetTier(deviceId string, tier Tier) {
	u.mu.Lock()
	defer u.mu.Unlock()
	rec := u.getOrCreate(deviceId)
	rec.tier = tier
}

// StripForFreeTier removes premium fields from the response for free-tier users.
func StripForFreeTier(res TutorResponse) TutorResponse {
	res.Options = nil
	res.CorrectOption = ""
	res.Difficulty = ""
	res.NcertReference = ""
	res.RevisionCard = RevisionCard{
		Concept: res.RevisionCard.Concept,
	}
	return res
}

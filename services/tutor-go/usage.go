package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"log"
	"os"
	"time"

	"cloud.google.com/go/firestore"
	"google.golang.org/api/iterator"
)

type Tier string

const (
	TierFree     Tier = "free"
	TierPro      Tier = "pro"
	TierUltimate Tier = "ultimate"
)

const (
	FreeDailyLimit     = 10
	ProDailyLimit      = -1
	UltimateDailyLimit = -1
)

type UsageInfo struct {
	Used     int    `json:"used"`
	Limit    int    `json:"limit"`
	Tier     Tier   `json:"tier"`
	Email    string `json:"email,omitempty"`
	ResetsAt string `json:"resetsAt"`
}

type StudentProfile struct {
	StudentName string `json:"studentName"`
	Email       string `json:"email"`
	PhoneNumber string `json:"phoneNumber"`
	ClassLevel  string `json:"classLevel"`
}

// Firestore document schema for devices/{deviceId}
type deviceDoc struct {
	Tier       string `firestore:"tier"`
	Email      string `firestore:"email"`
	DailyCount int    `firestore:"dailyCount"`
	CountDate  string `firestore:"countDate"`
	CreatedAt  string `firestore:"createdAt"`
}

// Firestore document schema for accounts/{email}
type accountDoc struct {
	Email       string   `firestore:"email"`
	Tier        string   `firestore:"tier"`
	DeviceIds   []string `firestore:"deviceIds"`
	LinkedAt    string   `firestore:"linkedAt"`
	FirebaseUID string   `firestore:"firebaseUid"`
	StudentName string   `firestore:"studentName"`
	PhoneNumber string   `firestore:"phoneNumber"`
	ClassLevel  string   `firestore:"classLevel"`
}

type googleEntitlementDoc struct {
	Email          string `firestore:"email"`
	ProductID      string `firestore:"productId"`
	PurchaseToken  string `firestore:"purchaseToken"`
	PackageName    string `firestore:"packageName"`
	Status         string `firestore:"status"`
	LastError      string `firestore:"lastError"`
	LastVerifiedAt string `firestore:"lastVerifiedAt"`
	CreatedAt      string `firestore:"createdAt"`
}

type UsageTracker struct {
	fs *firestore.Client
}

func NewUsageTracker() *UsageTracker {
	projectID := os.Getenv("GCP_PROJECT")
	if projectID == "" {
		projectID = os.Getenv("GOOGLE_CLOUD_PROJECT")
	}
	if projectID == "" {
		projectID = "smart-study-buddy-490413"
	}

	ctx := context.Background()
	client, err := firestore.NewClient(ctx, projectID)
	if err != nil {
		log.Fatalf("Failed to create Firestore client: %v", err)
	}
	log.Printf("Firestore connected (project: %s)", projectID)
	return &UsageTracker{fs: client}
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

func (u *UsageTracker) deviceRef(deviceId string) *firestore.DocumentRef {
	return u.fs.Collection("devices").Doc(deviceId)
}

func (u *UsageTracker) accountRef(email string) *firestore.DocumentRef {
	return u.fs.Collection("accounts").Doc(email)
}

func googleEntitlementDocID(purchaseToken string) string {
	sum := sha256.Sum256([]byte(purchaseToken))
	return hex.EncodeToString(sum[:])
}

func (u *UsageTracker) googleEntitlementRef(purchaseToken string) *firestore.DocumentRef {
	return u.fs.Collection("entitlements_google").Doc(googleEntitlementDocID(purchaseToken))
}

func (u *UsageTracker) getDevice(ctx context.Context, deviceId string) deviceDoc {
	snap, err := u.deviceRef(deviceId).Get(ctx)
	if err != nil {
		return deviceDoc{Tier: string(TierFree), CountDate: todayUTC()}
	}
	var doc deviceDoc
	if err := snap.DataTo(&doc); err != nil {
		return deviceDoc{Tier: string(TierFree), CountDate: todayUTC()}
	}
	if doc.CountDate != todayUTC() {
		doc.DailyCount = 0
		doc.CountDate = todayUTC()
	}
	return doc
}

// Resolve the effective tier: account tier takes priority over device tier.
func (u *UsageTracker) resolveTier(ctx context.Context, dev deviceDoc) Tier {
	if dev.Email != "" {
		snap, err := u.accountRef(dev.Email).Get(ctx)
		if err == nil {
			var acct accountDoc
			if err := snap.DataTo(&acct); err == nil && acct.Tier != "" {
				return Tier(acct.Tier)
			}
		}
	}
	if dev.Tier == "" {
		return TierFree
	}
	return Tier(dev.Tier)
}

func (u *UsageTracker) GetUsage(deviceId string) UsageInfo {
	ctx := context.Background()
	dev := u.getDevice(ctx, deviceId)
	tier := u.resolveTier(ctx, dev)
	return UsageInfo{
		Used:     dev.DailyCount,
		Limit:    limitForTier(tier),
		Tier:     tier,
		Email:    dev.Email,
		ResetsAt: tomorrowMidnightUTC(),
	}
}

func (u *UsageTracker) CheckAndIncrement(deviceId string) (UsageInfo, bool) {
	ctx := context.Background()
	dev := u.getDevice(ctx, deviceId)
	tier := u.resolveTier(ctx, dev)
	limit := limitForTier(tier)

	if limit > 0 && dev.DailyCount >= limit {
		return UsageInfo{
			Used: dev.DailyCount, Limit: limit, Tier: tier,
			Email: dev.Email, ResetsAt: tomorrowMidnightUTC(),
		}, false
	}

	dev.DailyCount++
	dev.CountDate = todayUTC()
	if dev.Tier == "" {
		dev.Tier = string(TierFree)
	}
	if dev.CreatedAt == "" {
		dev.CreatedAt = time.Now().UTC().Format(time.RFC3339)
	}

	if _, err := u.deviceRef(deviceId).Set(ctx, dev); err != nil {
		log.Printf("Firestore write error (device %s): %v", deviceId, err)
	}

	return UsageInfo{
		Used: dev.DailyCount, Limit: limit, Tier: tier,
		Email: dev.Email, ResetsAt: tomorrowMidnightUTC(),
	}, true
}

func (u *UsageTracker) SetTier(deviceId string, tier Tier) {
	ctx := context.Background()
	dev := u.getDevice(ctx, deviceId)
	dev.Tier = string(tier)
	if dev.CreatedAt == "" {
		dev.CreatedAt = time.Now().UTC().Format(time.RFC3339)
	}
	if _, err := u.deviceRef(deviceId).Set(ctx, dev); err != nil {
		log.Printf("Firestore write error (SetTier): %v", err)
	}
	if dev.Email != "" {
		u.SetTierByEmail(dev.Email, tier)
	}
}

func (u *UsageTracker) LinkEmail(deviceId, email string) UsageInfo {
	ctx := context.Background()
	now := time.Now().UTC().Format(time.RFC3339)

	dev := u.getDevice(ctx, deviceId)
	dev.Email = email
	if dev.Tier == "" {
		dev.Tier = string(TierFree)
	}
	if dev.CreatedAt == "" {
		dev.CreatedAt = now
	}
	if _, err := u.deviceRef(deviceId).Set(ctx, dev); err != nil {
		log.Printf("Firestore write error (LinkEmail device): %v", err)
	}

	snap, err := u.accountRef(email).Get(ctx)
	if err != nil {
		acct := accountDoc{
			Email:     email,
			Tier:      dev.Tier,
			DeviceIds: []string{deviceId},
			LinkedAt:  now,
		}
		if _, err := u.accountRef(email).Set(ctx, acct); err != nil {
			log.Printf("Firestore write error (LinkEmail account create): %v", err)
		}
	} else {
		var acct accountDoc
		if err := snap.DataTo(&acct); err == nil {
			found := false
			for _, id := range acct.DeviceIds {
				if id == deviceId {
					found = true
					break
				}
			}
			if !found {
				acct.DeviceIds = append(acct.DeviceIds, deviceId)
				if _, err := u.accountRef(email).Set(ctx, acct); err != nil {
					log.Printf("Firestore write error (LinkEmail account update): %v", err)
				}
			}
		}
	}

	tier := u.resolveTier(ctx, dev)
	return UsageInfo{
		Used: dev.DailyCount, Limit: limitForTier(tier), Tier: tier,
		Email: email, ResetsAt: tomorrowMidnightUTC(),
	}
}

func (u *UsageTracker) RestoreByEmail(deviceId, email string) (UsageInfo, bool) {
	ctx := context.Background()
	now := time.Now().UTC().Format(time.RFC3339)

	snap, err := u.accountRef(email).Get(ctx)
	if err != nil {
		return UsageInfo{
			Used: 0, Limit: FreeDailyLimit, Tier: TierFree,
			ResetsAt: tomorrowMidnightUTC(),
		}, false
	}

	var acct accountDoc
	if err := snap.DataTo(&acct); err != nil {
		return UsageInfo{
			Used: 0, Limit: FreeDailyLimit, Tier: TierFree,
			ResetsAt: tomorrowMidnightUTC(),
		}, false
	}

	dev := u.getDevice(ctx, deviceId)
	dev.Email = email
	dev.Tier = acct.Tier
	if dev.CreatedAt == "" {
		dev.CreatedAt = now
	}
	if _, err := u.deviceRef(deviceId).Set(ctx, dev); err != nil {
		log.Printf("Firestore write error (Restore device): %v", err)
	}

	found := false
	for _, id := range acct.DeviceIds {
		if id == deviceId {
			found = true
			break
		}
	}
	if !found {
		acct.DeviceIds = append(acct.DeviceIds, deviceId)
		if _, err := u.accountRef(email).Set(ctx, acct); err != nil {
			log.Printf("Firestore write error (Restore account): %v", err)
		}
	}

	tier := Tier(acct.Tier)
	return UsageInfo{
		Used: dev.DailyCount, Limit: limitForTier(tier), Tier: tier,
		Email: email, ResetsAt: tomorrowMidnightUTC(),
	}, true
}

func (u *UsageTracker) SetTierByEmail(email string, tier Tier) bool {
	ctx := context.Background()
	snap, err := u.accountRef(email).Get(ctx)
	if err != nil {
		return false
	}
	var acct accountDoc
	if err := snap.DataTo(&acct); err != nil {
		return false
	}
	acct.Tier = string(tier)
	if _, err := u.accountRef(email).Set(ctx, acct); err != nil {
		log.Printf("Firestore write error (SetTierByEmail): %v", err)
		return false
	}

	for _, devId := range acct.DeviceIds {
		dev := u.getDevice(ctx, devId)
		dev.Tier = string(tier)
		if _, err := u.deviceRef(devId).Set(ctx, dev); err != nil {
			log.Printf("Firestore write error (SetTierByEmail device %s): %v", devId, err)
		}
	}
	return true
}

func (u *UsageTracker) BindOrValidateAccountOwner(email, firebaseUID string) error {
	ctx := context.Background()
	now := time.Now().UTC().Format(time.RFC3339)

	snap, err := u.accountRef(email).Get(ctx)
	if err != nil {
		acct := accountDoc{
			Email:       email,
			Tier:        string(TierFree),
			DeviceIds:   []string{},
			LinkedAt:    now,
			FirebaseUID: firebaseUID,
		}
		_, setErr := u.accountRef(email).Set(ctx, acct)
		return setErr
	}

	var acct accountDoc
	if err := snap.DataTo(&acct); err != nil {
		return err
	}
	if acct.FirebaseUID == "" {
		acct.FirebaseUID = firebaseUID
		_, setErr := u.accountRef(email).Set(ctx, acct)
		return setErr
	}
	if acct.FirebaseUID != firebaseUID {
		return errors.New("account ownership mismatch")
	}
	return nil
}

func (u *UsageTracker) SaveGoogleEntitlement(email, productID, purchaseToken, packageName, status, lastErr string) {
	ctx := context.Background()
	now := time.Now().UTC().Format(time.RFC3339)
	ref := u.googleEntitlementRef(purchaseToken)
	doc := googleEntitlementDoc{
		Email:          email,
		ProductID:      productID,
		PurchaseToken:  purchaseToken,
		PackageName:    packageName,
		Status:         status,
		LastError:      lastErr,
		LastVerifiedAt: now,
		CreatedAt:      now,
	}
	if snap, err := ref.Get(ctx); err == nil {
		var existing googleEntitlementDoc
		if err := snap.DataTo(&existing); err == nil && existing.CreatedAt != "" {
			doc.CreatedAt = existing.CreatedAt
		}
	}
	if _, err := ref.Set(ctx, doc); err != nil {
		log.Printf("Firestore write error (SaveGoogleEntitlement): %v", err)
	}
}

func (u *UsageTracker) GetGoogleEntitlementByToken(purchaseToken string) (googleEntitlementDoc, bool) {
	ctx := context.Background()
	snap, err := u.googleEntitlementRef(purchaseToken).Get(ctx)
	if err != nil {
		return googleEntitlementDoc{}, false
	}
	var doc googleEntitlementDoc
	if err := snap.DataTo(&doc); err != nil {
		return googleEntitlementDoc{}, false
	}
	return doc, true
}

func (u *UsageTracker) ListGoogleEntitlementsByStatus(status string) ([]googleEntitlementDoc, error) {
	ctx := context.Background()
	iter := u.fs.Collection("entitlements_google").
		Where("status", "==", status).
		Documents(ctx)
	defer iter.Stop()

	var items []googleEntitlementDoc
	for {
		snap, err := iter.Next()
		if err == iterator.Done {
			break
		}
		if err != nil {
			return nil, err
		}
		var doc googleEntitlementDoc
		if err := snap.DataTo(&doc); err != nil {
			continue
		}
		items = append(items, doc)
	}
	return items, nil
}

func (u *UsageTracker) GetProfileByEmail(email string) (StudentProfile, bool) {
	ctx := context.Background()
	snap, err := u.accountRef(email).Get(ctx)
	if err != nil {
		return StudentProfile{}, false
	}
	var acct accountDoc
	if err := snap.DataTo(&acct); err != nil {
		return StudentProfile{}, false
	}
	return StudentProfile{
		StudentName: acct.StudentName,
		Email:       acct.Email,
		PhoneNumber: acct.PhoneNumber,
		ClassLevel:  acct.ClassLevel,
	}, true
}

func (u *UsageTracker) SaveProfileByEmail(email string, profile StudentProfile) (StudentProfile, error) {
	ctx := context.Background()
	snap, err := u.accountRef(email).Get(ctx)
	var acct accountDoc
	if err != nil {
		acct = accountDoc{
			Email:     email,
			Tier:      string(TierFree),
			DeviceIds: []string{},
			LinkedAt:  time.Now().UTC().Format(time.RFC3339),
		}
	} else if err := snap.DataTo(&acct); err != nil {
		return StudentProfile{}, err
	}

	acct.StudentName = profile.StudentName
	acct.PhoneNumber = profile.PhoneNumber
	acct.ClassLevel = profile.ClassLevel
	acct.Email = email

	if _, err := u.accountRef(email).Set(ctx, acct); err != nil {
		return StudentProfile{}, err
	}

	return StudentProfile{
		StudentName: acct.StudentName,
		Email:       acct.Email,
		PhoneNumber: acct.PhoneNumber,
		ClassLevel:  acct.ClassLevel,
	}, nil
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

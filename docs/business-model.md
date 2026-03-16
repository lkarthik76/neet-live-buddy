# NEET Live Buddy — Business Model & Tier System

## Tier Overview

| Feature | Free | Pro (Rs 99/mo) | Ultimate (Rs 299/mo) | Institutional (Rs 50/student/mo) |
|---|:---:|:---:|:---:|:---:|
| Questions per day | 10 | Unlimited | Unlimited | Unlimited |
| Languages (EN/HI/TA) | All | All | All | All |
| Camera scan | Yes | Yes | Yes | Yes |
| Voice input | Yes | Yes | Yes | Yes |
| Basic answer | Yes | Yes | Yes | Yes |
| Option-by-option analysis | No | Yes | Yes | Yes |
| Revision cards | Summary only | Full | Full | Full |
| NCERT reference | No | Yes | Yes | Yes |
| Difficulty badge | No | Yes | Yes | Yes |
| Doubt history | No | Yes (30 days) | Yes (unlimited) | Yes |
| Offline cached answers | No | No | Yes | Yes |
| AI practice tests | No | No | Yes | No |
| Performance analytics | No | No | Yes | Yes |
| Teacher dashboard | No | No | No | Yes |
| Ads | Between answers | None | None | None |
| Gemini model | 2.5 Flash | 2.5 Pro | 2.5 Pro | 2.5 Pro |

## Technical Implementation

### Device Identity (No Login Required)
- Generate UUID on first app launch
- Store in Android SharedPreferences / iOS UserDefaults
- Send as `deviceId` in every API request
- Future: Firebase Auth for cross-device sync

### Usage Tracking (Server-side)
- Backend maintains in-memory map: `deviceId -> {count, date, tier}`
- Resets daily at midnight UTC
- Returns usage info in every response
- Future: Firestore for persistence across cold starts

### Tier Enforcement Flow
```
Client sends: { deviceId, tier, prompt, ... }
                    |
                    v
Backend checks usage for deviceId
                    |
        +-----------+-----------+
        |                       |
   Under limit              Over limit
        |                       |
        v                       v
   Call Gemini            Return 429 +
        |                 "upgrade" message
        v
   Filter response
   based on tier
        |
        v
   Return response +
   usage info
```

### Response Filtering by Tier

**Free tier response:**
```json
{
  "answer": "...(basic explanation)...",
  "chapter": "Cell Division",
  "revisionCard": { "concept": "..." },
  "usage": { "used": 7, "limit": 10, "tier": "free" }
}
```

**Pro/Ultimate tier response:**
```json
{
  "answer": "...(detailed NEET answer-key)...",
  "chapter": "Cell Division",
  "correctOption": "B",
  "options": [...full analysis...],
  "difficulty": "Medium",
  "ncertReference": "NCERT Class 11, Ch 10, Sec 10.2",
  "revisionCard": { ...full card... },
  "usage": { "used": 47, "limit": -1, "tier": "pro" }
}
```

### API Changes

**Request** — add `deviceId`:
```json
POST /tutor
{
  "deviceId": "uuid-...",
  "prompt": "What is mitosis?",
  "language": "tamil",
  "subjectHint": "biology"
}
```

**New endpoint** — check usage:
```json
GET /usage?deviceId=uuid-...
Response: { "used": 7, "limit": 10, "tier": "free", "resetsAt": "2026-03-17T00:00:00Z" }
```

### Payment Integration (Phase 2)
- Google Play Billing Library for Android subscriptions
- Product IDs: `neet_pro_monthly`, `neet_ultimate_monthly`
- On purchase: call backend to upgrade tier
- Backend verifies purchase receipt with Google Play API

### Revenue Projections

| Scale | Free Users | Pro (2-5%) | Ultimate (0.5-1%) | Monthly Revenue |
|---|---|---|---|---|
| Early (3 mo) | 5,000 | 100 | 20 | Rs 15,880 |
| Growth (6 mo) | 25,000 | 750 | 150 | Rs 1,19,100 |
| Scale (12 mo) | 1,00,000 | 4,000 | 800 | Rs 6,35,200 |
| Mature (24 mo) | 5,00,000 | 25,000 | 5,000 | Rs 39,70,000 |

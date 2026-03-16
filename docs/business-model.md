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

### Identity & Account Recovery
- Generate UUID on first app launch (device identity)
- Store in Android SharedPreferences / iOS UserDefaults
- Send as `deviceId` in every API request
- **Email Linking**: Users can link an email to their device account
  - Protects paid subscriptions if device is lost/replaced
  - `POST /auth/link` — links email to current device + tier
  - `POST /auth/restore` — on a new device, enter email to restore tier
- Account record stores: `email -> {tier, deviceIds[]}`
- Multiple devices can share one account (e.g. phone + tablet)
- Future: Firebase Auth / Google Sign-In for seamless login

### Data Storage

| Data | Where | Details |
|---|---|---|
| Device UUID | Client (SharedPreferences / UserDefaults) | Generated once on first launch, persists locally |
| Email address | Firestore `accounts/{email}` | Linked by user, persists permanently |
| Tier (free/pro/ultimate) | Firestore `accounts/{email}` | Account-level, survives device loss |
| Daily usage count | Firestore `devices/{deviceId}` | Resets at midnight UTC |
| Device → Email mapping | Firestore `devices/{deviceId}` | Links device to its account |
| Account → Devices list | Firestore `accounts/{email}` | One email can have multiple devices |
| Payment receipts | Google Play + Backend | Verified via Google Play Developer API |
| Question/answer history | Not stored (Phase 2: Firestore) | Future: per-account doubt history |

**Storage:** All server-side data is persisted in **Firestore** (GCP free tier: 1 GiB, 50K reads/day, 20K writes/day — zero cost at our scale).

```
Firestore collections:
├── accounts/{email}
│   ├── tier: "pro"
│   ├── linkedDevices: ["uuid-1", "uuid-2"]
│   ├── linkedAt: timestamp
│   └── paymentHistory: [...]
├── devices/{deviceId}
│   ├── email: "user@example.com"  (or empty for anonymous)
│   ├── tier: "free"
│   ├── dailyUsage: { count: 7, date: "2026-03-16" }
│   └── createdAt: timestamp
└── history/{email}/doubts/{autoId}     ← Phase 2
    ├── prompt, answer, subject, timestamp
    └── imageUsed: true/false
```

### Usage Tracking (Server-side)
- Firestore `devices/{deviceId}` stores: `{dailyCount, countDate, tier, email, createdAt}`
- Firestore `accounts/{email}` stores: `{tier, deviceIds[], linkedAt}`
- Daily count resets at midnight UTC (checked on read)
- Returns usage info (including linked email) in every `/tutor` response
- Data persists across Cloud Run cold starts, redeployments, and scaling

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

**Check usage:**
```json
GET /usage?deviceId=uuid-...
→ { "used": 7, "limit": 10, "tier": "free", "email": "", "resetsAt": "2026-03-17T00:00:00Z" }
```

**Link email to device** (protects paid subscription):
```json
POST /auth/link
{ "deviceId": "uuid-...", "email": "user@example.com" }
→ { "used": 7, "limit": 10, "tier": "pro", "email": "user@example.com", "resetsAt": "..." }
```

**Restore purchase on new device:**
```json
POST /auth/restore
{ "deviceId": "new-uuid-...", "email": "user@example.com" }
→ { "used": 0, "limit": -1, "tier": "pro", "email": "user@example.com", "resetsAt": "..." }
// Returns 404 if email not found
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

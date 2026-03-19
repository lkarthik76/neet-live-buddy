# Billing Production Runbook

This runbook covers manual production setup that cannot be fully automated in app/backend code.

## 1) Android Play production setup

### Enable required APIs

```bash
gcloud services enable androidpublisher.googleapis.com pubsub.googleapis.com cloudscheduler.googleapis.com secretmanager.googleapis.com
```

### Link Play Console and GCP

1. Open Play Console -> `Setup` -> `API access`.
2. Link your Play Console app to the same GCP project used by backend.
3. Select/Create service account for backend purchase verification.

### Grant service account permissions

In Play Console (`Users and permissions`), grant the backend service account:

- View financial data, orders, cancellation survey responses
- Manage orders and subscriptions
- View app information and download bulk reports

Use least privilege beyond these.

### Create subscriptions

Create and activate products (IDs must match backend):

- `neet_pro_monthly`
- `neet_ultimate_monthly`

Add base plans/offers and activate.

### Internal testing validation

1. Upload release AAB to Internal testing.
2. Add test accounts.
3. Verify end-to-end:
   - purchase succeeds in app
   - backend `/billing/google/verify` returns upgraded tier
   - RTDN webhook updates tier on cancel/renewal events

## 2) iOS App Store production setup

### Create subscriptions

In App Store Connect create:

- `neet_pro_monthly`
- `neet_ultimate_monthly`

### Configure shared secret

Set app-specific shared secret in Secret Manager and deploy backend with:

- `APPLE_SHARED_SECRET_SECRET=<secret-name>`

### Sandbox/TestFlight validation

1. Create sandbox testers.
2. Run TestFlight build on real device.
3. Validate:
   - StoreKit purchase flow
   - backend `/billing/apple/verify` with real receipts
   - server notifications hitting `/billing/apple/notifications`

## 3) Webhooks and reconciliation ops

### Google RTDN (Pub/Sub -> Cloud Run)

1. Create Pub/Sub topic for Play notifications.
2. Configure Play Console RTDN to publish to topic.
3. Create push subscription to:
   - `POST /billing/google/rtdn`
   - header `Authorization: Bearer <GOOGLE_RTDN_BEARER_TOKEN>`

### Reconciliation job (Cloud Scheduler)

Create a scheduled HTTP job to call:

- `POST /billing/reconcile/google`
- header `Authorization: Bearer <RECONCILE_BEARER_TOKEN>`

Recommended: every 6-12 hours.

## 4) Secret Manager setup

Create secrets (example):

```bash
printf '%s' "$GOOGLE_GENAI_API_KEY" | gcloud secrets create google-genai-api-key --data-file=-
printf '%s' "$FIREBASE_WEB_API_KEY" | gcloud secrets create firebase-web-api-key --data-file=-
printf '%s' "$APPLE_SHARED_SECRET" | gcloud secrets create apple-shared-secret --data-file=-
printf '%s' "$GOOGLE_RTDN_BEARER_TOKEN" | gcloud secrets create google-rtdn-bearer-token --data-file=-
printf '%s' "$APPLE_SERVER_NOTIFICATIONS_BEARER_TOKEN" | gcloud secrets create apple-notifications-bearer-token --data-file=-
printf '%s' "$RECONCILE_BEARER_TOKEN" | gcloud secrets create reconcile-bearer-token --data-file=-
```

Grant Cloud Run runtime service account access:

```bash
gcloud secrets add-iam-policy-binding google-genai-api-key \
  --member="serviceAccount:<RUNTIME_SA_EMAIL>" \
  --role="roles/secretmanager.secretAccessor"
```

Repeat for each secret.

## 5) Least-privilege IAM baseline for runtime service account

- `roles/datastore.user` (Firestore read/write only)
- `roles/secretmanager.secretAccessor` (specific secrets only)
- `roles/storage.objectViewer` (if reading index from GCS)
- `roles/androidpublisher` equivalent permission scope via Play Console API access

Avoid broad roles like Editor/Owner.

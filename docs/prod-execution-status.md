# Production Execution Status (Live)

Last updated: 2026-03-19

This file tracks what has been executed automatically vs what still requires manual Store Console/Legal actions.

## Completed automatically

- Enabled required GCP APIs:
  - `run.googleapis.com`
  - `cloudbuild.googleapis.com`
  - `firestore.googleapis.com`
  - `secretmanager.googleapis.com`
  - `androidpublisher.googleapis.com`
  - `pubsub.googleapis.com`
  - `cloudscheduler.googleapis.com`
- Backend health verified: `GET /health` returns `ok`.
- Created dedicated runtime service account:
  - `smart-study-buddy-runtime@smart-study-buddy-490413.iam.gserviceaccount.com`
- Applied runtime IAM baseline:
  - `roles/datastore.user`
  - `roles/storage.objectViewer`
  - secret accessor bindings for required secrets
- Created and wired secrets:
  - `google-genai-api-key`
  - `firebase-web-api-key`
  - `google-rtdn-bearer-token`
  - `apple-notifications-bearer-token`
  - `reconcile-bearer-token`
- Cloud Run updated to:
  - use runtime SA
  - use Secret Manager env refs
  - enforce `ALLOW_TIER_ENDPOINT=false`
- Reconciliation pipeline wired:
  - Cloud Scheduler job: `smart-study-buddy-reconcile-google` (every 6h)
  - Endpoint test passed: `POST /billing/reconcile/google`
- RTDN pipeline wired:
  - Topic: `smart-study-buddy-play-rtdn`
  - Push subscription: `smart-study-buddy-play-rtdn-push`
  - Backend receives RTDN messages (currently returns `202 token_not_mapped` until real tokens exist)
- Build verification:
  - Android release AAB generated: `kmp-app/composeApp/build/outputs/bundle/release/composeApp-release.aab`
  - iOS arm64 compile successful

## In progress / requires manual console actions

## iOS (App Store Connect)

- Create subscriptions:
  - `neet_pro_monthly`
  - `neet_ultimate_monthly`
- Provide app-specific `APPLE_SHARED_SECRET` and store in Secret Manager.
- Run TestFlight/sandbox purchase and validate:
  - `POST /billing/apple/verify`
  - restore flow on new install/device

## Android (Play Console)

- Link Play Console app to this GCP project in API Access.
- Grant Play API permissions to backend service account.
- Create/activate base plans for:
  - `neet_pro_monthly`
  - `neet_ultimate_monthly`
- Upload generated AAB to Internal Testing and validate purchase/restore.

## Observability

- Configure monitoring notification channels (email/Slack/PagerDuty).
- Create alert policies using log metrics:
  - `purchase_verification_failed_count`
  - `rtdn_unauthorized_count`
  - `reconcile_downgraded_count`
- Helper script added to speed setup after channel creation:
  - `infra/setup-monitoring-alerts.sh`
- Build Firebase Analytics conversion dashboard for upgrade funnel.

## Compliance / release governance

- Finalize:
  - Privacy policy updates
  - Terms and refund policy
  - Play Data Safety form
  - App Store privacy labels
- Document key backup and run final smoke matrix.

## Post-manual validation commands

```bash
# Health
curl -sS https://smart-study-buddy-go-tutor-567474220033.asia-south1.run.app/health

# Reconcile endpoint auth check
RECON_TOKEN="$(gcloud secrets versions access latest --secret=reconcile-bearer-token)"
curl -sS -X POST \
  "https://smart-study-buddy-go-tutor-567474220033.asia-south1.run.app/billing/reconcile/google" \
  -H "Authorization: Bearer ${RECON_TOKEN}"

# Latest logs
gcloud run services logs read smart-study-buddy-go-tutor --region asia-south1 --limit 50
```

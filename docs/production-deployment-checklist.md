# Smart Study Buddy - Production Deployment Checklist

This document lists what is still pending before production release on Android (Play Store) and iOS (App Store).

## Current status snapshot

- Core tutor flow is deployed on Cloud Run and working.
- Firestore persistence is enabled and used for usage/tier state.
- Android billing flow (Play Billing client) is integrated in app code.
- iOS billing flow (StoreKit purchase + receipt capture) is integrated in app code.
- Backend has verification endpoints:
  - `POST /billing/google/verify`
  - `POST /billing/apple/verify`

## Release blockers (must finish before production)

## 1) Billing and entitlement security

- [x] Disable or protect `POST /tier` in production.
  - Reason: this endpoint can directly set plan tiers and should not be publicly available in production.
- [x] Accept tier upgrades only via verified store purchases (`/billing/google/verify`, `/billing/apple/verify`).
- [x] Add auth or signed identity binding for account ownership.
  - Recommended: Firebase Auth / Google Sign-In / Apple Sign-In.
  - Implemented: Firebase ID token validation + email-verified check + UID ownership binding to account record.

## 2) Android production billing setup (Play Store)

- [ ] Enable Google Play Android Developer API in GCP project (`androidpublisher.googleapis.com`).
- [ ] Link Play Console and GCP API access.
- [ ] Grant correct permissions to service account used by backend verification.
- [ ] Create subscription products in Play Console:
  - `neet_pro_monthly`
  - `neet_ultimate_monthly`
- [ ] Configure base plans/offers and activate them.
- [ ] Upload release build to Internal Testing track and verify purchase flow with test accounts.

## 3) iOS production billing setup (App Store)

- [ ] Create App Store Connect subscription products matching backend IDs:
  - `neet_pro_monthly`
  - `neet_ultimate_monthly`
- [ ] Set and configure `APPLE_SHARED_SECRET` in backend environment.
- [ ] Test StoreKit purchases with sandbox test users on real iOS device/TestFlight.
- [ ] Verify backend `/billing/apple/verify` end-to-end with real receipt data.

## 4) Subscription lifecycle handling (both stores)

- [x] Implement webhook/server-notification handling for renewals, cancellations, billing retry, refunds.
  - Google: RTDN (Real-time Developer Notifications)
  - Apple: App Store Server Notifications
- [x] Add periodic reconciliation job to prevent entitlement drift.
  - Implemented endpoints:
    - `POST /billing/google/rtdn` (protected by `GOOGLE_RTDN_BEARER_TOKEN`)
    - `POST /billing/apple/notifications` (protected by `APPLE_SERVER_NOTIFICATIONS_BEARER_TOKEN`)
    - `POST /billing/reconcile/google` (protected by `RECONCILE_BEARER_TOKEN`; trigger via Cloud Scheduler)

## 5) Secrets and environment hardening

- [x] Move secrets to Secret Manager (not plain env vars in scripts/shell history).
  - `GOOGLE_GENAI_API_KEY`
  - `APPLE_SHARED_SECRET`
  - (optional) `GOOGLE_PLAY_API_KEY` if used
- [ ] Ensure production Cloud Run service account has least privilege IAM only.
  - Deploy script now supports Secret Manager env injection (`*_SECRET` variables) and avoids plain secrets by default.

## High-priority (should complete before public launch)

## 6) App quality and platform readiness

- [ ] Add crash reporting/monitoring (Firebase Crashlytics + logs/alerts).
- [ ] Add analytics for conversion funnel (upgrade click -> purchase -> activated).
- [ ] Add robust network error handling + retry messaging around purchase verification.
- [ ] Complete iOS voice output implementation (`Voice.ios.kt` still TODO).

## 7) Store compliance and legal

- [ ] Verify Privacy Policy reflects billing + account linking behavior.
- [ ] Finalize Terms of Service / Refund policy.
- [ ] Complete Play Store Data Safety form with accurate data processing details.
- [ ] Complete App Store privacy nutrition labels.

## 8) Release pipeline and verification

- [ ] Ensure reproducible release builds for Android and iOS.
- [ ] Verify signing credentials and key backup process.
- [ ] Smoke-test matrix before release:
  - Free tier limit and reset behavior
  - Pro/Ultimate purchase activation
  - Restore on new device using same account
  - Offline and poor-network behavior
  - Cancellation/refund downgrade behavior

## Recommended order to production

1. Secure entitlement path (`/tier` lockdown + auth hardening).
2. Finish Play Console API + product setup and verify Android purchases.
3. Finish App Store Connect + shared secret setup and verify iOS purchases.
4. Implement renewal/cancel webhooks and reconciliation.
5. Run internal test cycles (Android internal track + iOS TestFlight).
6. Publish staged rollout (small percentage first), monitor, then scale.

## Optional hardening improvements

- [ ] Migrate Apple receipt validation from legacy `verifyReceipt` to App Store Server API (newer model).
- [ ] Add idempotency keys on billing verification endpoints.
- [ ] Add abuse/rate-limit controls for billing endpoints.
- [ ] Add audit trail table/collection for purchase verification events.

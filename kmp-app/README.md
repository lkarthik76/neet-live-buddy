# KMP Mobile App (Compose Multiplatform)

This is the Kotlin Multiplatform frontend for NEET Live Buddy.

## What is included

- Shared Compose UI (`commonMain`)
- Android app target
- iOS target scaffold
- Tutor API integration to Go backend (`/tutor`)
- Language support: `english`, `hindi`, `tamil`
- Android Text-to-Speech voice playback

## Current scope

- Works as a mobile-first shared UI/client foundation.
- Camera and speech-to-text are marked as next integration steps.

## Prerequisites

- JDK 17
- Android Studio (latest stable)
- Kotlin Multiplatform plugin (in Android Studio)

## Run on Android

1. Open `kmp-app` in Android Studio.
2. Let Gradle sync complete.
3. In `NeetLiveBuddyApp`, set backend URL:
   - `https://<your-go-cloud-run-url>` or local LAN URL
4. Run Android target on emulator/device.

## Backend requirement

Go tutor service must be reachable and expose:

- `POST /tutor`

Expected request fields:

- `prompt`
- `subjectHint`
- `language`
- `confused`
- `imageContext`

## Next tasks

- Add camera capture in KMP app (platform-specific integration)
- Add speech-to-text in app (platform-specific)
- Add iOS TTS implementation (AVSpeechSynthesizer)

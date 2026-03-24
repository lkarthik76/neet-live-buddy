# KMP Mobile App (Compose Multiplatform)

This is the Kotlin Multiplatform frontend for **Smart Study Buddy** (AI study companion).

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
3. In `SmartStudyBuddyApp`, set backend URL:
   - `https://<your-go-cloud-run-url>` or local LAN URL
4. Run Android target on emulator/device.

If Android Studio shows an error like `Activity class {com.neetbuddy.app/...} does not exist`, your **Run configuration** or an old install still uses the wrong package. Set launch to **Default Activity**, uninstall the app from the device, and ensure the built app id is **`com.smartstudybuddy.app`** (see `composeApp/build.gradle.kts`).

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

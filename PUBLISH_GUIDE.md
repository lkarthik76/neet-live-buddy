# Publishing NEET Live Buddy to Google Play Store

## Step 1: Generate Upload Keystore

```bash
cd neet-live-buddy/kmp-app
keytool -genkey -v -keystore neet-buddy-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias neet-buddy
```

When prompted:
- Enter a strong password (save it securely!)
- Fill in your name, organization, etc.

## Step 2: Create keystore.properties

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties` with your actual values:
```properties
storeFile=../neet-buddy-release.jks
storePassword=YOUR_ACTUAL_PASSWORD
keyAlias=neet-buddy
keyPassword=YOUR_ACTUAL_PASSWORD
```

## Step 3: Build Release AAB

```bash
cd kmp-app
./gradlew composeApp:bundleRelease
```

The AAB file will be at:
`composeApp/build/outputs/bundle/release/composeApp-release.aab`

## Step 4: Google Play Console Setup

1. Go to https://play.google.com/console
2. Pay the $25 one-time developer fee
3. Complete identity verification (takes 24-48 hours)

## Step 5: Create App in Play Console

1. Click "Create app"
2. App name: **NEET Live Buddy - AI Exam Tutor**
3. Default language: English (United States)
4. App type: App
5. Free or paid: Free

## Step 6: Fill Store Listing

Use the content from `store-listing.md`:
- Short description (80 chars)
- Full description
- Screenshots (take from your phone - need at least 2)
- Feature graphic (1024x500 image)
- App icon (512x512 - the Play Console generates this from your APK)

## Step 7: Content Rating

Complete the content rating questionnaire:
- Category: Education
- No violence, no sexual content, no drugs
- Rating will be: **Everyone**

## Step 8: Privacy Policy

Host the privacy policy at:
`https://github.com/lkarthik76/neet-live-buddy/blob/main/PRIVACY_POLICY.md`

Add this URL in Play Console under App content > Privacy policy.

## Step 9: Testing Track (Recommended First)

1. Go to Testing > Internal testing
2. Create a new release
3. Upload the AAB
4. Add yourself as a tester
5. Publish to internal track
6. Test on your device via the Play Store

## Step 10: Production Release

1. Go to Production > Create new release
2. Upload the AAB
3. Add release notes: "Initial release - AI-powered NEET exam tutor with multilingual support"
4. Submit for review

## Screenshots Needed

Take these screenshots from your phone (at least 2 required, recommend 5):
1. Home screen with subject and language selection
2. Camera scanning a question
3. AI answer with option analysis
4. Revision card view
5. Voice input in use

## Tips

- First app review typically takes 1-3 business days
- Make sure the app works end-to-end before submitting
- The backend must be running (Cloud Run) for the app to function

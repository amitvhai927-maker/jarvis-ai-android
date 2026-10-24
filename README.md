# JARVIS AI Assistant (Android + Node.js backend)

Starter project for an Android-native voice assistant with Hindi, Nepali, and English input/output.

## What works in this starter
- Native Android UI using Kotlin + Jetpack Compose
- Speech input using Android `SpeechRecognizer`
- Spoken replies using Android `TextToSpeech`
- Sends prompts to a separate Node.js backend
- Backend calls Gemini with `GEMINI_API_KEY` stored as an environment secret
- GitHub Actions workflow to build a debug APK

## Important limitations
This is a starter, not a full always-listening JARVIS. The microphone starts only when you tap the mic button. Speech recognition may use Android's configured speech service and can require internet/language packs. The app-open feature, wake word, background service, long-term memory, and authentication are not implemented yet.

## 1. Run the backend
Requires Node.js 20+.

```bash
cd backend
npm install
# Set GEMINI_API_KEY in your shell/environment; do not commit it.
# Linux/macOS:
export GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
npm start
```

By default, the backend listens on port 3000. For a phone to reach it, deploy the backend to a hosting service with HTTPS, then set `BASE_URL` in `app/build.gradle.kts` to that deployed URL. `10.0.2.2` is for the Android emulator only, not a physical phone.

## 2. Build the Android app
Open this folder in Android Studio and sync Gradle. Use JDK 17 and Android SDK 35.
Alternatively, push the repository to GitHub; the included workflow builds a debug APK on every push.

## 3. Build APK on GitHub from your Android phone
1. Create a new GitHub repository.
2. Upload the contents of this project (not the outer folder) to the repository.
3. Open the repository's **Actions** tab and enable Actions if prompted.
4. Wait for **Build Android debug APK** to finish.
5. Open the completed workflow run, scroll to **Artifacts**, and download `jarvis-debug-apk`.
6. Extract the ZIP and install the APK. Android may ask you to allow installs from that browser/file manager; only install APKs you built and trust.

## 4. API key safety
- Never put the Gemini key in Android code, `gradle.properties`, or a public repository.
- Set it only as a backend environment variable or hosting secret.
- Add authentication, rate limits, and usage limits before sharing the backend publicly.
- If you have already posted a key publicly, revoke/rotate it.

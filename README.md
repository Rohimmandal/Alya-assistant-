# Alya Assistant

A no-login, local-first AI assistant for the web and Android. The website and native Android app are separate clients of the Gemini API. This repository does not provision an API proxy, account service, APK host, or deployed website.

## Project layout

- `src/`: responsive React/TypeScript website, Gemini client, browser speech adapters, and tests.
- `app/`: native Android app (Java), encrypted settings, permissions, Android speech services, and Gemini client.
- `.github/workflows/`: GitHub Pages deployment and Android build/release pipelines.
- `.env.example`: public website/release metadata variable names only; no secrets.

The website is static and sends requests from the user's browser directly to Google over HTTPS. The Gemini key is entered in Settings and held in session storage; it is not placed in a URL or local history. Android stores the user-entered key in AndroidX `EncryptedSharedPreferences` with an Android Keystore-backed AES-GCM master key. The single Android runtime accessor is `SecureKeyStore.getUserApiKey()`. Conversations, preferences, and opt-in memories are stored locally and do not sync. There is no server-side provider key.

## Web development

Requirements: Node.js 22+ and npm.

```sh
npm ci
npm run dev
```

Run tests and create the production bundle:

```sh
npm test
npm run build
npm run preview
```

Enter a Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey) in Settings. Requests use Google's documented `generateContent` REST API with the configurable `gemini-2.5-flash` default. Check current model availability in the [official model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash). A key is required; missing keys never produce simulated assistant responses. The browser keeps the key in session storage until cleared or the session ends. Do not put keys in source, `.env`, or frontend builds.

Vite build variables are optional:

| Variable | Purpose |
| --- | --- |
| `VITE_OFFICIAL_WEBSITE_URL` | Official website link on the download page |
| `VITE_APK_URL` | Real published APK URL; the button stays disabled without it |
| `VITE_RELEASE_VERSION` | Version of a configured artifact |
| `VITE_RELEASE_DATE` | Publication date |
| `VITE_RELEASE_NOTES` | Release notes |

Copy `.env.example` to `.env` for local testing, or set these as hosting-platform build variables. Vite-prefixed variables are public and must not contain secrets. Provider key, quota, rate-limit, timeout, network, and service errors are handled without automatic retries.

## Android development

Requirements: Android SDK Platform 35, Android Build Tools, JDK 17, and Gradle 8.9. The minimum Android version is 10 (API 29). Open the project in Android Studio or run:

```sh
gradle :app:assembleDebug
```

The debug APK, when built, is `app/build/outputs/apk/debug/app-debug.apk`. A signed release needs the CI configuration below or Android Studio signing. Do not commit a keystore, password, or API key. This workspace has no Android SDK and does not have all Android Gradle artifacts cached; the APK has not been built here.

## Implemented behavior and limits

- The website has exactly three primary sections: **Download**, **Website**, and **Android app**. It opens directly to chat without login or signup.
- Website Gemini chat sends the latest 20 messages for context and supports request cancellation. Local conversations can be created, renamed, reopened, deleted, and cleared. Light/dark theme, language, speech preference, and model are configurable.
- Saved memories must be entered by the user, can be viewed and deleted, and are included in Gemini requests only while saved. Alya does not create memories automatically.
- Website speech input uses the browser SpeechRecognition API when available. Reply playback uses installed browser/system speech-synthesis voices; voice availability and gender depend on the device.
- Android starts in Settings when no encrypted user key is available. Its Settings screen is the only place to enter or remove a key; no developer key, `BuildConfig` key, or `local.properties` key is used. Existing users upgrading from the earlier custom key store may need to enter their key again.
- Android supports local multi-conversation history, encrypted API-key storage, microphone speech recognition after an explicit tap, on-device text-to-speech where installed, and user-managed local memories. TTS prefers an installed voice whose name indicates a feminine voice; otherwise it falls back to the system voice.
- Android reports current microphone, notification, location, contacts, accessibility, notification-listener, overlay, foreground-service, assistant-role, flashlight, battery, and hardware/permission state. Dangerous permissions are requested only after a feature-specific explanation. Contacts and last-known location are shown only after the user selects those actions.
- After the user enables Accessibility in Android settings, the user-started floating overlay can preview visible non-password text locally and click an exact visible label after a separate explicit tap. The app does not send screen content to Gemini. Android may limit what other apps expose through accessibility.
- After the user enables notification access in Android settings, the listener holds only the latest WhatsApp or default-dialer call-category notification preview in memory for local display. It is cleared when that notification is dismissed. This cannot read messages that an app does not include in its notification or guarantee detection of every call.
- SMS sending and phone calls require runtime permission and a second confirmation showing the recipient/number and message before the native action. SMS submission is not delivery confirmation. Wi-Fi settings can be opened; opening settings does not toggle Wi-Fi. A compatible flashlight can be toggled after camera permission, and an approximate one-shot timer can be scheduled.
- The foreground session is user-started, visibly notified, non-sticky, and does not use the microphone. Android may stop it. Overlay and battery exemption require separate user approval; neither guarantees background execution.
- Alya can request Android's default-assistant role and asks Android to allow launch from the keyguard. Power-button and lock-screen behavior depend on Android version, secure-device policy, and OEM settings. Alya cannot unlock a locked device.
- Timers use `AlarmManager.setAndAllowWhileIdle`; Android battery policy may delay notifications. Timers are not restored after reboot and are not guaranteed calendar reminders.
- Gemini Live sessions, live audio streaming, speech interruption, Gemini function calling, Google Search grounding, live translation, generated-code WebView previews, and model-triggered native device actions are not implemented. Chat uses Gemini `generateContent`; speech recognition plus text-to-speech is not a Live session.
- No wake-word engine or continuous microphone monitoring is implemented. The app does not secretly record audio. A user-started foreground notification is not a promise that Android will keep a process running.
- No speaker-verification model or voiceprint enrollment is included. Android device-admin/accessibility APIs cannot keep a phone unresponsive until voice recognition or automatically unlock it after a voice match; the device owner must unlock using the configured system credential. No guardian behavior is claimed.
- AccessibilityService, NotificationListenerService, overlay, contacts, location, SMS, calls, and assistant role are all opt-in. Android settings are authoritative; installing the app or opening a settings page does not grant access. Google Play may restrict distribution of sensitive permissions/services.
- No update manifest, endpoint, or installer integration is configured. The installed Android version is shown, but update checking/installation is not claimed.
- History, preferences, memories, and credentials are local to each browser/device. They do not transfer and may be lost when app/browser data is cleared or the app is uninstalled.

## Website deployment

Push to `main` or start **Deploy website** manually. The workflow runs `npm ci`, tests, builds `dist/`, and deploys to GitHub Pages. Enable GitHub Pages with **GitHub Actions** as its source in repository settings. Configure the public `VITE_*` build variables after real deployment and release URLs exist. No website is deployed until that workflow succeeds.

## APK build and release

Start **Android APK** manually for web checks and a debug APK. The workflow checks that the artifact exists, prints its SHA-256 digest, verifies Android package metadata, and uploads the APK to the workflow run.

To publish a signed GitHub Release APK, add these repository Actions secrets and push a tag such as `v0.1.0`:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded release keystore.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Tag builds fail if a signing secret is missing. The keystore is decoded only into the ephemeral runner's temporary directory. The workflow publishes the signed artifact to GitHub Releases. Configure `VITE_APK_URL` and release metadata only after a real public URL exists. No download URL or release is fabricated.

## Permissions and troubleshooting

The website microphone relies on browser permission and browser-specific SpeechRecognition support. If unavailable, type instead; text-to-speech may still work independently. Android 13+ requires notification permission for timer notifications; Android can still delay alarms due to battery settings. Accessibility and notification access must be enabled in Android's dedicated settings screens. SMS, calls, contacts, location, microphone, and camera require runtime consent. Some OEMs do not expose the default assistant role or call notifications consistently.

If Gemini reports 400/401/403, check the key, model, and request; 429 means rate/quota limit; 5xx means temporary provider unavailability. Check the key's Google AI plan and quota. Requests time out after 30 seconds and are not automatically retried.

For a deployment threat model requiring server-side quotas, do not put a privileged key in the static site. Implement a properly protected backend first; a public unauthenticated key proxy is not included. If local Android build reports a missing SDK, install API 35 and Android Build Tools or run the GitHub Actions build. Android emulators/physical devices are needed to verify microphone, TTS voices, flashlight, notifications, and timers.

## Verification status

The checked-in web tests cover no-login startup, the three sections, truthful APK availability, Gemini requests/errors, and local conversation history. Run `npm test` and `npm run build`. Android compilation/APK generation, device testing, website deployment, and signed publishing require the configured Android SDK/device or GitHub Actions; none is claimed as completed from this workspace.

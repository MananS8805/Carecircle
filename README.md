# CareCircle

<img src="docs/logo.svg" alt="CareCircle logo" width="72" align="right">

Android app (Kotlin + Jetpack Compose) that helps families manage medicines for parents and grandparents, plus a small Vercel serverless backend that builds plain-English medicine info cards from official sources. India first, local-first.

> Informational only, not medical advice.

## Design

A calm, light interface with a black pill navigation bar and a single orange accent. The home screen shows a **living organ** for the condition each medicine treats (pancreas for diabetes, heart for blood pressure, lungs for asthma, thyroid, kidney, brain, joints). The organs are drawn in code (no image assets), animate (the heart beats, the lungs breathe) and respond to taps: tap an organ to see which medicines act on it, swipe to move between organs. The logo is an open ring (the circle of family) around a heart, with an orange dot where the ring opens.

## Versions

Every version is a git tag (`v0.1` ... `v0.6`); see [CHANGELOG.md](CHANGELOG.md).

| Version | What it adds |
|---------|--------------|
| v0.1 | Starter: schedule, exact-alarm reminders, missed-dose and refill alerts, strip scanner, info card backend |
| v0.2 | Compiles; new interface and organ view; info card states; reminder onboarding; Snooze 10 min |
| v0.3 | Time picker, food hints, interaction check, export / restore / delete all |
| v0.4 | Insurance policy summary with clause citations, CameraX strip scanner |
| v0.5 | Multiple people with separate data, Hindi and Gujarati UI with in-app switch, CI and release signing config |
| v0.6 | Condition tags with cited policy notes, voice entry of medicines, adherence history and PDF report |

## Run the Android app

1. Android Studio (Ladybug or newer) with Android SDK 35 and JDK 17.
2. Open this folder. The Gradle wrapper (8.9) is committed.
3. Point the app at your backend by adding these lines to `~/.gradle/gradle.properties` (not committed):
   ```
   careCircleApi=https://YOUR-APP.vercel.app/
   careCircleAppKey=YOUR_APP_KEY
   ```
   `YOUR-APP.vercel.app` is the deploy URL from the backend step below (placeholder until deployed). `careCircleAppKey` must equal the backend `APP_KEY`; leave it out if the backend has none.
4. Run on a device or emulator (API 26+). On first launch a short setup dialog asks for notifications, exact alarms and battery settings.
5. Tests: `./gradlew :app:testDebugUnitTest`; build: `./gradlew :app:assembleDebug`; lint: `./gradlew :app:lintDebug`.

## Run / deploy the backend

```
cd backend
npm install
npm test
npm run typecheck
npx vercel dev      # local
npx vercel --prod   # deploy
```
Environment variables (Vercel project settings): `ANTHROPIC_API_KEY` (optional; without it cards use extractive text from the label, never invented text), `LLM_MODEL` (default `claude-haiku-4-5`), `APP_KEY` (optional shared secret sent by the app as `x-app-key`). The LLM key lives only on the server. Add a Vercel Firewall rate-limit rule for `/api/*` for real abuse protection (the in-code limiter is per instance).

## Screenshots

_Placeholder: add phone screenshots here (Today with the organ view, Medicines, Insurance summary, Settings) before a public release._

| Today | Medicines | Insurance | Settings |
|-------|-----------|-----------|----------|
| `docs/screenshots/today.png` | `docs/screenshots/meds.png` | `docs/screenshots/insurance.png` | `docs/screenshots/settings.png` |

## Release checklist

1. `cd backend && npm ci && npm run typecheck && npm test`, then deploy (`npx vercel --prod`) and set `ANTHROPIC_API_KEY`, `LLM_MODEL`, `APP_KEY` in Vercel.
2. Point the app at the deployed backend (`careCircleApi`, `careCircleAppKey` in `~/.gradle/gradle.properties`).
3. `./gradlew :app:testDebugUnitTest :app:lintDebug`; on a phone run `./gradlew :app:connectedDebugAndroidTest` (migrations) on a test device only: it reinstalls the app.
4. Have a native speaker review the Hindi and Gujarati strings (disclaimers and the insurance consent screen first).
5. Bump `versionCode` / `versionName` in `app/build.gradle.kts`, update `CHANGELOG.md`, tag the release (`git tag vX.Y`).
6. Build a signed release: set `careCircleKeystore`, `careCircleKeystorePassword`, `careCircleKeyAlias`, `careCircleKeyPassword` (gradle properties) or `CARECIRCLE_KEYSTORE`, `CARECIRCLE_KEYSTORE_PASSWORD`, `CARECIRCLE_KEY_ALIAS`, `CARECIRCLE_KEY_PASSWORD` (environment) and run `./gradlew :app:bundleRelease`. Without them the release is unsigned. Never commit keystores or passwords.

## API

- `GET /api/medicine-info?salt=...` plain-English card from official sources (RxNorm, openFDA label, MedlinePlus).
- `GET /api/interactions?salts=a,b,c` (max 8 names): label sentences that mention another of your medicines, each with a source link; honest empty state otherwise.
- `POST /api/policy-summary` body `{pages:[{page,text}]}` (max 40 pages, 200,000 characters, else 413). Returns `{summary, unverified, mode, disclaimer}`. Every item carries page, clause and a quote that was checked to appear on that page; anything that cannot be verified is dropped and listed in `unverified`. Without an LLM key `mode` is `extractive` (regex). Limit: 5 requests per minute per IP. The request and result are never stored or logged. Only call it after explicit consent in the app.

## Privacy

Data is local-first (Room + SharedPreferences). Only a medicine's generic name is sent to the backend, and insurance text only after an explicit consent screen (never stored by the server). Strip photos never leave the phone. WhatsApp and SMS messages are composed by you and sent by you.

## Layout

- `app/` Android app. `domain/` pure logic (JVM-testable), `data/` Room + API, `reminder/` alarms, workers, notifications, `ui/` Compose screens, `ui/theme/` design system, logo and organ art.
- `backend/` Vercel functions: `api/medicine-info.ts`, `api/interactions.ts`, `api/policy-summary.ts`, `lib/` logic.
- `docs/` logo and design notes.

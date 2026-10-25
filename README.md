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
| v0.5 | Multiple people, Hindi and Gujarati, CI |
| v0.6 | Condition tags with cited policy notes, voice entry, adherence history |

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

## Privacy

Data is local-first (Room + SharedPreferences). Only a medicine's generic name is sent to the backend. Strip photos never leave the phone. WhatsApp and SMS messages are composed by you and sent by you.

## Layout

- `app/` Android app. `domain/` pure logic (JVM-testable), `data/` Room + API, `reminder/` alarms, workers, notifications, `ui/` Compose screens, `ui/theme/` design system, logo and organ art.
- `backend/` Vercel functions: `api/medicine-info.ts`, `api/policy-summary.ts`, `lib/` logic.
- `docs/` logo and design notes.

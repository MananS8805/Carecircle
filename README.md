# CareCircle (v0.1 starter)

Android app (Kotlin + Jetpack Compose) that helps families manage medicines for parents/grandparents, plus a small Vercel serverless backend that builds plain-English medicine info cards from official sources. India first.

Informational only, not medical advice.

## What v0.1 does
1. Medicine schedule: name, dose, times, pill count; exact-alarm reminders (AlarmManager + WorkManager); big "Taken" button (in notification and on Today screen).
2. Missed-dose alert: no "Taken" within 30 minutes -> notification that opens a pre-filled WhatsApp message to the caregiver (no backend).
3. Refill countdown: pills decrease per dose; at 5 days left a "Reorder" message to the pharmacy opens in WhatsApp.
4. Strip scanner: photo of the strip -> on-device ML Kit OCR -> generic name(s) pre-filled for you to confirm.
5. Medicine info card: backend `/api/medicine-info?salt=...` (openFDA/DailyMed label + RxNorm + MedlinePlus, optional LLM rewrite), cached in Room for offline.
6. Doctor-visit PDF: share a list of current medicines.

## Run the Android app
1. Install Android Studio (Ladybug or newer) with Android SDK 35 and JDK 17+.
2. Unzip, open the `carecircle` folder in Android Studio. It will sync Gradle and create the Gradle wrapper (or run `gradle wrapper --gradle-version 8.9` once).
3. Point the app at your backend: add to `~/.gradle/gradle.properties` (or `local` gradle properties):
   `careCircleApi=https://YOUR-APP.vercel.app/` and optionally `careCircleAppKey=...`
4. Run on a device/emulator (API 26+). Grant notification permission; for exact reminders use Settings > "Allow exact alarms".
5. Unit tests (pure Kotlin logic): `./gradlew :app:testDebugUnitTest`

## Run / deploy the backend
```
cd backend
npm install
npm test            # 11 tests
npm run typecheck
npx vercel dev      # local
npx vercel --prod   # deploy
```
Environment variables (Vercel project settings): `ANTHROPIC_API_KEY` (optional; without it cards use extractive text from the label, never invented text), `LLM_MODEL` (default claude-haiku-4-5), `APP_KEY` (optional shared secret sent by the app as `x-app-key`). The LLM key lives only on the server. Add a Vercel Firewall rate-limit rule for `/api/*` for real abuse protection (the in-code limiter is per instance).

## Privacy
Data is local-first (Room + SharedPreferences). Only a medicine's generic name is sent to the backend. Strip photos never leave the phone. WhatsApp/SMS messages are composed by you and sent by you.

## Layout
- `app/` Android app. `domain/` pure logic (JVM-testable), `data/` Room + API, `reminder/` alarms, workers, notifications, `ui/` Compose screens.
- `backend/` Vercel functions: `api/medicine-info.ts`, `api/policy-summary.ts` (stub for Week 2), `lib/` logic.

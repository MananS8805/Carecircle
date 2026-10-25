# CareCircle - context for Claude Code

## Product
Android app helping families manage medicines for elderly parents (India first). Local-first. Medical/insurance info must always cite sources and show "Informational only, not medical advice". Never present generated text as medical advice.

## Architecture
- `app/` single Android module, package `com.carecircle`. Kotlin 2.0.21, AGP 8.7.3, Compose (BOM 2024.12.01), Room 2.6.1 (KSP), WorkManager, ML Kit text-recognition (on-device), Retrofit + kotlinx.serialization, minSdk 26, target/compile 35.
  - `domain/` PURE Kotlin, no android.* imports, unit-tested on JVM: `Schedule` (parse times, next dose), `Refill` (days left, reorder threshold 5), `MissedDose` (30 min grace), `Messages` (WhatsApp wa.me URLs, India +91 normalisation), `SaltParser` (OCR text -> generic names).
  - `data/` Room (`Medicine`, `DoseEvent` unique on (medicineId, scheduledAt), `InfoCardEntity` offline cache), `Prefs` (caregiver/pharmacy phones), `Api` (Retrofit `CareApi`), `Repository` (cache-first info cards).
  - `reminder/` `ReminderScheduler` (one exact alarm per medicine = next dose; receiver re-arms), `DoseAlarmReceiver`, `TakenActionReceiver`, `BootReceiver`, `MissedDoseWorker` (30 min after dose), `DoseActions.markTaken` (idempotent; decrements pills; reorder alert), `Notifier`.
  - `ui/` Compose: Today, Medicines, Edit (with strip scan via system camera TakePicture + FileProvider + ML Kit), Detail (info card), Settings; `PdfExport` (android.graphics.pdf).
- `backend/` Vercel serverless TypeScript (Web `Request`/`Response` handlers, ESM). `api/medicine-info.ts` -> `lib/medicineInfo.ts`: normalize salt -> RxNorm rxcui + openFDA label + MedlinePlus -> LLM rewrite constrained to fetched text (falls back to extractive) -> cached (6h in-memory + CDN s-maxage) and rate-limited. `api/policy-summary.ts` is a 501 stub (Week 2).

## Commands
- Backend: `cd backend && npm install && npm test && npm run typecheck`
- Android: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug` (needs Android SDK 35; wrapper may need generating: `gradle wrapper --gradle-version 8.9`)

## Conventions
- Keep business logic in `domain/` with tests; keep Android classes thin.
- All user health data stays on device. Network sends only generic names. Any upload of documents (insurance, Week 2) requires an explicit consent screen and is never stored server-side.
- LLM keys only on the server. LLM prompts must say "use only the supplied source text"; validate JSON output; always fall back gracefully.
- No heavy dependencies without need. Follow existing code style (official Kotlin style, 4 spaces; TS strict, ESM with `.js` import suffixes).
- India defaults: +91 numbers, INN->USAN name map in `backend/lib/sources.ts` (paracetamol -> acetaminophen etc.).

## Status (v0.1)
Backend verified (tests + typecheck). Android sources were written WITHOUT an Android SDK available, so they have not been compiled: first task is always `./gradlew :app:assembleDebug` and fixing any compile errors.
Notes: RxNorm interaction API was retired, so interactions come from the label's drug_interactions section. Strip scan uses the system camera (TakePicture) instead of CameraX for simplicity; CameraX live preview is a later upgrade.

## Roadmap
Week 2: cloud LLM summaries hardening, interaction check, insurance policy summary card (`/api/policy-summary`). Week 3: Hindi/Gujarati UI, caregiver sync (Firebase), voice entry. Later: "Can I claim this?" with clause citations.

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

## Status (v0.6)
Android app compiles and `DomainTest` passes (wrapper 8.9, JDK 17, SDK 35; set `sdk.dir` in local.properties, git-ignored). UI lives in `ui/` (screens) and `ui/theme/` (Theme, Components, OrganArt, logo). All user-facing text is in `res/values/strings.xml`. `domain/Condition.kt` maps medicines to conditions/organs for the organ view.
Notes: RxNorm interaction API was retired, so interactions come from the label's drug_interactions section. Strip scan uses the system camera (TakePicture) instead of CameraX for simplicity; CameraX live preview is a later upgrade.

## Roadmap
Week 2: cloud LLM summaries hardening, interaction check, insurance policy summary card (`/api/policy-summary`). Week 3: Hindi/Gujarati UI, caregiver sync (Firebase), voice entry. Later: "Can I claim this?" with clause citations.

## v0.3 notes
- Room is at version 2 (exportSchema on, schemas in `app/schemas`). Every schema change needs a real `Migration` in `data/Db.kt` plus a case in `androidTest/.../MigrationTest.kt`. No destructive fallback.
- `data/Backup.kt`: `BackupCodec` (pure, unit-tested) + `BackupService`. Backup schema version 1; bump `CURRENT_VERSION` and keep reading older ones.
- Backend `GET /api/interactions` reuses the openFDA label fetch (interaction text clipped at 12000 chars); never log names or snippets.
- Compose UI text goes in `res/values/strings.xml`. Do not use bash heredocs with apostrophes in tooling here; use the file editor.

## v0.4 notes
- Room version 3 (`policy_summary` table stores summary JSON only). Backup schema version 2.
- Policy models live in `domain/Policy.kt` (pure, serializable). No claim may be shown without page, clause and quote; always show "Informational only. Verify with your insurer."
- OCR text (insurance) exists only in `InsuranceState.NeedConsent` memory and is dropped on cancel, decline or send. Never log it.
- Strip scanner: `ui/StripScanScreen.kt` (CameraX 1.4.1). Result is handed to Edit through `AppViewModel.pendingScan`; Edit form fields use rememberSaveable.

## v0.5 notes
- Room version 4: `person` table; `medicine.personId` and `policy_summary.personId` (no foreign keys, deletes cascade by hand in `Repository.deletePerson`). At least one person always exists (`Repository.ensureDefaultPerson`, Room `onCreate`, delete-all). Backup schema version 3.
- Contacts live on `Person`, never in Prefs. Message templates are string resources (`msg_missed`, `msg_reorder`); `Messages` only builds links.
- Notifications, messages and the PDF use `AppLocale.wrap(ctx)` so they follow the app language, not the system language.
- **Translations are machine-drafted and must be reviewed by a native speaker before release.** Review first: `disclaimer_medical`, `disclaimer_insurance`, `disclaimer_interactions`, the consent screen (`consent_*`, `ins_notice_pill`), `onb_*`, `data_delete_*`, `msg_missed`, `msg_reorder`.
- `StringsTest` checks every key exists in en/hi/gu with identical placeholders. Lint treats MissingTranslation and HardcodedText as errors.
- CI: `.github/workflows/ci.yml`. Release signing is read from gradle properties or `CARECIRCLE_*` environment variables.

## v0.6 notes
- Room version 5: `medicine.conditionTag`, `createdAt`, `archivedAt`; `dose_event.skipped`. Adherence counts a dose only between `createdAt` and `archivedAt`. Backup schema version 4.
- `domain/Coverage.kt` (synonym map in `ConditionSynonyms`), `domain/VoiceParser.kt`, `domain/Adherence.kt` are pure and unit-tested. Tags, coverage matching, voice text and adherence never leave the phone (only user-initiated share intents and the PDF).
- Voice entry uses `RecognizerIntent` (the system speech service), so CareCircle holds no RECORD_AUDIO permission; the rationale dialog says the service may use its own online processing.
- New strings must be added to values, values-hi and values-gu (StringsTest and lint enforce it). Review the new disclaimers (`cov_*`, `voice_rationale`, `msg_weekly`) with a native speaker.

## v0.6.1 notes
- `data/OnDeviceAi.kt` wraps `com.google.mlkit:genai-prompt:1.0.0-beta4`. The libraries are built with a newer Kotlin than 2.0.21, hence `-Xskip-metadata-version-check` in `app/build.gradle.kts`; upgrading Kotlin to 2.2+ would remove the need. Unsupported phones report UNAVAILABLE; always keep the extractive fallback.

## v0.7 notes
- Room version 6: `medicine.expiry` ("2028-02"), `medicine.doctorNote`, `symptom_log` table. Backup schema version 5.
- `domain/Expiry.kt` reads the printed EXP month only; a strip is valid through the last day of that month.
- Backend `LLM_PROVIDER` / `GEMINI_API_KEY` / `GEMINI_MODEL` pick the info-card rewriter; keys live only in Vercel env, never in git. The Gemini free tier may use prompts for training, so it must never receive insurance text.

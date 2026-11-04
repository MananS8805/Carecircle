# Catch-up status (brief v0.6 -> v1.3)

The brief was written against a v0.6 baseline. This project had already moved on in its own direction (v0.7 to v1.3.1:
Firebase pairing-code sync, a separate family app, safety flags, vitals). Where the brief and the code disagree, finished
work is kept and the gap is filled; nothing is rewritten for the sake of matching the brief's wording.

Version mapping: the brief's v0.7 to v1.3 would collide with tags that already exist, so these phases ship as
**v1.4 (A), v1.5 (B), v1.6 (C), v1.7 (D), v1.8 (E), v1.9 (F), v2.0 (G)**.

Nothing in this file was verified on a phone or emulator. Verification so far is unit tests, lint and assembleDebug
(Android) and vitest and tsc (backend). Phone checks are listed per phase under "needs a device".

Baseline before starting (2026-11-04): backend 39 tests and typecheck green; Android unit tests, lint, assembleDebug green.

| Phase | Item | Status | Evidence |
|---|---|---|---|
| A1 | Boot / update / time / time-zone / exact-alarm receivers | done | `BootReceiver` manifest actions, `RescheduleWorker.enqueue` |
| A1 | `rescheduleAll(reason)` via WorkManager, 12 h safety net | done | `ReminderScheduler.rescheduleAll`, `RescheduleWorker.ensurePeriodic` |
| A1 | Health issues NO_RECENT_RESCHEDULE, ALARM_COUNT_MISMATCH | done | `domain/ReminderHealth.kt`, `ReminderHealthTest` |
| A1 | Names EXACT_ALARM_DENIED / BATTERY_OPTIMIZED | kept existing names | `EXACT_ALARMS_OFF`, `BATTERY_RESTRICTED` (same meaning) |
| A1 | Health screen, test reminder, 20-entry log | done | `HealthScreen.kt`, `Prefs.rescheduleLog`. Test reminder posts straight away, not in 1 minute (existing behaviour) |
| A1 | Missed while phone off | done | `RescheduleWorker.sweepMissed`, `MissedSweep` + test |
| A2 | `/api/policy-question` with citation verifier | done | `backend/lib/policyQuestion.ts`, `policyQuestion.test.ts` |
| A2 | "Ask about your policy" UI, consent each time, prefill from tag | done | `ui/AskPolicy.kt`. Clauses sent are the saved summary quotes (the document itself is never kept on the phone) |
| A3 | Daily LLM budget, JSON request log, `/health` | done | `budget.ts`, `log.ts`, `health.ts`, `budget.test.ts`. Default store is per-instance memory; see `docs/backend-production.md` |
| G2 | Claim card + backend confidence/clauseRef/excerpt | done early in A2 | `domain/ClaimAnswer.kt`, `ClaimAnswerTest` |
| B1 | Home-screen widget | done, **RemoteViews instead of Glance** | `reminder/DoseWidget.kt`, `domain/NextDose.kt`, `NextDoseTest`. No new dependency; same states (due, upcoming, +N more, all done with tomorrow, empty, overdue) |
| B2 | Refill history, "I bought more", undo | done | `RefillEvent`, migration 10 to 11, `RefillUi.kt`, `RefillTest`, `MigrationTest` (instrumented, not run) |
| B2 | Monthly order message | done | `Messages.monthlyOrder` + button on Medicines; user-initiated share only |
| B2 | Backup / delete-all with refills | done | backup version 9, `BackupTest`; old files still import |
| B3 | Text size, high contrast, Simple mode | done | `domain/Display.kt`, `DisplayTest`, Settings > Display |
| B3 | Screen audit and semantics tests | partial | dose rows, Taken, source links, chart days and the family ring fixed; `AccessibilityTest` compiles but has not run. A full TalkBack walk-through of every screen is **needs a device** |
| C1 | First-run onboarding (5 steps, resumable) | done | `domain/Onboarding.kt`, `OnboardingTest`, `ui/OnboardingScreen.kt`. Step 3 hands off to the existing scan / add screens |
| C1 | "What is new" for existing users, replay in Settings | done | `Prefs.whatsNewSeen`, `vm.replayGuide` |
| C2 | Privacy page, policy, Play docs, privacy.html | done | `ui/PrivacyScreen.kt`, `docs/privacy-policy.md`, `docs/play-store/*`, `backend/scripts/build-privacy.mjs`, `PrivacyDocsTest`, `privacy.test.ts`. `SUPPORT_EMAIL` / `PRIVACY_URL` are blank placeholders (TODO). Legal review still needed |
| C3 | `UserDataRegistry` drives export, restore, delete-all | done | `data/UserDataRegistry.kt`, `UserDataRegistryTest` |
| C3 | Password-protected backups | done | `data/BackupCrypto.kt`, `BackupCryptoTest`. UI not run on a device |
| C3 | Transactional restore with preview, delete-all summary | done | `BackupService.restore` uses `withTransaction`; the SQL-in-transaction path **needs a device check** |

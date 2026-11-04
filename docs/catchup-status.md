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

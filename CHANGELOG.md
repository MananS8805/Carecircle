# Changelog

Each version is a git tag. Informational only, not medical advice.

## v0.6.1
- Settings: "On-device AI (experimental)" card. Checks whether the phone can run Gemini Nano through ML Kit GenAI (AICore), can download the model and run a quick local test. Groundwork for simplifying medicine text with no API key and no network.
- Fixed a lint error in v0.6 (explicit Int types on `AdherenceResult`) so CI passes. Compiler flag `-Xskip-metadata-version-check` lets Kotlin 2.0.21 read the newer ML Kit GenAI libraries.

## v0.6
- Condition tags: "What is it for?" chips plus free text on every medicine (also drives the organ view). The Detail screen can suggest a tag from the medicine information, never silently. Room migration 4 to 5 (tag, created/archived times, skipped doses).
- "Your policy" card on a medicine: lines from the saved policy summary that mention its condition, each with page, clause and an expandable quote. Honest empty states; never says covered or excluded. Matching runs on the phone with a synonym map in `domain/Coverage.kt`.
- Voice entry of a new medicine (English, Hinglish, Hindi, Gujarati phrases): the phone speech service listens, `VoiceParser` fills name, dose, times and food timing, fields are marked "Check this", nothing is saved until you press Save.
- Adherence History (7 / 30 days): totals and percentage (never 0% without data), a bar per day, doses of a tapped day, per-medicine breakdown (most missed first), "Share weekly summary". "Skip this dose" action. The doctor-visit PDF now lists what each medicine is for and an "Adherence (last 30 days)" section.
- Backup schema version 4 (reads 1 to 3).

## v0.5
- People: each person has their own medicines, saved policy summaries and caregiver/pharmacy numbers. Person chips on Today (with "All people", grouped), Medicines and Insurance; "Manage people" in Settings (add, rename, edit numbers, delete with a confirmation that states how many medicines go). Room migration 3 to 4 moves the old numbers into the default person. Backup schema version 3 (reads 1 and 2). WhatsApp messages name the person; buttons are disabled with a hint when a number is missing; notification titles include the name when there is more than one person.
- Hindi and Gujarati across every screen, notification, message template and the PDF, with an in-app language switch (per-app locale, `localeConfig`). Dates use a 12-hour clock with Latin digits.
- CI (GitHub Actions: backend typecheck and tests; Android unit tests, lint, debug build), release signing config that is skipped without secrets, lint fails on missing translations and hard-coded text.
- Today now refreshes its day window after midnight.

## v0.4
- Insurance tab: pick PDFs or photos (PdfRenderer + on-device ML Kit), consent screen every time (what is sent, to whom, not stored), cited summary (page, clause, expandable quote), "could not verify" list, save or delete summaries on the phone. Room migration 2 to 3; backup schema version 2 (still reads version 1).
- Backend `POST /api/policy-summary`: quote verification, 413 limits, extractive fallback, 5 requests/min/IP.
- Strip scanner: CameraX live preview with framing guide, torch, low-confidence retry, gallery fallback, camera permission rationale; frames stay in memory.

## v0.3
- Time picker (Material 3 clock) with removable time chips instead of typing "08:00,20:00"; existing lists open as chips.
- "How to take" (before / with / after food, empty stomach, bedtime), shown on the Today card and in the dose notification. Room migration 1 to 2 (no destructive fallback); schemas exported to `app/schemas`.
- Interaction check: `GET /api/interactions?salts=a,b,c` scans official label interaction text for the other names (incl. paracetamol/acetaminophen synonyms), returns cited sentences or an honest empty state; app caches the last result for offline.
- Your data: export to JSON through the system file picker, restore with a confirmation (counts, version check), two-step delete-all (tables, alarms, jobs, preferences).
- Tests: backend 20 (9 new), app BackupTest, time-list and food-timing tests; instrumented MigrationTest.

## v0.2
- App compiles and unit tests pass (Gradle wrapper 8.9 committed).
- New interface: light theme, orange accent, black pill navigation, minimal logo (ring around a heart) and adaptive launcher icon.
- Interactive organ view on Today: pancreas, heart, lungs, thyroid, kidney, brain, joints, whole body, drawn in code and animated; tap for linked medicines, swipe between conditions.
- Info card states: loading, offline with saved copy, nothing found (with "Edit name"), "Too many requests" (429), error with Retry. An empty name never calls the API.
- First-run reminder setup (notifications, exact alarms, battery) and a warning banner until granted.
- "Snooze 10 min" notification action (one-shot alarm re-posts the same dose; missed-dose deadline unchanged).
- User-facing text moved to string resources.

## v0.1
- Starter: schedule and exact-alarm reminders, missed-dose WhatsApp alert, refill countdown, strip scan, info card backend, doctor-visit PDF.

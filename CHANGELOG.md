# Changelog

Each version is a git tag. Informational only, not medical advice.

## v1.2
- Reminders health check (Settings): one screen that answers "will my reminders fire?" with checks for notifications, reminder categories, exact alarms, battery, Do Not Disturb, scheduled medicines and when the last reminder was posted, a fix button for each, and a test reminder.
- Home-screen widget: the dose that needs attention (or the next one) with a one-tap Taken button; refreshed whenever a dose changes.
- Second caregiver: an optional backup number per person. If a dose is still open 45 minutes after its time (15 minutes after the first alert), a notification offers a pre-filled WhatsApp message to the second caregiver; the Today card also gets a button. Family sync already supports several family phones per person (one code each).
- Room migration 8 to 9; backup schema version 7.

## v1.1
- Safety checks, all on the phone: an allergy list per person (Manage people) with a warning on the Edit and Detail screens when a medicine's name or generic name seems to fall under it (including groups such as penicillin or NSAIDs); a warning when the same ingredient is in more than one medicine; a "High-alert medicine" flag with a reminder to follow the prescription exactly (warfarin, insulin, digoxin and similar). Warnings never block saving and never say a medicine is safe.
- Medicine card text is tidied: section headings and reference marks from the label are removed, three short sentences per section with "Show more".
- Room migration 7 to 8; backup schema version 6 (reads 1 to 5).

## v1.0
- Family view: a separate experience for people who follow a patient, with its own calm teal look and its own navigation (Family, Add, Settings). First launch asks "I take medicines" or "I follow a family member"; the choice can be switched in Settings.
- Each followed person has a live card: an animated ring of doses taken today, a mood (all taken, on track, missed, no update), and the next dose. Their page has a big tappable ring (flip to the next dose), an interactive organ for what their medicines are for (worked out on the family phone from medicine names), a 14-day bar chart (tap a day), and a timeline of recent activity.
- The patient phone now keeps a small "today" status (taken, scheduled, missed, next dose) up to date in Firestore; rules updated in `firebase/firestore.rules` (publish them again).

## v0.9
- Caregiver sync (Firebase anonymous sign-in + Firestore): a patient phone creates a short-lived 8-character code after a consent screen; a family phone enters it. From then on the patient phone sends **Taken, Missed (automatically after 30 minutes, no tap), Skipped and a 9 pm daily summary**, with names and times only. The family phone shows a live status list and, in the background about every 15 minutes, raises notifications for missed doses and daily summaries.
- Either side can end the link; sharing off deletes the stored events. Delete-all and restore also unlink. Events older than 14 days are pruned.
- `firebase/firestore.rules` and `firebase/README.md` document the setup. `app/google-services.json` is git-ignored; without it the app builds and Family sync says it is not set up.
- Room migration 6 to 7.

## v0.8
- Escalation so a dose is not silently ignored: reminder at the dose time, repeats at +10 and +20 minutes (the second on a louder alarm-sound channel, each saying how many minutes are left before the family is told), family alert at +30 minutes. Repeats stop when the dose is taken or skipped.
- Evening summary: at 9 pm each person with a caregiver number gets a notification that opens a pre-filled WhatsApp message ("Dad, today: 3 of 4 doses taken. Not taken: Metformin."), so family hears even if nothing was tapped.
- Honest limit: WhatsApp cannot be sent silently by an app, so these still need one tap. Automatic delivery comes with caregiver sync (planned, needs a Firebase project).

## v0.7
- Medicine card, Android side: the strip scan now also reads the printed expiry month (never the manufacturing date), and Edit has an Expiry field. Expiry shows as a green, amber or red pill on the card, the amber/red ones on the Medicines list, and an expired strip warns on the Today dose card.
- "Doctor's instructions" note on each medicine, shown above the generic information and kept on the phone.
- Card freshness: "Information updated on ..." (or "Saved copy from ...") with a Refresh button.
- Side-effect diary per medicine (quick symptom chips, severity, optional note, a "contact your doctor" hint for severe); the doctor-visit PDF lists the last 30 days.
- Room migration 5 to 6; backup schema version 5 (reads 1 to 4).
- Backend (not deployed): Gemini provider switch for the info-card rewrite (`GEMINI_API_KEY`, `GEMINI_MODEL`, `LLM_PROVIDER`). Insurance summaries still use only the Anthropic provider.

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

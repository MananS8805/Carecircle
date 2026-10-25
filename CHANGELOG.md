# Changelog

Each version is a git tag. Informational only, not medical advice.

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

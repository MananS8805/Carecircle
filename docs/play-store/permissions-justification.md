# Permissions and why

`scripts/check-release.sh` (phase D) compares the built manifest with this list.

| Permission | Why |
|---|---|
| `INTERNET` | Medicine cards, interaction check, policy summary and questions, family sync |
| `POST_NOTIFICATIONS` | Dose reminders with Taken and Snooze buttons, missed-dose and refill alerts |
| `SCHEDULE_EXACT_ALARM` | Reminders on the minute. Without it the app falls back to inexact alarms and the health check shows red |
| `RECEIVE_BOOT_COMPLETED` | Re-arm reminders after a restart (also after an app update and a time or time-zone change) |
| `USE_FULL_SCREEN_INTENT` | Declared for the urgent dose alert channel. If this is not used by a shipped feature, remove it before release |
| `CAMERA` | Photograph a medicine strip or an insurance paper. Optional: gallery pick works without it. Photos are read on the phone and deleted after reading |

Not requested: `RECORD_AUDIO` (voice entry uses the system speech service), contacts, location, storage.

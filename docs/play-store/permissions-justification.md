# Permissions and why

`scripts/check-release.sh` compares the built manifest with the first column of this table, so a permission added to the app or by a
library fails the check until it is explained here.

| Permission | Why |
|---|---|
| `INTERNET` | Medicine cards, interaction check, policy summary and questions, family sync |
| `ACCESS_NETWORK_STATE` | Added by libraries (WorkManager, Firebase) to see whether the network is up before a background sync |
| `POST_NOTIFICATIONS` | Dose reminders with Taken and Snooze buttons, missed-dose and refill alerts |
| `SCHEDULE_EXACT_ALARM` | Reminders on the minute. Without it the app falls back to inexact alarms and the health check shows red |
| `RECEIVE_BOOT_COMPLETED` | Re-arm reminders after a restart (also after an app update and a time or time-zone change) |
| `USE_FULL_SCREEN_INTENT` | Declared for the urgent dose alert channel. If no shipped feature uses it, remove it before release |
| `CAMERA` | Photograph a medicine strip or an insurance paper. Optional: gallery pick works without it. Photos are read on the phone and deleted after reading |
| `WAKE_LOCK` | Added by WorkManager so a background job can finish |
| `FOREGROUND_SERVICE` | Added by WorkManager for long background jobs. CareCircle starts no foreground service of its own |
| `com.carecircle.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added by AndroidX so receivers registered at run time stay private to the app |
| `com.google.android.apps.aicore.service.BIND_SERVICE` | Added by the ML Kit GenAI library for the experimental on-device AI check (Settings). Nothing leaves the phone |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | Added by Google Play services libraries |

Not requested: `RECORD_AUDIO` (voice entry uses the system speech service), contacts, location, storage.

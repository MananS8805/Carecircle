# Play Console: Data safety answers (draft)

Draft to be checked against the Play Console form and by a person who knows the policy rules. It follows what the app does today
(see `../privacy-policy.md`). If a network call is added, this file, the policy and `ui/PrivacyScreen.kt` change together.

| Question | Answer | Notes |
|---|---|---|
| Does the app collect or share data? | Collects: yes (limited, optional). Shares with third parties: no | Firebase and the CareCircle server act as service providers |
| Is all data encrypted in transit? | Yes | HTTPS to the backend and Firebase |
| Can users request deletion? | Yes | In app: Settings > Delete all my data; Family sync removal deletes shared data |
| Personal info: Name | Optional, off by default | First name of a shared person, only if the user turns on "Also share names" before creating a family code. Purpose: App functionality |
| Health and fitness: Health info | Collected, optional | Taken / missed / skipped status and dose times of a shared person (medicine names only with "Also share names"); generic ingredient names for medicine cards; policy clause text and questions processed without storage. Purpose: App functionality |
| Photos and videos | Not collected | Strip and policy photos are read on the device |
| Audio | Not collected by the app | Voice entry uses the system speech service; CareCircle receives text |
| Location, contacts, financial info, messages | Not collected | Caregiver numbers stay on the phone |
| Device or other IDs | Collected | Anonymous Firebase Auth ID and, for the family phone, a Firebase Cloud Messaging push token, only when family sync is used and push is on. Purpose: App functionality |
| Advertising, analytics | None | |
| Crash logs | None sent | The optional local crash log (when added) stays on the phone |

Data processed ephemerally (not stored, not logged) by the CareCircle server: insurance text, policy questions. Mark as
"processed ephemerally" where the form allows it.

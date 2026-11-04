# CareCircle privacy policy

_Draft for review. Have this reviewed by a lawyer before publishing it or linking it from a store listing. It describes what
the app does today and does not claim compliance with any particular law._

Contact: TODO (set `SUPPORT_EMAIL` in `gradle.properties`)  
Published at: TODO (set `PRIVACY_URL`; `npm run build:privacy` in `backend/` generates `public/privacy.html` from this file)

## The short version

CareCircle keeps your medicines, doses, notes and health readings on your phone. It sends a little information to a server
only when you use a feature that needs it, and the table below lists every case. Nothing is sold, there is no advertising and no
analytics.

CareCircle is informational. It is not medical advice and it is not an insurance claim decision.

## What stays on your phone

People and their names, medicines, doses and dose history, side-effect diary, allergies, health readings (blood pressure,
sugar, pulse, weight, temperature), appointments, refill history, saved insurance summaries, caregiver and pharmacy phone
numbers, settings, and the reminder refresh log. Photos of medicine strips and of insurance papers are read on the phone and
are not kept or uploaded.

## What can leave your phone, and when

| What | When | What is sent | Where it goes |
|---|---|---|---|
| `GET /api/medicine-info` | You open a medicine card | The generic ingredient name only (for example "paracetamol") | CareCircle server, which reads public sources (RxNorm, openFDA label, MedlinePlus) and may rewrite the label text with an AI provider. No name of yours or of the person is sent |
| `GET /api/interactions` | You run the interaction check | The generic ingredient names of the selected medicines | CareCircle server (same public sources) |
| `POST /api/policy-summary` | You add an insurance policy, after a consent screen shown every time | The text read from the policy pages | CareCircle server, which may use an AI provider to find items. Not stored, not logged |
| `POST /api/policy-question` | You ask about your policy, after a consent screen shown every time | Your question and the saved clause quotes (page, clause number, quote) | CareCircle server, which may use an AI provider. Not stored, not logged |
| Family sync (Firebase) | Only if you create a code to share a person, or enter a code to follow someone | An anonymous sign-in. By default only: taken / missed / skipped, the time of each dose and daily counts. A first name and medicine names are sent only if you turn on "Also share names". Entering five wrong codes locks code entry for 15 minutes | Google Firebase (Authentication and Firestore). Stopping sharing deletes it |
| `POST /api/notify-missed` and push | Only while family sync is on, when a dose is missed on the shared phone | From the shared phone: the link code, a dose id and the dose time (for example "8:00 AM"), with a Firebase sign-in token. To the family phone: a data-only push with the type, the dose time and the link code. The family phone writes its push token to the link so it can be reached, and can turn this off | CareCircle server (Firebase Admin) and Google Firebase Cloud Messaging. Nothing is stored or logged by the server; no names or medicine names are included |
| Voice entry | You tap the microphone button | Nothing from CareCircle. Your phone's speech service listens and may process audio online under its own policy. CareCircle only receives the text | Your phone's speech service |
| WhatsApp, SMS and sharing | You tap a message or share button | A message you can read and edit first | The app you pick. CareCircle never sends it by itself |

## What the server logs

For each request: the endpoint name, the status, how long it took, whether the answer came from cache and token counts. It does not
log what you sent, medicine names, questions, document text or IP addresses. Network providers and Vercel keep their own
infrastructure logs under their policies.

## What is never sent

Photos, caregiver and pharmacy phone numbers, allergies, health readings, appointments, notes, refill history, and the contents of
a backup file.

## Your controls

- Settings > Delete all my data removes everything on the phone, stops sharing, and cancels reminders.
- Remove a person from Family sync to delete what was shared about them.
- Export a backup, or restore one, from Settings. A backup stays in the place you save it, and you can lock it with a password (a lost password cannot be recovered).
- Each upload of insurance text or a policy question needs your confirmation on a screen that says what will be sent.

## Children

CareCircle is meant for adults managing medicines for themselves or for family members.

## Changes

If this policy changes, the new version is published at the address above and the app's Privacy page is updated in the same release.

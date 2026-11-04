# Firebase setup for caregiver sync

1. Firebase console, project `carecircle-c908b`: enable **Authentication, Anonymous** and create **Firestore** (production mode).
2. Open **Firestore Database, Rules**, paste the contents of `firestore.rules` and **Publish**.
3. Put `google-services.json` in `app/` (it is git-ignored). Without it the app still builds and Family sync shows "not set up".

How it works: the patient phone writes a pairing document whose id is a short code (valid 15 minutes). A family phone reads it and claims it. After that the patient phone writes small event documents (taken, missed, skipped, daily summary) and the family phone reads them live, or every ~15 minutes in the background. "Stop sharing" or "Stop following" deletes the pairing; the patient phone also deletes the events.

Push when the family app is closed without the 15-minute delay needs a Cloud Function (Blaze plan). Not included.

## Names, codes and tests (v1.7)

By default the patient phone sends no person name and no medicine name: events carry the time of the dose, and the status carries counts and the next dose time. The `Also share names` switch in Family sync adds them. Five wrong codes on the family phone lock code entry for 15 minutes. The rules did not change in v1.7.

Test the rules: `cd firebase/rules-test && npm ci && npm test` (needs Java; it starts the Firestore emulator).

Differences from the first design brief: the invite code is the pairing document id (get-only, no listing) rather than a hashed field, and it expires in 15 minutes rather than 24 hours. The per-day slot documents were not built; the family view uses events and the status document.

## Push (v1.8)

The family phone writes its Firebase Cloud Messaging token to `pairings/{code}.caregiverFcmToken` (with `tokenUpdatedAt`); the rules let it write only those two fields on a link it claimed. The parent phone calls `/api/notify-missed`; the server verifies the parent's ID token with the Firebase Admin SDK, checks the link, and sends a data-only push. Setting it up:

1. Firebase console, Project settings, Service accounts: generate a private key. Put the JSON in the Vercel variable `FIREBASE_SERVICE_ACCOUNT_JSON` (never in the repository).
2. **Publish the new `firestore.rules`** (they gained the push-token rule).
3. Cloud Messaging needs no extra setup for data messages. Android 13 or later also needs the notification permission on the family phone.

No Cloud Function and no Blaze plan are needed for this path; the Vercel backend does the sending.

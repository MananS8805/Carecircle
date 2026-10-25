# Firebase setup for caregiver sync

1. Firebase console, project `carecircle-c908b`: enable **Authentication, Anonymous** and create **Firestore** (production mode).
2. Open **Firestore Database, Rules**, paste the contents of `firestore.rules` and **Publish**.
3. Put `google-services.json` in `app/` (it is git-ignored). Without it the app still builds and Family sync shows "not set up".

How it works: the patient phone writes a pairing document whose id is a short code (valid 15 minutes). A family phone reads it and claims it. After that the patient phone writes small event documents (taken, missed, skipped, daily summary) and the family phone reads them live, or every ~15 minutes in the background. "Stop sharing" or "Stop following" deletes the pairing; the patient phone also deletes the events.

Push when the family app is closed without the 15-minute delay needs a Cloud Function (Blaze plan). Not included.

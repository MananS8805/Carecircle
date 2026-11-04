# Release checklist

1. `cd backend && npm ci && npm test && npm run typecheck && npm run build:privacy`
2. `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`
3. Set `SUPPORT_EMAIL` and `PRIVACY_URL` in `gradle.properties`; publish `backend/public/privacy.html` and use that address.
4. Have a lawyer review `docs/privacy-policy.md` and the Data safety answers.
5. Have a native speaker review the Hindi and Gujarati strings, store text and the consent screens.
6. Upgrade-install over the previous version on a phone: data is kept, reminders still fire.
7. Settings > Delete all my data leaves no alarms and an empty widget.
8. TalkBack, Extra large text and high contrast pass on the new screens, in Hindi and Gujarati.
9. Signed bundle: see `signing.md` (phase D).

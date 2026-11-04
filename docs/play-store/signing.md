# Signing and releasing

## One-time: make the upload key

```
keytool -genkeypair -v -keystore carecircle-upload.jks -alias carecircle -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` and its passwords somewhere safe and offline (a password manager). **Never commit it**: `.gitignore` blocks
`*.jks` and `*.keystore`, and CI fails if one is tracked. Enroll in Play App Signing so the key you hold is only an upload key
that Google can reset.

## Local signed build

Put these in `~/.gradle/gradle.properties` (not in the repository), or export them as environment variables with the upper-case names:

```
careCircleKeystore=C:/path/to/carecircle-upload.jks
careCircleKeystorePassword=...
careCircleKeyAlias=carecircle
careCircleKeyPassword=...
careCircleApi=https://YOUR-APP.vercel.app/
careCircleAppKey=...
careCircleSupportEmail=...
careCirclePrivacyUrl=https://YOUR-APP.vercel.app/privacy.html
```

| Gradle property | Environment variable |
|---|---|
| `careCircleKeystore` | `CARECIRCLE_KEYSTORE` (path) |
| `careCircleKeystorePassword` | `CARECIRCLE_KEYSTORE_PASSWORD` |
| `careCircleKeyAlias` | `CARECIRCLE_KEY_ALIAS` |
| `careCircleKeyPassword` | `CARECIRCLE_KEY_PASSWORD` |

Without them the release build still works but is **unsigned**, and Gradle prints a warning. Then run `scripts/check-release.sh`.

## Version

Edit `carecircle.versionCode` (always up) and `carecircle.versionName` in `gradle.properties`, nothing else.

## Releasing from GitHub

1. Add repository secrets: `CARECIRCLE_KEYSTORE_B64` (`base64 -w0 carecircle-upload.jks`), `CARECIRCLE_KEYSTORE_PASSWORD`,
   `CARECIRCLE_KEY_ALIAS`, `CARECIRCLE_KEY_PASSWORD`, and optionally `GOOGLE_SERVICES_B64` (the Firebase config, base64).
2. Push a tag such as `v1.7`. The `Android` workflow runs the checks, builds the signed bundle, runs `scripts/check-release.sh`
   and uploads `app-release.aab` and `mapping.txt` as a workflow artifact.
3. Upload the `.aab` in the Play Console, and upload `mapping.txt` with it so crash traces can be read.

Pull requests and branches never use the secrets.

## R8

Release builds are minified and shrunk. The rules are in `app/proguard-rules.pro` and are deliberately narrow. If a release
build crashes on something the debug build does not, look for a reflection-based library first, and add the smallest rule that
fixes it. The first release should be smoke-tested on a phone before it goes anywhere.

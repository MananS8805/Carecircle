#!/usr/bin/env bash
# Builds the release bundle and checks what Play will see. Run from anywhere; needs a JDK and the Android SDK, and unzip.
#
#   scripts/check-release.sh            build, then check
#   scripts/check-release.sh --no-build check what is already built
#
# Checks: the bundle and R8 mapping file exist, the app is not debuggable, the permissions are exactly those listed in
# docs/play-store/permissions-justification.md, no secrets or Firebase files are inside the bundle, and no debug-only reset
# hook (phase E) leaked into release.
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0
bad() { echo "FAIL: $*" >&2; fail=1; }
ok() { echo "ok:   $*"; }

if [ "${1:-}" != "--no-build" ]; then
  ./gradlew :app:bundleRelease :app:processReleaseMainManifest --console=plain -q
fi

aab=app/build/outputs/bundle/release/app-release.aab
mapping=app/build/outputs/mapping/release/mapping.txt
manifest=$(find app/build/intermediates -path "*merged_manifests/release*" -name AndroidManifest.xml | head -1)

[ -f "$aab" ] && ok "bundle exists ($aab)" || bad "no bundle at $aab"
[ -s "$mapping" ] && ok "R8 mapping file present" || bad "no mapping file: the release was not minified"
[ -n "$manifest" ] || { bad "merged release manifest not found"; exit 1; }

if grep -q 'android:debuggable="true"' "$manifest"; then bad "release manifest is debuggable"; else ok "not debuggable"; fi

# Permissions: manifest versus the documented list (first backticked word of each table row that looks like a permission).
actual=$(grep -o 'uses-permission[^/]*name="[^"]*"' "$manifest" | sed 's/.*name="//;s/"$//' | sort -u)
documented=$(grep -oE '^\| `[A-Za-z0-9_.]+`' docs/play-store/permissions-justification.md | sed 's/^| `//;s/`$//' | sort -u)
actual_short=$(echo "$actual" | sed 's/^android\.permission\.//')
documented_short=$(echo "$documented" | sed 's/^android\.permission\.//')
extra=$(comm -23 <(echo "$actual_short") <(echo "$documented_short") || true)
missing=$(comm -13 <(echo "$actual_short") <(echo "$documented_short") || true)
[ -z "$extra" ] && ok "every manifest permission is documented" || bad "permissions in the manifest but not in docs/play-store/permissions-justification.md: $(echo $extra)"
[ -z "$missing" ] && ok "no documented permission is missing from the manifest" || bad "documented but not in the manifest: $(echo $missing)"

# Nothing secret inside the bundle.
if [ -f "$aab" ]; then
  leaks=$(unzip -l "$aab" | grep -iE 'google-services\.json|(^|/)\.env|\.jks|\.keystore|service-account' || true)
  [ -z "$leaks" ] && ok "no google-services.json, .env or keystore inside the bundle" || bad "secret-looking files in the bundle: $leaks"
  # The debug reset hook must be absent from release (class name is fixed by phase E).
  if unzip -p "$aab" 'base/dex/*.dex' 2>/dev/null | grep -aq 'DebugReset'; then bad "DebugReset hook found in a release dex"; else ok "no debug reset hook in release"; fi
fi

# google-services.json must never be tracked.
if git ls-files --error-unmatch app/google-services.json >/dev/null 2>&1; then bad "app/google-services.json is tracked by git"; else ok "google-services.json is not tracked"; fi

exit $fail

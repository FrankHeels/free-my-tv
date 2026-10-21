#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the test device}"
ADB="${ADB:-adb}"
# Test-only package: every run starts clean and gets a fresh ephemeral test signature.
"$ADB" -s "$ANDROID_SERIAL" uninstall local.codex.continuetests >/dev/null 2>&1 || true
"$ADB" -s "$ANDROID_SERIAL" install --no-incremental -r out/android-tests/tests.apk
trap '"$ADB" -s "$ANDROID_SERIAL" uninstall local.codex.continuetests >/dev/null' EXIT
for phase in main persistence weather stock-home; do
  "$ADB" -s "$ANDROID_SERIAL" shell am force-stop local.codex.continuetests
  result="$("$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -e phase "$phase" local.codex.continuetests/io.freemytv.ContinueAndroidTest)"
  printf '%s\n' "$result"
  [[ "$result" == *"PASS:"* && "$result" != *"FAIL:"* ]] || exit 1
done

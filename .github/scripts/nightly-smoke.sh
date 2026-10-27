#!/usr/bin/env bash
# Keep the emulator alive while collecting screenshots and diagnostics.
set -uo pipefail
mkdir -p nightly-output/screenshots
./gradlew --no-daemon connectedDebugAndroidTest
test_exit=$?

for screen in first-frame notes homework; do
  adb pull "/sdcard/Download/nightly-${screen}.png" "nightly-output/screenshots/nightly-${screen}.png" || true
done

adb logcat -d -s NightlySemantics:D '*:S' > nightly-output/compose-semantics.log || true
adb shell dumpsys activity activities > nightly-output/activity-state.txt || true
echo "Instrumentation test exit code: $test_exit"
exit "$test_exit"

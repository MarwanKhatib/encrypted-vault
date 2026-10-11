#!/bin/bash
# Device checks for the Gap 13 list (docs/DESIGN_v5.md). Runs on a booted emulator or phone with USB debugging.
#
#   scripts/device-checks.sh unit        instrumented DeviceChecksTest + existing suites
#   scripts/device-checks.sh drill-kill  kill the app mid-batch, relaunch, verify (Gap 13 item 1)
#   scripts/device-checks.sh drill-reboot reboot the device mid-batch, relaunch, verify (Gap 13 item 2)
#   scripts/device-checks.sh recents     release build: check the window is FLAG_SECURE (Gap 13 item 6)
#
# Needs: app-debug.apk and app-debug-androidTest.apk built (./gradlew assembleDebug assembleDebugAndroidTest).
# Optional samples, copied into the app cache when present: SAMPLE_MP4=<file>, SAMPLE_HEIC=<file>.
# Results and logs go to $OUT (default: build/device-checks).
set -u

REPO="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
PKG=com.example.encryptedvault
TEST_PKG=$PKG.test
RUNNER="$TEST_PKG/androidx.test.runner.AndroidJUnitRunner"
OUT="${OUT:-$REPO/build/device-checks}"
DEBUG_APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$REPO/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RELEASE_APK="$REPO/app/build/outputs/apk/release/app-release.apk"
mkdir -p "$OUT"

say() { echo "== $*"; }

wait_for_boot() {
  "$ADB" wait-for-device
  for _ in $(seq 1 120); do
    [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 3
  done
  echo "device did not finish booting"; return 1
}

install_debug() {
  "$ADB" install -r "$DEBUG_APK" >/dev/null && "$ADB" install -r "$TEST_APK" >/dev/null
}

copy_samples() {
  for pair in "SAMPLE_MP4:sample.mp4" "SAMPLE_HEIC:sample.heic"; do
    var="${pair%%:*}"; name="${pair##*:}"; src="${!var:-}"
    [ -n "$src" ] || continue
    "$ADB" push "$src" "/data/local/tmp/$name" >/dev/null
    "$ADB" shell run-as "$PKG" cp "/data/local/tmp/$name" "cache/$name"
  done
}

unit_checks() {
  say "instrumented checks (DeviceChecksTest, DatabaseMigrationTest, KeyHierarchyTest, PanicWipeTest, VaultDataSourceTest)"
  (cd "$REPO" && ./gradlew connectedDebugAndroidTest --console=plain) > "$OUT/connected.log" 2>&1
  grep -E "BUILD|FAILED" "$OUT/connected.log" | tail -3
  echo "  full log: $OUT/connected.log"
}

drill() {
  local mode="$1"
  say "kill drill ($mode): 150 photos in a batch chain"
  "$ADB" logcat -c
  "$ADB" shell am instrument -w -r -e killDrill true -e class "$PKG.KillMidBatchTest#phase1StartBatchThenHostKillsApp" "$RUNNER" > "$OUT/drill-phase1.txt" 2>&1 &
  local phase1=$!
  for _ in $(seq 1 150); do
    "$ADB" logcat -d -s KillMidBatch:I | grep -q "batch enqueued" && break
    sleep 2
  done
  sleep 2
  # Read the progress before the kill: logcat is lost on reboot, and a force-stop does not clear it.
  local last
  last="$("$ADB" logcat -d -s KillMidBatch:I | grep "progress:" | tail -1 | sed 's/.*progress:/progress:/')"
  say "state at the kill: ${last:-no progress line}"
  if [ "$mode" = "reboot" ]; then
    "$ADB" reboot
    wait "$phase1" 2>/dev/null
    wait_for_boot
  else
    "$ADB" shell am force-stop "$PKG"
    wait "$phase1"
  fi
  say "relaunch and verify"
  "$ADB" shell am instrument -w -r -e killDrill true -e class "$PKG.KillMidBatchTest#phase2VerifyAfterKill" "$RUNNER" > "$OUT/drill-phase2.txt" 2>&1
  grep -E "OK \(|FAILURES|stack=" "$OUT/drill-phase2.txt" | head -5
}

flows() {
  say "trim and backup flows (TrimAndBackupFlowTest)"
  "$ADB" shell am instrument -w -r -e class "$PKG.TrimAndBackupFlowTest" "$RUNNER" > "$OUT/flows.txt" 2>&1
  grep -E "OK \(|FAILURES|stack=|Tests run" "$OUT/flows.txt" | head -10
  echo "  full log: $OUT/flows.txt"
}

recents() {
  say "release build: window must be FLAG_SECURE so recents shows no content"
  [ -f "$RELEASE_APK" ] || { echo "  build first: ./gradlew assembleRelease"; return 1; }
  echo "  note: this replaces the debug install and erases its vault (release is signed with another key)"
  "$ADB" uninstall "$PKG" >/dev/null 2>&1
  "$ADB" install -r "$RELEASE_APK" >/dev/null
  "$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
  sleep 4
  "$ADB" shell input keyevent KEYCODE_APP_SWITCH
  sleep 2
  "$ADB" exec-out screencap -p > "$OUT/recents.png"
  "$ADB" shell input keyevent KEYCODE_HOME
  echo "  screenshot: $OUT/recents.png (should be black or blank where the app card is)"
  "$ADB" shell dumpsys window | grep -c "FLAG_SECURE" | sed 's/^/  FLAG_SECURE lines in window dump: /'
}

case "${1:-}" in
  unit) wait_for_boot && install_debug && copy_samples && unit_checks ;;
  drill-kill) wait_for_boot && install_debug && copy_samples && drill kill ;;
  drill-reboot) wait_for_boot && install_debug && copy_samples && drill reboot ;;
  recents) wait_for_boot && recents ;;
  flows) wait_for_boot && install_debug && copy_samples && flows ;;
  *) sed -n '2,11p' "$0"; exit 2 ;;
esac

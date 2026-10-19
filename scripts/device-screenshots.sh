#!/usr/bin/env bash
# Saves a screenshot for every marker the device end-to-end tests log while they run
# (log tag LoopE2eScreenshot, see saveScreenshot in app/src/androidTest/.../harness/Device.kt):
#
#   scripts/device-screenshots.sh <output-dir> &
#   ./gradlew :app:connectedDebugAndroidTest
#   kill %1
#
# On an emulator the picture comes from the emulator itself (adb emu screenrecord screenshot),
# which is instant and costs the guest nothing; on a phone from screencap. ANDROID_SERIAL
# picks the device, as for adb. While this runs, the tests wait for each screenshot: it
# confirms them in /data/local/tmp/loop-e2e-screenshots on the device.
set -euo pipefail

out="${1:?usage: $0 <output-dir>}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
ack_dir=/data/local/tmp/loop-e2e-screenshots

if adb get-serialno | grep -q '^emulator-'; then
  capture() { adb emu screenrecord screenshot "$1" >/dev/null; }
else
  capture() { adb exec-out screencap -p >"$1"; }
fi

# World-writable: the tests (as the shell user) clear old confirmations, also when adb runs as root.
adb shell "rm -rf $ack_dir && mkdir -p $ack_dir && chmod 777 $ack_dir && touch $ack_dir/.watching"
trap 'adb shell rm -rf "$ack_dir" >/dev/null 2>&1 || true' EXIT

# Only markers logged from now on (-T in seconds since the epoch: adb splits arguments at spaces).
since="$(adb shell date +%s | tr -d '\r').000"
adb logcat -T "$since" -v raw -s LoopE2eScreenshot:I | while IFS= read -r name; do
  name="${name%$'\r'}"
  # Skip logcat's "--------- beginning of ..." lines and anything that is not a plain file name.
  case "$name" in '' | -* | */* | .*) continue ;; esac
  capture "$out/$name.png"
  adb shell touch "$ack_dir/$name"
  echo "saved $out/$name.png"
done

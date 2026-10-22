#!/usr/bin/env bash
# Installs the watch face on a running Wear OS emulator, makes it the active face and saves an
# interactive and an ambient screenshot. Pick the emulator with ANDROID_SERIAL when several run.
#
#   bash scripts/emulator-smoke.sh <apk> <screenshot-dir>
set -euo pipefail

APK="${1:?usage: emulator-smoke.sh <apk> <screenshot-dir>}"
OUT="${2:?usage: emulator-smoke.sh <apk> <screenshot-dir>}"
PACKAGE="dev.kadirozturk.watchface.compiletime"
mkdir -p "$OUT"

# shellcheck disable=SC2016 # the watch's shell expands it
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
adb logcat -c
adb install -r "$APK" >/dev/null
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation set-watchface --es watchFaceId "$PACKAGE" >/dev/null

# Emulators boot on a virtual charger, and the charging screen covers the watch face.
adb emu power ac off >/dev/null
adb shell dumpsys battery unplug

adb shell input keyevent KEYCODE_WAKEUP

# A fresh emulator plays a welcome animation first; wait until the watch face itself is on screen.
for _ in $(seq 30); do
  window="$(adb shell 'rm -f /sdcard/window.xml; uiautomator dump /sdcard/window.xml >/dev/null 2>&1; cat /sdcard/window.xml 2>/dev/null' || true)"
  [[ "$window" == *'content-desc="Watch face'* ]] && break
  sleep 2
done
sleep 2

# Logs go through a file: grep -q exiting early would kill adb with SIGPIPE and fail the pipeline.
log="$OUT/logcat.txt"
adb logcat -d > "$log"
if ! grep -q "Preparing WatchFaceDocument object for package: $PACKAGE" "$log"; then
  grep "dwf version of $PACKAGE" "$log" | tail -1 >&2 || true
  echo "The watch face runtime did not load $PACKAGE." >&2
  exit 1
fi
adb exec-out screencap -p > "$OUT/interactive.png"

adb shell input keyevent KEYCODE_SLEEP
sleep 3
adb exec-out screencap -p > "$OUT/ambient.png"
adb shell input keyevent KEYCODE_WAKEUP

# The runtime logs "Path is empty or null" for every face with a ColorConfiguration.
errors="$OUT/logcat-errors.txt"
adb logcat -d '*:E' > "$errors"
if grep 'DWF:' "$errors" | grep -v 'Path is empty or null'; then
  echo "The watch face runtime reported errors (above)." >&2
  exit 1
fi
echo "Screenshots: $OUT/interactive.png $OUT/ambient.png"

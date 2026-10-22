#!/usr/bin/env bash
# Checks both release builds the way Google Play does: each watch face XML against its Watch Face Format schema,
# each APK against the memory limits, and that no APK carries code. Run after ./gradlew assembleRelease.
#
#   bash scripts/validate.sh
set -euo pipefail

# Google rebuilds its downloadable jars in place, so the tools are built from a pinned google/watchface commit.
WATCHFACE_COMMIT=d1cdafa953c95d5554fc54df4bc3948befbefc4d

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/build/wff-tools/$WATCHFACE_COMMIT"
VALIDATOR="$TOOLS/wff-validator.jar"
FOOTPRINT="$TOOLS/memory-footprint.jar"

if [[ ! -f "$VALIDATOR" || ! -f "$FOOTPRINT" ]]; then
  # The pinned tools build with Gradle 8.9, which runs on JDK 17 to 22.
  java_major="$("${JAVA_HOME:+$JAVA_HOME/bin/}java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
  if (( ${java_major%%.*} < 17 || ${java_major%%.*} > 22 )); then
    echo "Building Google's validators needs JDK 17 to 22; found JDK $java_major." >&2
    exit 1
  fi
  src="$TOOLS/src"
  rm -rf "$src"
  git init -q "$src"
  git -C "$src" fetch -q --depth 1 https://github.com/google/watchface.git "$WATCHFACE_COMMIT"
  git -C "$src" checkout -q FETCH_HEAD
  (cd "$src/play-validations" && ./gradlew -q memory-footprint:executable-jar validator:executable-jar)
  cp "$src/third_party/wff/specification/validator/build/libs/wff-validator.jar" "$VALIDATOR"
  cp "$src/play-validations/memory-footprint/build/libs/memory-footprint.jar" "$FOOTPRINT"
fi

for spec in WearOs6:wearOs6:4 WearOs4:wearOs4:1; do
  IFS=: read -r task flavor version <<<"$spec"
  xml="$ROOT/watchface/build/generated/res/generate${task}ReleaseWatchFace/raw/watchface.xml"
  apk="$ROOT/watchface/build/outputs/apk/$flavor/release/watchface-$flavor-release.apk"

  # Without --stop-on-fail the validator exits 0 on an invalid file.
  java -jar "$VALIDATOR" "$version" --stop-on-fail "$xml"
  java -jar "$FOOTPRINT" --watch-face "$apk"
  entries="$(unzip -Z1 "$apk")"
  if grep -q '\.dex$' <<<"$entries"; then
    echo "$apk contains code; a Watch Face Format APK must be resource-only." >&2
    exit 1
  fi
done

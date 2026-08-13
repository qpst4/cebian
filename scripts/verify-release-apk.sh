#!/usr/bin/env bash
# Verify release APK versionCode/versionName match app/build.gradle.kts.
set -euo pipefail

GRADLE_FILE="${2:-app/build.gradle.kts}"

if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "ERROR: Gradle file not found: $GRADLE_FILE" >&2
  exit 1
fi

expected_code="$(grep -E 'versionCode[[:space:]]*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/')"
expected_name="$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/')"

if [[ -z "$expected_code" || -z "$expected_name" ]]; then
  echo "ERROR: Failed to parse version from $GRADLE_FILE" >&2
  exit 1
fi

APK_PATH="${1:-app/build/outputs/apk/release/cebian-${expected_name}.apk}"

if [[ ! -f "$APK_PATH" ]]; then
  echo "ERROR: APK not found: $APK_PATH" >&2
  exit 1
fi

find_aapt() {
  local bases=(
    "${ANDROID_SDK_ROOT:-}/build-tools"
    "${ANDROID_HOME:-}/build-tools"
    "${HOME}/.android/sdk/build-tools"
    "${HOME}/Android/Sdk/build-tools"
  )
  local base aapt
  for base in "${bases[@]}"; do
    [[ -d "$base" ]] || continue
    aapt="$(find "$base" -name aapt -type f 2>/dev/null | sort -V | tail -1)"
    if [[ -n "$aapt" ]]; then
      echo "$aapt"
      return 0
    fi
  done
  return 1
}

AAPT="$(find_aapt || true)"
if [[ -z "$AAPT" ]]; then
  echo "ERROR: aapt not found. Set ANDROID_SDK_ROOT or install Android build-tools." >&2
  exit 1
fi

badging="$("$AAPT" dump badging "$APK_PATH")"
actual_code="$(printf '%s\n' "$badging" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
actual_name="$(printf '%s\n' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"

if [[ -z "$actual_code" || -z "$actual_name" ]]; then
  echo "ERROR: Failed to parse APK version from: $APK_PATH" >&2
  exit 1
fi

echo "Expected: versionCode=$expected_code versionName=$expected_name"
echo "Actual:   versionCode=$actual_code versionName=$actual_name"

if [[ "$actual_code" != "$expected_code" || "$actual_name" != "$expected_name" ]]; then
  echo "ERROR: Release APK version mismatch." >&2
  exit 1
fi

echo "OK: Release APK version matches $GRADLE_FILE"

MIN_RELEASE_APK_BYTES="${MIN_RELEASE_APK_BYTES:-35000000}"
apk_bytes="$(wc -c < "$APK_PATH" | tr -d ' ')"
echo "APK size: $apk_bytes bytes (minimum $MIN_RELEASE_APK_BYTES)"
if [[ "$apk_bytes" -lt "$MIN_RELEASE_APK_BYTES" ]]; then
  echo "ERROR: Release APK too small; bundled native engine packs may be missing." >&2
  exit 1
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: unzip not found; required to verify bundled native engine assets." >&2
  exit 1
fi

required_packs=(ocr-engine translate-engine segmentation-engine)
for pack in "${required_packs[@]}"; do
  asset_path="assets/bundled-native-engine/${pack}.zip"
  if ! unzip -l "$APK_PATH" "$asset_path" >/dev/null 2>&1; then
    echo "ERROR: Missing bundled asset in APK: $asset_path" >&2
    exit 1
  fi
  echo "OK: Found $asset_path"
done

echo "OK: Release APK bundled native engine assets verified"

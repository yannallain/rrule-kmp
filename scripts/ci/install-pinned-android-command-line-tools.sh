#!/usr/bin/env bash

set -euo pipefail

readonly command_line_tools_revision="15859902"
readonly repository_base_url="https://dl.google.com/android/repository"

# Google's archive name retains a "latest" suffix, but the numeric revision and
# verified checksum below make the downloaded toolchain content immutable.
android_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$android_sdk_root" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must identify the Android SDK." >&2
  exit 1
fi

installation_directory="$android_sdk_root/cmdline-tools/$command_line_tools_revision"
installed_sdkmanager="$installation_directory/bin/sdkmanager"
if [[ -x "$installed_sdkmanager" ]]; then
  printf '%s\n' "$installed_sdkmanager"
  exit 0
fi
if [[ -e "$installation_directory" ]]; then
  echo "Android command-line tools installation is incomplete: $installation_directory" >&2
  exit 1
fi

case "$(uname -s):$(uname -m)" in
  Linux:x86_64)
    archive_name="commandlinetools-linux-${command_line_tools_revision}_latest.zip"
    expected_sha256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
    ;;
  Darwin:x86_64)
    archive_name="commandlinetools-mac_x86_64-${command_line_tools_revision}_latest.zip"
    expected_sha256="c5a6378ab5cf7e0d5701921405115befff13e9ff7417fb588389338f8bd050f3"
    ;;
  Darwin:arm64)
    archive_name="commandlinetools-mac_arm64-${command_line_tools_revision}_latest.zip"
    expected_sha256="835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e"
    ;;
  *)
    echo "No pinned Android command-line tools archive exists for this host." >&2
    exit 1
    ;;
esac

for required_command in curl unzip; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "Required command is unavailable: $required_command" >&2
    exit 1
  fi
done

temporary_directory="$(mktemp -d)"
cleanup() {
  rm -rf "$temporary_directory"
}
trap cleanup EXIT

archive="$temporary_directory/$archive_name"
extracted_directory="$temporary_directory/extracted"
curl \
  --fail \
  --location \
  --retry 3 \
  --silent \
  --show-error \
  --output "$archive" \
  "$repository_base_url/$archive_name"

if command -v sha256sum >/dev/null 2>&1; then
  actual_sha256="$(sha256sum "$archive" | awk '{ print $1 }')"
elif command -v shasum >/dev/null 2>&1; then
  actual_sha256="$(shasum -a 256 "$archive" | awk '{ print $1 }')"
else
  echo "A SHA-256 checksum command is required." >&2
  exit 1
fi
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  echo "Android command-line tools checksum verification failed." >&2
  exit 1
fi

unzip -q "$archive" -d "$extracted_directory"
extracted_tools="$extracted_directory/cmdline-tools"
if [[ ! -x "$extracted_tools/bin/sdkmanager" ]]; then
  echo "The Android command-line tools archive has an unexpected layout." >&2
  exit 1
fi

mkdir -p "$(dirname "$installation_directory")"
mv "$extracted_tools" "$installation_directory"
printf '%s\n' "$installed_sdkmanager"

#!/usr/bin/env bash

set -euo pipefail

readonly android_platform="platforms;android-36"
readonly android_build_tools="build-tools;36.0.0"
readonly android_platform_jar="platforms/android-36/android.jar"
readonly android_aapt2="build-tools/36.0.0/aapt2"

android_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$android_sdk_root" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must identify the Android SDK." >&2
  exit 1
fi

if [[ -f "$android_sdk_root/$android_platform_jar" ]] &&
  [[ -x "$android_sdk_root/$android_aapt2" ]]; then
  echo "Required Android SDK packages are already installed."
  exit 0
fi

sdkmanager="$android_sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" && -d "$android_sdk_root/cmdline-tools" ]]; then
  sdkmanager=""
  while IFS= read -r candidate; do
    if [[ -x "$candidate" ]]; then
      sdkmanager="$candidate"
      break
    fi
  done < <(
    find "$android_sdk_root/cmdline-tools" \
      -mindepth 3 \
      -maxdepth 3 \
      -type f \
      -name sdkmanager \
      -print |
      sort -Vr
  )
fi

if [[ ! -x "$sdkmanager" ]]; then
  script_directory="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &&
      pwd
  )"
  sdkmanager="$("$script_directory/install-pinned-android-command-line-tools.sh")"
fi

"$sdkmanager" \
  "--sdk_root=$android_sdk_root" \
  "$android_platform" \
  "$android_build_tools"

if [[ ! -f "$android_sdk_root/$android_platform_jar" ]] ||
  [[ ! -x "$android_sdk_root/$android_aapt2" ]]; then
  echo "Android SDK package installation completed without the required files." >&2
  exit 1
fi

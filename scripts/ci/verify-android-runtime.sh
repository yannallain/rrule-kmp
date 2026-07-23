#!/usr/bin/env bash

set -euo pipefail

run_phase() {
  local phase="$1"
  shift

  printf '::group::%s\n' "$phase"
  if "$@"; then
    printf '::endgroup::\n'
    return
  else
    local status=$?
    printf '::endgroup::\n'
    printf '::error title=%s::Command failed with exit code %s.\n' \
      "$phase" \
      "$status"
    return "$status"
  fi
}

run_phase \
  "Android device tests" \
  ./gradlew connectedAndroidDeviceTest

run_phase \
  "Publish Android consumer artifacts" \
  ./gradlew \
  -PPUBLICATION_GROUP=com.github.yannallain \
  -PVERSION_NAME=0.0.0 \
  publishKotlinMultiplatformPublicationToLocalBuildRepository \
  publishAndroidPublicationToLocalBuildRepository

run_phase \
  "Build minified Android consumer" \
  ./gradlew \
  -p integration-tests/android-minified-consumer \
  -PrruleGroup=com.github.yannallain \
  -PrruleVersion=0.0.0 \
  assembleRelease

run_phase \
  "Run minified Android consumer" \
  ./integration-tests/android-minified-consumer/verify-on-device.sh

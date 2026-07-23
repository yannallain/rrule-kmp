#!/usr/bin/env bash

set -euo pipefail

: "${GROUP:?JitPack must provide the publication group through GROUP.}"
: "${ARTIFACT:?JitPack must provide the repository name through ARTIFACT.}"
: "${VERSION:?JitPack must provide the requested version through VERSION.}"

script_directory="$(
  cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &&
    pwd
)"
repository_root="$(cd -- "$script_directory/../.." && pwd)"
readonly publication_group="$GROUP.$ARTIFACT"
readonly network_retry="$script_directory/run-with-network-retries.sh"

"$network_retry" "$script_directory/ensure-android-sdk-packages.sh"

exec "$network_retry" \
  "$repository_root/gradlew" \
  -p "$repository_root" \
  -PPUBLICATION_GROUP="$publication_group" \
  -PVERSION_NAME="$VERSION" \
  -PAPPLE_FRAMEWORK_VERSION=0.0.0 \
  :jvmTest \
  :testAndroidHostTest \
  :verifyEmbeddedLegalFiles \
  :checkKotlinAbi \
  :publishToMavenLocal \
  --no-configuration-cache

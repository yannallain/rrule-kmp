#!/usr/bin/env bash

set -euo pipefail

readonly release_version="${1:-}"

if [[ ! "$release_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    echo "Usage: $0 <major.minor.patch>" >&2
    exit 64
fi

readonly script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly repository_root="$(cd "$script_directory/../.." && pwd)"
readonly distribution_directory="$repository_root/build/distributions/$release_version"
readonly maven_archive="$distribution_directory/rrule-kmp-$release_version-maven-repository.zip"
readonly license_archive="$distribution_directory/rrule-kmp-$release_version-licenses.zip"
readonly verification_directory="$repository_root/build/maven-release-verification"

for required_tool in cmp grep mktemp shasum unzip; do
    if ! command -v "$required_tool" >/dev/null 2>&1; then
        echo "Required tool is unavailable: $required_tool" >&2
        exit 69
    fi
done

readonly temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/rrule-kmp-maven-release.XXXXXX")"

cleanup() {
    rm -rf -- "$temporary_directory"
}
trap cleanup EXIT

build_archives() {
    local publication_repository="$1"

    "$repository_root/gradlew" \
        --no-build-cache \
        --no-configuration-cache \
        --rerun-tasks \
        -PPUBLICATION_GROUP=com.github.yannallain \
        -PLOCAL_PUBLICATION_REPOSITORY="$publication_repository" \
        -PVERSION_NAME="$release_version" \
        :zipMavenPublications \
        :zipReleaseLicenses
}

mkdir -p "$verification_directory"

build_archives "$temporary_directory/first-repository"
cp "$maven_archive" "$temporary_directory/first-maven.zip"
cp "$license_archive" "$temporary_directory/first-licenses.zip"

build_archives "$temporary_directory/second-repository"

cmp "$temporary_directory/first-maven.zip" "$maven_archive"
cmp "$temporary_directory/first-licenses.zip" "$license_archive"

readonly archive_entries="$(unzip -Z1 "$maven_archive")"
if grep -q '/maven-metadata\.xml' <<< "$archive_entries"; then
    echo "The release archive contains mutable Maven metadata." >&2
    exit 1
fi

readonly -a required_entries=(
    "repository/com/github/yannallain/rrule-kmp/$release_version/rrule-kmp-$release_version.module"
    "repository/com/github/yannallain/rrule-kmp-android/$release_version/rrule-kmp-android-$release_version.aar"
    "repository/com/github/yannallain/rrule-kmp-jvm/$release_version/rrule-kmp-jvm-$release_version.jar"
    "repository/com/github/yannallain/rrule-kmp-iosarm64/$release_version/rrule-kmp-iosarm64-$release_version.klib"
    "repository/com/github/yannallain/rrule-kmp-iossimulatorarm64/$release_version/rrule-kmp-iossimulatorarm64-$release_version.klib"
    "repository/com/github/yannallain/rrule-kmp-iosx64/$release_version/rrule-kmp-iosx64-$release_version.klib"
    "legal/LICENSE"
    "legal/THIRD_PARTY_NOTICES"
)
for required_entry in "${required_entries[@]}"; do
    if ! grep -Fxq "$required_entry" <<< "$archive_entries"; then
        echo "The release archive is missing: $required_entry" >&2
        exit 1
    fi
done

shasum -a 256 "$maven_archive" "$license_archive" \
    | tee "$verification_directory/checksums.txt"

echo "Reproducible Maven release-candidate checks passed."
echo "Maven archive: $maven_archive"
echo "License archive: $license_archive"

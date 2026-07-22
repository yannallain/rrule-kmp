#!/usr/bin/env bash

set -euo pipefail

readonly release_version="${1:-}"
readonly simulator_udid="${IOS_SIMULATOR_UDID:-}"

if [[ ! "$release_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    echo "Usage: IOS_SIMULATOR_UDID=<udid> $0 <major.minor.patch>" >&2
    exit 64
fi
if [[ -z "$simulator_udid" ]]; then
    echo "IOS_SIMULATOR_UDID must identify an available iOS simulator." >&2
    exit 64
fi

readonly script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly repository_root="$(cd "$script_directory/.." && pwd)"
readonly verification_directory="$repository_root/build/native-ios-verification"
readonly package_directory="$repository_root/build/swift-package"
readonly xcframework_directory="$repository_root/apple/rrule-kit/build/normalizedXCFrameworks/release/RRuleKmpCore.xcframework"
readonly device_framework="$xcframework_directory/ios-arm64/RRuleKmpCore.framework"
readonly simulator_framework="$xcframework_directory/ios-arm64_x86_64-simulator/RRuleKmpCore.framework"
readonly archive="$repository_root/build/distributions/$release_version/RRuleKmpCore-$release_version.xcframework.zip"
readonly android_sdk_directory="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

for required_tool in cmp lipo otool plutil swift xcodebuild xcrun; do
    if ! command -v "$required_tool" >/dev/null 2>&1; then
        echo "Required tool is unavailable: $required_tool" >&2
        exit 69
    fi
done

if ! xcrun simctl list devices available | awk -v simulator_udid="$simulator_udid" '
    index($0, "(" simulator_udid ")") > 0 { found = 1 }
    END { exit found ? 0 : 1 }
'; then
    echo "IOS_SIMULATOR_UDID is not an available simulator: $simulator_udid" >&2
    exit 66
fi

assert_equal() {
    local expected="$1"
    local actual="$2"
    local subject="$3"
    if [[ "$actual" != "$expected" ]]; then
        echo "$subject: expected '$expected', received '$actual'" >&2
        exit 1
    fi
}

framework_value() {
    local key="$1"
    local framework="$2"
    plutil -extract "$key" raw -o - "$framework/Info.plist"
}

build_version() {
    local architecture="$1"
    local binary="$2"
    otool -arch "$architecture" -l "$binary" | awk '
        $1 == "cmd" && $2 == "LC_BUILD_VERSION" {
            reading_build_version = 1
            platform = ""
            next
        }
        reading_build_version && $1 == "platform" {
            platform = $2
            next
        }
        reading_build_version && $1 == "minos" {
            print platform " " $2
            reading_build_version = 0
        }
    ' | sort -u
}

cd "$repository_root"

ANDROID_HOME="$android_sdk_directory" \
ANDROID_SDK_ROOT="$android_sdk_directory" \
    ./gradlew \
        clean \
        check \
        checkKotlinAbi \
        :apple:rrule-kit:iosSimulatorArm64Test \
        :apple:rrule-kit:checkKotlinAbi

mkdir -p "$verification_directory"

./gradlew --no-build-cache --rerun-tasks -PVERSION_NAME="$release_version" \
    :apple:rrule-kit:prepareLocalSwiftPackage \
    :apple:rrule-kit:zipRRuleKmpCoreReleaseXCFramework

if [[ ! -d "$package_directory/Artifacts/RRuleKmpCore.xcframework" ]]; then
    echo "Prepared Swift package is missing RRuleKmpCore.xcframework" >&2
    exit 1
fi
if [[ ! -f "$package_directory/LICENSE" ]]; then
    echo "Prepared Swift package is missing the project LICENSE" >&2
    exit 1
fi
cmp "$repository_root/LICENSE" "$package_directory/LICENSE"
if [[ ! -f "$package_directory/THIRD_PARTY_NOTICES" ]]; then
    echo "Prepared Swift package is missing third-party notices" >&2
    exit 1
fi
cmp "$repository_root/THIRD_PARTY_NOTICES" "$package_directory/THIRD_PARTY_NOTICES"
if [[ ! -d "$package_directory/LICENSES" ]]; then
    echo "Prepared Swift package is missing binary dependency licences" >&2
    exit 1
fi
diff -r "$repository_root/LICENSES" "$package_directory/LICENSES"
forbidden_package_path="$(find "$package_directory" \
    \( -name .build -o -name .swiftpm -o -name .DS_Store -o -name '*.xcresult' \
       -o -name '*.xcworkspace' -o -name DerivedData \) \
    -print -quit)"
if [[ -n "$forbidden_package_path" ]]; then
    echo "Prepared Swift package contains local or generated workspace state: $forbidden_package_path" >&2
    exit 1
fi
unexpected_artifact="$(find "$package_directory/Artifacts" -mindepth 1 -maxdepth 1 \
    ! -name RRuleKmpCore.xcframework -print -quit)"
if [[ -n "$unexpected_artifact" ]]; then
    echo "Prepared Swift package contains an unexpected binary artifact: $unexpected_artifact" >&2
    exit 1
fi

cp "$archive" "$verification_directory/first.xcframework.zip"

./gradlew --no-build-cache --rerun-tasks -PVERSION_NAME="$release_version" \
    :apple:rrule-kit:zipRRuleKmpCoreReleaseXCFramework

cmp "$verification_directory/first.xcframework.zip" "$archive"

swift package --package-path "$package_directory" dump-package \
    > "$verification_directory/package.json"

(
    cd "$package_directory"
    xcodebuild test \
        -scheme RRuleKit \
        -destination "platform=iOS Simulator,id=$simulator_udid" \
        -derivedDataPath "$verification_directory/simulator-tests" \
        CODE_SIGNING_ALLOWED=NO

    xcodebuild build \
        -scheme RRuleKit \
        -configuration Release \
        -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$verification_directory/simulator-build" \
        'ARCHS=arm64 x86_64' \
        ONLY_ACTIVE_ARCH=NO \
        CODE_SIGNING_ALLOWED=NO

    xcodebuild build-for-testing \
        -scheme RRuleKit \
        -configuration Release \
        -destination 'generic/platform=iOS' \
        -derivedDataPath "$verification_directory/device-build" \
        CODE_SIGNING_ALLOWED=NO
)

plutil -lint "$xcframework_directory/Info.plist" >/dev/null
for framework in "$device_framework" "$simulator_framework"; do
    assert_equal "$release_version" "$(framework_value CFBundleShortVersionString "$framework")" \
        "$framework CFBundleShortVersionString"
    assert_equal "$release_version" "$(framework_value CFBundleVersion "$framework")" \
        "$framework CFBundleVersion"
    assert_equal "13.0" "$(framework_value MinimumOSVersion "$framework")" \
        "$framework MinimumOSVersion"
done

lipo "$device_framework/RRuleKmpCore" -verify_arch arm64
lipo "$simulator_framework/RRuleKmpCore" -verify_arch arm64 x86_64

assert_equal "2 13.0" "$(build_version arm64 "$device_framework/RRuleKmpCore")" \
    "device arm64 build version"
assert_equal "7 14.0" "$(build_version arm64 "$simulator_framework/RRuleKmpCore")" \
    "simulator arm64 build version"
assert_equal "7 13.0" "$(build_version x86_64 "$simulator_framework/RRuleKmpCore")" \
    "simulator x86_64 build version"

swift package compute-checksum "$archive" | tee "$verification_directory/checksum.txt"

echo "Local native iOS release-candidate checks passed."
echo "A real iOS 13 runtime/device gate is still required before release."
echo "Archive: $archive"
echo "Verification output: $verification_directory"

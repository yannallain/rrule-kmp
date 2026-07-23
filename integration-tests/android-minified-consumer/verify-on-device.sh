#!/bin/sh

set -eu

script_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
apk="$script_directory/build/outputs/apk/release/rrule-kmp-android-minified-consumer-release.apk"

if [ -n "${ANDROID_HOME:-}" ]; then
    adb="$ANDROID_HOME/platform-tools/adb"
else
    adb=$(command -v adb)
fi

if [ ! -x "$adb" ]; then
    echo "adb was not found; set ANDROID_HOME or add adb to PATH" >&2
    exit 1
fi

if [ ! -f "$apk" ]; then
    echo "Release APK not found: $apk" >&2
    echo "Run assembleRelease before this script." >&2
    exit 1
fi

"$adb" install -r "$apk" >/dev/null
result=$("$adb" shell content call \
    --uri content://io.github.yallain.rrule.integration.shrinker.status \
    --method status)

case "$result" in
    *"status=ok"*)
        echo "Minified recurrence consumer passed."
        ;;
    *)
        echo "Minified recurrence consumer failed: $result" >&2
        exit 1
        ;;
esac

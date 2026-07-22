# Release checklist

The build can produce and locally validate every Kotlin Multiplatform publication. Project licence,
developer, and source-repository metadata are checked in; signing credentials and remote repository
configuration remain release-environment inputs.

## Verify a candidate

Run the native release-candidate gate first because it starts from a clean build. Then run Android
device checks, publish the candidate locally, and consume its Android AAR from the standalone
fully-minified fixture:

```shell
ANDROID_HOME=/path/to/android-sdk \
  IOS_SIMULATOR_UDID='<available-simulator-udid>' \
  ./apple/verify-native-ios-release-candidate.sh 0.1.0
ANDROID_HOME=/path/to/android-sdk ./gradlew connectedAndroidDeviceTest
ANDROID_HOME=/path/to/android-sdk ./gradlew -PVERSION_NAME=0.1.0 publishAllPublicationsToLocalBuildRepository
ANDROID_HOME=/path/to/android-sdk \
  ./gradlew -p integration-tests/android-minified-consumer \
  -PrruleVersion=0.1.0 assembleRelease
ANDROID_HOME=/path/to/android-sdk \
  ./integration-tests/android-minified-consumer/verify-on-device.sh
```

Inspect `build/repository/io/github/yallain/rrule-kmp/` and consume the generated version from a
representative JVM, Android, or iOS application before promoting it.

The protected release workflow should also consume the AAR with the oldest declared Android
toolchain (AGP 8.0, compile SDK 21), run the minified fixture on API 21, and run it again on a current
API. A local current-device run does not replace that release-floor compatibility job.

If a public API change is intentional, review it first and then regenerate the checked-in baseline
with `./gradlew updateKotlinAbi`. An unexplained ABI diff is a release blocker.

## Configure external publication

Before publishing outside this repository, the owner must supply and review:

- the final Maven namespace and artifact coordinates;
- the target Maven repository and credentials;
- artifact signing when required by that repository;
- a non-snapshot `VERSION_NAME` and an immutable source tag;
- the CI host and protected release workflow for the canonical remote repository.

Keep credentials outside the repository and inject them only into the protected release environment.

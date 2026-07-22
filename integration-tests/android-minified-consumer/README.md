# Android minified consumer

This standalone fixture validates the published Android variant as a real application dependency.
It deliberately uses an empty application ProGuard file, R8 full-mode optimization, resource
shrinking, and API 21 core-library desugaring. Provider invocation exercises recurrence-content
parsing, timezone resolution across a daylight-saving transition, recurrence-set inclusion and
exclusion, and conversion to instants.

Publish a candidate locally, build the minified application, and optionally run it on a connected
device or emulator:

```shell
ANDROID_HOME=/path/to/android-sdk \
  ./gradlew -PVERSION_NAME=0.1.0 publishAllPublicationsToLocalBuildRepository
ANDROID_HOME=/path/to/android-sdk \
  ./gradlew -p integration-tests/android-minified-consumer \
  -PrruleVersion=0.1.0 assembleRelease
ANDROID_HOME=/path/to/android-sdk \
  ./integration-tests/android-minified-consumer/verify-on-device.sh
```

The device probe installs only the production release APK and invokes its smoke-test provider. The
provider executes the scenario synchronously and returns the result. There is no test APK that could
accidentally keep the API under test or use a different core-library-desugaring map.

Set `rruleGroup` and `rruleRepositoryUrl` to point this same fixture at the
public JitPack coordinate. The post-release distribution workflow uses those
properties to verify the hosted Android variant without changing the smoke
contract.

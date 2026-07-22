# Native iOS integration

`rrule-kmp` has two deliberately separate Apple consumption paths:

- KMP applications depend on the root Kotlin Multiplatform artifact and use the complete Kotlin
  API.
- Pure Swift applications import the `RRuleKit` Swift Package. Its static `RRuleKmpCore` binary is
  an implementation detail behind a Foundation-native Swift facade.

The separation keeps Kotlin `Sequence`, sealed hierarchies, and `kotlinx-datetime` types out of the
supported Swift API. Once remote distribution is configured, it will also prevent a native iOS
application from needing Java, Gradle, or the Kotlin toolchain after consuming the binary package.

There is no remote Swift Package release today. The workflow below builds a generated local package
from a source checkout and requires macOS, Xcode, JDK 17+, and a configured Android SDK for the
repository's Gradle build. Run Gradle commands from the repository root.

## Local package development

Build the release XCFramework and assemble the local package:

```shell
./gradlew :apple:rrule-kit:prepareLocalSwiftPackage
```

In Xcode, choose **File → Add Package Dependencies → Add Local** and select the generated
`build/swift-package` directory. Add the `RRuleKit` product to the application target, then:

```swift
import Foundation
import RRuleKit

let schedule = try RecurrenceSchedule(
    content: """
    DTSTART;TZID=Europe/Paris:20170101T210000
    RRULE:FREQ=DAILY;WKST=MO
    """
)

let nightIntervals = try schedule.intervals(
    overlapping: attendanceStart..<attendanceEnd,
    elapsedDurationSeconds: 32_400,
    maximumCount: 100
)

let grossNightSeconds = nightIntervals.reduce(0) { total, interval in
    total + interval.overlapDuration(with: attendanceStart..<attendanceEnd)
}
```

`intervals` automatically queries far enough before the attendance start to include a night that
began earlier but still overlaps the attendance. Every end is the start instant plus exactly
32,400 seconds, including across daylight-saving transitions. `RecurrenceInterval` exposes
`start` and `endExclusive`, so exact-end adjacency remains unambiguously half-open. Returned values
are complete schedule intervals, not attendance-clipped values: use `intersection(with:)` or
`overlapDuration(with:)` before summing an attendance. Summing each interval's full `duration` can
overcount when the attendance contains only part of a night period.

Filtering payload entries by `tags.contains("night")`, subtracting policy-specific breaks, and
applying pricing rates remain application responsibilities. Multiple matching payload entries must
be parsed as separate `RecurrenceSchedule` instances when they represent separate rates. For a
single day-versus-night classification, union every overlapping night interval before
summing—including overlaps within one schedule when its duration exceeds its cadence and overlaps
across schedules. For rate calculation, keep the contributing schedules separate when premium
rates stack. Normalize overlapping breaks and clamp them to the attendance before subtraction.

The Swift facade also provides:

- bounded `occurrences(fromInclusive:toExclusive:maximumCount:)` queries;
- `nextOccurrence(after:inclusive:)` and `previousOccurrence(before:inclusive:)`;
- explicit earlier/later daylight-saving overlap selection;
- `timeZoneIdentifier` for a `TZID`-bearing `DTSTART`;
- `RRuleError` values that are safe to catch in Swift, with structured parse diagnostics.

A query may materialize at most `RecurrenceSchedule.maximumResultCount` values (currently 100,000).
Split larger time windows. Occurrence and interval queries fail with
`RRuleError.resultLimitExceeded` instead of silently returning an incomplete result. Invalid
content carries an `RRuleDiagnostic` with the reason, property name, rejected token, source
position, and original input whenever the core parser can provide them.

The instant-oriented facade rejects date-only and floating starts. KMP consumers can continue to
use those temporal domains through the complete Kotlin API.

## Verification from Swift

Regenerate the local package after changing Kotlin or Swift sources, then run its tests on an
installed simulator. Select a UDID from `xcrun simctl list devices available` rather than relying
on a model name that may exist in several runtimes:

```shell
./gradlew :apple:rrule-kit:prepareLocalSwiftPackage
export IOS_SIMULATOR_UDID='<available-simulator-udid>'
xcodebuild test \
  -scheme RRuleKit \
  -destination "platform=iOS Simulator,id=$IOS_SIMULATOR_UDID" \
  -derivedDataPath build/swift-test-derived-data \
  CODE_SIGNING_ALLOWED=NO
```

The checked-in Swift tests cover import/linking, `DTSTART`, `TZID`, `RDATE`, `EXDATE`, UTC and zoned
starts, Paris spring and fall DST, fractional bounds, exact elapsed duration, pre-window lookback,
half-open adjacency, overlap policy, catchable failures, safe limits, truncation detection,
concurrent queries, empty windows, and multiple independent schedules. A signing-free generic
device `build-for-testing` should also be part of release CI so the consumer test bundle is
final-linked for the device slice:

```shell
xcodebuild build-for-testing \
  -scheme RRuleKit \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build/swift-device-derived-data \
  CODE_SIGNING_ALLOWED=NO
```

The framework is compiled with a device deployment target of iOS 13 for older supported consumers.
The current Kotlin/Native toolchain normally targets iOS 14, so the bridge uses its explicit
lower-target override.
Release CI must retain a real iOS 13 runtime or device lane; compiling with iOS 13 metadata alone
does not replace runtime verification. Device arm64 and simulator x86_64 slices report iOS 13;
simulator arm64 reports iOS 14, matching the first Apple-silicon simulator generation.

## Remote Swift Package releases

Run the local native release-candidate gate with an available simulator. It performs the KMP and
ABI checks, Swift tests, release-mode simulator and device builds, framework metadata and
architecture validation, and two cache-free byte-for-byte archive builds:

```shell
IOS_SIMULATOR_UDID='<available-simulator-udid>' \
  ./apple/verify-native-ios-release-candidate.sh 0.1.0
```

This local gate does not certify iOS 13 runtime compatibility. The protected release workflow must
also pass on a real iOS 13 runtime/device before an artifact is promoted with an iOS 13 compatibility claim.

Create the deterministic XCFramework ZIP for a release version:

```shell
./gradlew -PVERSION_NAME=0.1.0 \
  :apple:rrule-kit:zipRRuleKmpCoreReleaseXCFramework

swift package compute-checksum \
  build/distributions/0.1.0/RRuleKmpCore-0.1.0.xcframework.zip
```

The checked-in manifest is intentionally a nested local-package fixture with a path-based binary
target; this repository root cannot currently be added as a remote Swift dependency. Before release,
choose either a dedicated Swift Package repository or a root package layout in this repository. Its
tag must put a remote-capable `Package.swift` at the package root, expose the `RRuleKit` facade
sources at the paths declared by that manifest, and carry the approved legal files.

Publish the exact XCFramework ZIP at an immutable HTTPS URL. The tagged manifest then combines the
facade sources with a remote binary target:

```swift
.binaryTarget(
    name: "RRuleKmpCore",
    url: "https://OWNER_HOST/RRuleKmpCore-0.1.0.xcframework.zip",
    checksum: "SWIFTPM_CHECKSUM"
)
```

The canonical package repository, binary host, release signing, and CI credentials are
owner-supplied release inputs and are intentionally not invented by this repository.
`prepareLocalSwiftPackage` and `zipRRuleKmpCoreReleaseXCFramework` produce build artifacts; they do
not assemble a legally complete release package. Release staging must carry this repository's root
[`LICENSE`](../LICENSE), [`THIRD_PARTY_NOTICES`](../THIRD_PARTY_NOTICES), and the required licence
texts and notices for the statically linked Kotlin/Native runtime and `kotlinx-datetime`. Gate their
presence and review a generated dependency-licence report before
publishing. Before a tag is promoted, resolve the remote package from a clean native application
and repeat simulator and generic-device builds.

The packaging follows the stable Objective-C framework/XCFramework route. Kotlin's direct Swift
export remains experimental and is not used for the production surface. See Kotlin's
[native binary](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html),
[Swift Package export](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-export.html), and
[Objective-C/Swift interop](https://kotlinlang.org/docs/native-objc-interop.html) documentation, plus
Apple's [binary Swift Package guidance](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages).

# Native iOS integration

`rrule-kmp` has two deliberately separate Apple consumption paths:

- KMP applications depend on the root Kotlin Multiplatform artifact and use the complete Kotlin
  API.
- Pure Swift applications import the `RRuleKit` Swift Package. Its static `RRuleKmpCore` binary is
  an implementation detail behind a Foundation-native Swift facade.

The separation keeps Kotlin `Sequence`, sealed hierarchies, and `kotlinx-datetime` types out of the
supported Swift API. A native iOS consumer needs only Xcode and Swift Package Manager: the Kotlin
implementation is delivered as a prebuilt XCFramework.

## Install with Swift Package Manager

### Xcode

Once `0.1.0` is published, add the package directly from GitHub:

1. Choose **File → Add Package Dependencies**.
2. Enter `https://github.com/yannallain/rrule-kmp.git`.
3. Select **Exact Version** and enter `0.1.0`.
4. Add the `RRuleKit` product to the application target.

Then import the facade:

```swift
import RRuleKit
```

The package automatically downloads the release's checksum-verified XCFramework. Consumer
applications do not need Java, Gradle, or a Kotlin toolchain.

### Package.swift

Swift packages can declare the same exact dependency in their manifest:

```swift
dependencies: [
    .package(
        url: "https://github.com/yannallain/rrule-kmp.git",
        exact: "0.1.0"
    ),
],
targets: [
    .target(
        name: "MyTarget",
        dependencies: [
            .product(name: "RRuleKit", package: "rrule-kmp"),
        ]
    ),
]
```

The exact version becomes resolvable only after both the Git tag and its GitHub Release asset are
published. The root [`Package.swift`](../Package.swift) is the public, remote-capable manifest. It
combines the checked-in Foundation-native facade sources with the immutable XCFramework ZIP attached
to that release.

## Local package development

Contributors can build the framework from source and use a generated local package. This path
requires macOS, Xcode, JDK 21, and a configured Android SDK. Run Gradle commands from the repository
root:

```shell
./gradlew :apple:rrule-kit:prepareLocalSwiftPackage
```

In Xcode, choose **File → Add Package Dependencies → Add Local** and select the generated
`build/swift-package` directory. Add the `RRuleKit` product to the application target, then:

```swift
import Foundation
import RRuleKit

let attendanceStart = Date(timeIntervalSince1970: 1_483_315_200)
let attendanceEnd = attendanceStart.addingTimeInterval(12 * 60 * 60)

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

The package declares iOS 13. The XCFramework contains device arm64 plus simulator arm64 and x86_64
architectures. Device arm64 and simulator x86_64 slices report iOS 13; simulator arm64 reports
iOS 14, matching the first Apple-silicon simulator generation.

The current release gate verifies compilation, linking, and metadata, but it does not run on a real
iOS 13 runtime or device. Until that lane exists, iOS 13 is a compile/link compatibility target, not
a fully runtime-certified claim. Publishing must not remove this caveat merely because the package
manifest and framework metadata declare iOS 13.

## Release packaging and publication

Run the local native release-candidate gate with an available simulator. It performs the KMP and
ABI checks, Swift tests, release-mode simulator and device builds, framework metadata and
architecture validation, and two cache-free byte-for-byte archive builds:

```shell
IOS_SIMULATOR_UDID='<available-simulator-udid>' \
  ./apple/verify-native-ios-release-candidate.sh 0.1.0
```

This local gate does not certify iOS 13 runtime compatibility. A real iOS 13 runtime or device lane
is still required before making an unqualified iOS 13 compatibility claim.

Create the deterministic XCFramework ZIP for a release version:

```shell
./gradlew -PVERSION_NAME=0.1.0 \
  :apple:rrule-kit:zipRRuleKmpCoreReleaseXCFramework

swift package compute-checksum \
  build/distributions/0.1.0/RRuleKmpCore-0.1.0.xcframework.zip
```

There are intentionally two Swift manifests:

- the root [`Package.swift`](../Package.swift) is the public manifest. Its binary target uses the
  versioned GitHub Release URL and exact checksum;
- [`apple/swift-package/Package.swift`](../apple/swift-package/Package.swift) is a development
  fixture. `prepareLocalSwiftPackage` copies it into `build/swift-package` with a path-based
  XCFramework, facade sources, tests, and legal files.

For each release, run the hosted Release candidate workflow on `main` first. Its Xcode 26.6 archive
and checksum are the authoritative inputs for the root manifest. A local archive built by another
Xcode version is useful as a preflight, but is not a substitute for the hosted candidate. For
`0.1.0`, the remote target is:

```swift
.binaryTarget(
    name: "RRuleKmpCore",
    url: "https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/RRuleKmpCore-0.1.0.xcframework.zip",
    checksum: "<hosted-candidate-checksum>"
)
```

The protected release flow is deliberately staged:

1. Dispatch the [Release candidate workflow](../.github/workflows/release-candidate.yml) for a
   numeric three-part version from `main`. Download its deterministic XCFramework archive and
   checksum produced with hosted Xcode 26.6.
2. Put that exact release URL and checksum in the root manifest, update versioned documentation,
   open a release-preparation pull request, and merge it only after every required CI job passes.
3. Dispatch the protected [Release workflow](../.github/workflows/release.yml) for the same version
   and green `main` commit. Its read-only build job reproduces and verifies every asset. Only its
   protected publish job can create the tag, attest the assets, and publish the GitHub Release.
   Review the staged artifact while that job waits for the `release` environment approval.
4. The protected job explicitly dispatches the
   [Distribution smoke workflow](../.github/workflows/distribution-smoke.yml) after publication.
   It resolves the exact tag and release asset from clean Swift Package Manager caches and builds
   generic simulator and device consumers.
5. Keep the real iOS 13 runtime/device result with the release evidence. Hosted compile, link, and
   metadata checks do not yet prove execution on iOS 13 itself.

The public release includes `RRuleKmpCore-<version>.xcframework.zip`, `SHA256SUMS`, the project
licence and third-party notices, and a complete licence archive. The generated local package also
carries [`LICENSE`](../LICENSE), [`THIRD_PARTY_NOTICES`](../THIRD_PARTY_NOTICES), and
[`LICENSES`](../LICENSES). These files provide the release inputs; their contents still require
the project owner's final review before approving publication.

The packaging follows the stable Objective-C framework/XCFramework route. Kotlin's direct Swift
export remains experimental and is not used for the production surface. See Kotlin's
[native binary](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html),
[Swift Package export](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-export.html), and
[Objective-C/Swift interop](https://kotlinlang.org/docs/native-objc-interop.html) documentation, plus
Apple's [binary Swift Package guidance](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages).

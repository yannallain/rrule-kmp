# Using rrule-kmp from native iOS

This guide is for pure Swift iOS applications. Kotlin Multiplatform applications should consume
the Kotlin dependency described in the [main README](../README.md#installation) and use the
complete Kotlin API.

Native applications import the `RRuleKit` Swift package. It exposes Foundation `Date` values,
Swift collections, bounded queries, and catchable errors while the shared recurrence engine is
delivered as a prebuilt static XCFramework. Normal consumers need only Xcode and Swift Package
Manager—no Java, Gradle, or Kotlin toolchain.

[Install](#install-with-swift-package-manager) · [Quick start](#quick-start) ·
[Supported input](#supported-input-and-limits) · [Platform support](#platform-support) ·
[Local development](#developing-rrulekit-locally)

## Install with Swift Package Manager

The examples pin `0.1.0`; use the version shown by the
[latest release](https://github.com/yannallain/rrule-kmp/releases/latest) for production builds.

### Xcode

1. Choose **File → Add Package Dependencies**.
2. Enter `https://github.com/yannallain/rrule-kmp.git`.
3. Select **Exact Version** and enter `0.1.0`.
4. Add the `RRuleKit` product to the application target.

Then import the module:

```swift
import RRuleKit
```

### Package.swift

Swift packages can declare the same exact dependency:

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

Swift Package Manager resolves the tag and downloads the checksum-verified XCFramework from its
matching GitHub Release. The root [`Package.swift`](../Package.swift) is the public manifest.

## Quick start

Create a schedule from recurrence content containing a UTC or `TZID`-bearing `DTSTART`, then query
absolute Foundation dates:

```swift
import Foundation
import RRuleKit

let schedule = try RecurrenceSchedule(
    content: """
    DTSTART;TZID=Europe/Paris:20260720T090000
    RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=6
    """
)

let iso8601 = ISO8601DateFormatter()
let windowStart = iso8601.date(from: "2026-07-20T00:00:00Z")!
let windowEnd = iso8601.date(from: "2026-07-27T00:00:00Z")!

let starts = try schedule.occurrences(
    fromInclusive: windowStart,
    toExclusive: windowEnd,
    maximumCount: 100
)

let next = try schedule.nextOccurrence(
    after: windowEnd,
    inclusive: false
)
```

`occurrences` returns every start in the half-open interval `[windowStart, windowEnd)`. It throws
instead of returning a partial collection when more than `maximumCount` values match.

### Fixed elapsed-duration intervals

When each occurrence represents a fixed amount of elapsed time, query the complete intervals that
overlap a window:

```swift
let oneHourSlots = try schedule.intervals(
    overlapping: windowStart..<windowEnd,
    elapsedDurationSeconds: 3_600,
    maximumCount: 100
)

let secondsInsideWindow = oneHourSlots.reduce(0) { total, interval in
    total + interval.overlapDuration(with: windowStart..<windowEnd)
}
```

Each `RecurrenceInterval` is half-open: `start..<endExclusive`. Its end is the start instant plus
exactly `elapsedDurationSeconds`, including across daylight-saving transitions. Returned intervals
retain their complete bounds; use `intersection(with:)` or `overlapDuration(with:)` when only the
portion inside a query window is needed.

## Supported input and limits

- `DTSTART` is required and must either be UTC (`Z`) or carry a `TZID`.
- The Swift facade is instant-oriented. Date-only and floating starts remain available through the
  complete Kotlin API but are rejected by `RecurrenceSchedule`.
- One content block supports at most one `RRULE` plus any supported `RDATE` and `EXDATE` lines. Use
  separate `RecurrenceSchedule` values for independent rules.
- Generated local times inside a daylight-saving gap are skipped. During an overlap,
  `AmbiguousTimePolicy.earlier` is the default; pass `.later` to select the second instant.
- `timeZoneIdentifier` exposes the `TZID` declared by `DTSTART`, or `nil` for a UTC start.
- Collection queries may materialize at most `RecurrenceSchedule.maximumResultCount` values
  (currently 100,000). Split larger windows.
- Invalid content and evaluation failures are reported as `RRuleError`. Parse failures include an
  `RRuleDiagnostic` with structured fields when the parser can provide them.

For example, select the later instant during a daylight-saving overlap:

```swift
let laterSchedule = try RecurrenceSchedule(
    content: """
    DTSTART;TZID=Europe/Paris:20261025T023000
    RRULE:FREQ=DAILY;COUNT=1
    """,
    ambiguousTimePolicy: .later
)
```

## Platform support

| Slice | Minimum reported iOS version |
| --- | --- |
| Device arm64 | iOS 13 |
| Simulator x86_64 | iOS 13 |
| Simulator arm64 | iOS 14 |

The package manifest declares iOS 13. The Apple-silicon simulator slice starts at iOS 14, matching
the first Apple-silicon simulator generation.

Current release gates verify compilation, linking, architectures, deployment metadata, Swift
tests, and generic simulator/device consumer builds. They do not execute on a real iOS 13 runtime
or device. Until that lane exists, iOS 13 is a compile/link compatibility target rather than a
runtime-certified claim.

## Developing RRuleKit locally

Local framework development requires macOS, Xcode, JDK 21, and a configured Android SDK. From the
repository root, generate the local package:

```shell
./gradlew :apple:rrule-kit:prepareLocalSwiftPackage
```

In Xcode, choose **File → Add Package Dependencies → Add Local**, select
`build/swift-package`, and add the `RRuleKit` product to the application target. Regenerate that
directory after changing Kotlin or Swift sources.

From the repository root, run the Swift tests against an installed simulator selected by UDID:

```shell
cd build/swift-package
export IOS_SIMULATOR_UDID='<available-simulator-udid>'
xcodebuild test \
  -scheme RRuleKit \
  -destination "platform=iOS Simulator,id=$IOS_SIMULATOR_UDID" \
  -derivedDataPath ../swift-test-derived-data \
  CODE_SIGNING_ALLOWED=NO
```

Use `xcrun simctl list devices available` to find a UDID. From the repository root, a signing-free
device link check is also available:

```shell
cd build/swift-package
xcodebuild build-for-testing \
  -scheme RRuleKit \
  -destination 'generic/platform=iOS' \
  -derivedDataPath ../swift-device-derived-data \
  CODE_SIGNING_ALLOWED=NO
```

The checked-in root [`Package.swift`](../Package.swift) points to the released binary. The
development fixture at
[`apple/swift-package/Package.swift`](../apple/swift-package/Package.swift) is copied into the
generated local package with a path-based XCFramework, facade sources, tests, and legal files.

Maintainers should use the [release guide](releasing.md) for deterministic XCFramework packaging,
hosted checksum preparation, protected publication, attestations, and clean remote-consumer
verification.

`RRuleKit` intentionally uses the stable Objective-C framework/XCFramework route. Kotlin's
[direct Swift export](https://kotlinlang.org/docs/native-swift-export.html) remains Alpha and
incomplete, so it is not part of the supported production surface.

# rrule-kmp

[![CI](https://github.com/yannallain/rrule-kmp/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/yannallain/rrule-kmp/actions/workflows/ci.yml)
[![JVM coverage](https://codecov.io/gh/yannallain/rrule-kmp/graph/badge.svg)](https://codecov.io/gh/yannallain/rrule-kmp)
[![Release](https://img.shields.io/github/v/release/yannallain/rrule-kmp?sort=semver)](https://github.com/yannallain/rrule-kmp/releases/latest)
[![JitPack](https://jitpack.io/v/yannallain/rrule-kmp.svg)](https://jitpack.io/#yannallain/rrule-kmp)
[![License: MIT](https://img.shields.io/github/license/yannallain/rrule-kmp)](LICENSE)

`rrule-kmp` is a strict RFC 5545 recurrence engine for Kotlin Multiplatform and native iOS. It
gives Android, JVM, shared Kotlin, and pure Swift applications the same recurrence core and
documented DST behavior, so scheduling semantics do not drift between platforms.

Consume it as a Kotlin dependency on Android, JVM, or KMP, or through the Foundation-friendly
`RRuleKit` Swift package on iOS.

- Strict RRULE parsing and canonical serialization, plus strict recurrence-content parsing.
- Lazy occurrence generation and bounded `between`, `after`, and `before` queries.
- Recurrence sets with `DTSTART`, multiple programmatic rules, `RDATE`, `EXDATE`, and exclusion
  precedence.
- Explicit date-only, floating, UTC, `TZID`, DST-gap, and overlap semantics.
- Count-aware indexing for large prefixes, including billion-candidate SECONDLY schedules.
- Published artifacts verified from clean Kotlin, Android full-R8, Kotlin/Native, and native Swift
  consumers.
- Strict-only validation: RFC-invalid input is rejected instead of silently repaired.

[Quick start](#quick-start) · [Installation](#installation) ·
[RFC feature matrix](#supported-rfc-feature-matrix) ·
[Timezone and DST](#timezone-and-dst-semantics) ·
[Known limitations](#known-limitations)

## Quick start

### Choose the right API

| Input or consumer | Entry point |
| --- | --- |
| An RRULE value such as `FREQ=WEEKLY;BYDAY=MO,WE` | `RecurrenceRuleParser.parse`, then `rule.recurrence(start)` |
| `DTSTART` plus optional `RRULE`, `RDATE`, or `EXDATE` content | `RecurrenceContentParser.parse(...).recurrenceSet()` |
| Multiple rules or already-structured application data | Construct `RecurrenceDefinition` or `RecurrenceSet` |
| A pure Swift application | Use `RecurrenceSchedule` from the [native iOS guide](docs/native-ios.md) |

`RecurrenceRuleParser` accepts the value after `RRULE:`, not the `RRULE:` prefix itself. The
recurrence-content parser accepts at most one `RRULE`; use the programmatic set API when one
schedule has multiple inclusion rules.

For recurrence content containing a required `DTSTART` and optional `RRULE`, `RDATE`, or `EXDATE`
lines, parse the complete content and bind it to the lazy recurrence-set engine:

```kotlin
import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.toInstantOrNull
import kotlin.time.Instant

val schedule = RecurrenceContentParser.parse(
    """
    DTSTART;TZID=Europe/Paris:20170102T090000
    RRULE:FREQ=WEEKLY;BYDAY=MO,WE;WKST=MO
    """.trimIndent()
).recurrenceSet()

val scheduledInstants: List<Instant> = schedule.between(
    startInclusive = RecurrenceDateTime.Utc(Instant.parse("2017-01-02T00:00:00Z")),
    endExclusive = RecurrenceDateTime.Utc(Instant.parse("2017-01-09T00:00:00Z")),
    limit = 100,
).mapNotNull { it.toInstantOrNull() }
```

Use `RecurrenceRuleParser` when the input is only an RRULE value, or construct `RecurrenceSet`
directly when recurrence sources are already structured. Pure Swift applications use the smaller
Foundation-native API described in the [native iOS guide](docs/native-ios.md).

## Installation

The examples below pin `0.1.0`; use the version shown by the
[latest release](https://github.com/yannallain/rrule-kmp/releases/latest) for production builds.

### Kotlin Multiplatform, JVM, and Android with JitPack

Add JitPack to dependency resolution in `settings.gradle.kts`. Keeping the repository content
filter makes Gradle use JitPack only for this library:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.yannallain.rrule-kmp")
            }
        }
    }
}
```

For a Kotlin Multiplatform module, add the dependency to `commonMain`:

```kotlin
// Shared module build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(
                "com.github.yannallain.rrule-kmp:rrule-kmp:0.1.0"
            )
        }
    }
}
```

For a regular JVM or Android module, use the same coordinate:

```kotlin
// JVM or Android module build.gradle.kts
dependencies {
    implementation("com.github.yannallain.rrule-kmp:rrule-kmp:0.1.0")
}
```

JitPack exposes Kotlin Multiplatform target publications as modules beneath the
repository-specific `com.github.yannallain.rrule-kmp` group. The shorter
`com.github.yannallain:rrule-kmp` coordinate is its aggregate POM and should not
be used by Gradle consumers. The downloadable GitHub Release Maven repository
is separate and keeps the direct `com.github.yannallain:rrule-kmp` coordinate.

Kotlin consumers should use Kotlin 2.3.20 or newer so their compiler can read
the published metadata. The JVM and Android bytecode target remains Java 11.

JitPack builds numbered Git tags on demand, so the first sync of a new version can take a little
longer. Its Gradle module metadata selects the appropriate JVM, Android, or Kotlin Multiplatform
variant. A pure native iOS application should use the Swift package below instead of consuming a
Kotlin/Native KLIB directly.

Android applications whose `minSdk` is below 26 must also enable the
[desugaring setup](#android-api-2125-consumer-setup).

### Native iOS with Swift Package Manager

In Xcode, choose **File → Add Package Dependencies**, enter:

```text
https://github.com/yannallain/rrule-kmp.git
```

Select the exact version `0.1.0`, add the `RRuleKit` product to the application target, and import
it from Swift:

```swift
import RRuleKit
```

For a package manifest, declare the package and product explicitly:

```swift
dependencies: [
    .package(
        url: "https://github.com/yannallain/rrule-kmp.git",
        exact: "0.1.0"
    ),
],
targets: [
    .target(
        name: "YourApp",
        dependencies: [
            .product(name: "RRuleKit", package: "rrule-kmp"),
        ]
    ),
]
```

Swift Package Manager downloads the versioned
`RRuleKmpCore-<version>.xcframework.zip` from the matching
GitHub Release and builds the Foundation-native `RRuleKit` wrapper. No local Gradle build is needed
for normal Swift consumption. The GitHub Release must be published rather than left as a draft so
that Swift Package Manager can fetch its binary asset.

See the [native iOS integration guide](docs/native-ios.md) for the Swift API, interval queries,
local package development, and consumer verification.

## Releases

Each [GitHub Release](https://github.com/yannallain/rrule-kmp/releases) includes checksummed,
attested artifacts for dependency-manager and manual integration workflows. For `0.1.0`:

| Download | Intended use |
| --- | --- |
| [`RRuleKmpCore-0.1.0.xcframework.zip`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/RRuleKmpCore-0.1.0.xcframework.zip) | Binary consumed automatically by Swift Package Manager |
| [`rrule-kmp-0.1.0-maven-repository.zip`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/rrule-kmp-0.1.0-maven-repository.zip) | All `rrule-kmp` Gradle/Maven publications for local or manual hosting (`com.github.yannallain:rrule-kmp:0.1.0`) |
| [`rrule-kmp-android-0.1.0.aar`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/rrule-kmp-android-0.1.0.aar) | Standalone Android artifact |
| [`rrule-kmp-jvm-0.1.0.jar`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/rrule-kmp-jvm-0.1.0.jar) | Standalone JVM artifact |
| [`rrule-kmp-0.1.0-sources.jar`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/rrule-kmp-0.1.0-sources.jar) | Kotlin sources for IDE navigation and inspection |
| [`rrule-kmp-0.1.0-licenses.zip`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/rrule-kmp-0.1.0-licenses.zip) | Project and bundled third-party notices |
| [`SHA256SUMS`](https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/SHA256SUMS) | SHA-256 verification for every uploaded release asset except the checksum file itself |

The release also carries top-level legal files, the Swift checksum and parsed
manifest, and the exact hosted toolchain record used to build the binaries.

For local or manually hosted Gradle use, extract the Maven archive and point a `maven` repository
at its `repository` directory. The archive contains every `rrule-kmp` variant, but not third-party
dependencies: retain Maven Central and Google, or provide those dependencies through your own
mirror or cache. Prefer dependency-manager resolution over adding the standalone AAR or JAR
directly, because Gradle then selects the right variant and dependencies automatically.

Release assets are rebuilt from a green commit, checksummed, attested, and published through a
protected environment. A post-publication workflow then resolves the numbered JitPack artifacts and
tagged Swift package from clean consumer projects.

Maintainers should follow the [release guide](docs/releasing.md) for candidate preparation,
protected publication, recovery, attestation verification, and distribution smoke checks.

## Platform support and development

| Consumer | Supported target |
| --- | --- |
| JVM | Java 11+ bytecode |
| Android | API 21+; API 21–25 requires core-library desugaring |
| Kotlin Multiplatform for iOS | arm64 device, arm64 simulator, and x86_64 simulator |
| Native Swift | iOS 13+ package with arm64 device and arm64/x86_64 simulator slices |

The Swift package declares iOS 13. Device arm64 and simulator x86_64 slices report iOS 13;
simulator arm64 reports iOS 14, matching the first Apple-silicon simulator generation. The iOS 13
floor is compile/link and metadata verified; execution on a real iOS 13 runtime or device is not
currently part of CI.

The repository currently publishes no Kotlin/JS or Wasm artifact. Native Swift consumers need only
Xcode and Swift Package Manager. Building the Kotlin project requires JDK 21, the checked-in Gradle
wrapper, and Android SDK platform 36; Apple targets additionally require macOS and Xcode.

```shell
./gradlew check checkKotlinAbi koverVerifyJvm koverHtmlReportJvm
./gradlew compileKotlinIosSimulatorArm64 compileTestKotlinIosSimulatorArm64
ANDROID_HOME=/path/to/android-sdk ./gradlew compileAndroidMain testAndroidHostTest
```

CI independently verifies shared/JVM and Android host tests, ABI compatibility, a 90% JVM
line-coverage floor, Android devices and full-R8 consumers on API 21 and API 36, Kotlin/Native,
Swift, and clean public-package consumers. The coverage badge is deliberately limited to common
and JVM Kotlin code executed on the JVM; native, Swift, Android-device, and R8 checks remain
separate release gates.

See the [published KMP consumer fixture](integration-tests/published-kmp-consumer/README.md),
[native iOS guide](docs/native-ios.md), [conformance evidence](docs/conformance-coverage.md), and
[release guide](docs/releasing.md) for the detailed development and verification workflows.

### Android API 21–25 consumer setup

The JVM/Android implementation of `kotlinx-datetime` uses `java.time`. Applications whose
`minSdk` is below 26 must therefore enable core-library desugaring in the Android application
module that produces the final APK or app bundle:

```kotlin
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
```

This is an application packaging requirement and cannot be enabled transitively by a published
Kotlin Multiplatform library. Applications that do not enable desugaring must use API 26 or newer.
The repository's instrumented-test APK enables desugaring itself so the shared suite can run on an
API 21–25 emulator or device; that test-only setting does not alter the published consumer contract.
CI exercises the device suite and standalone minified consumer on API 21 and API 36, so the
minimum-runtime and current-toolchain contracts are both protected.

See the official [kotlinx-datetime setup notes](https://github.com/Kotlin/kotlinx-datetime#using-in-your-projects)
and [Android desugaring documentation](https://developer.android.com/studio/write/java8-support#library-desugaring).

### R8 and ProGuard

The library requires no consumer ProGuard or R8 keep rules. It uses no reflection, name-based class
loading, serialization runtime, JNI, or Android manifest component. Avoid blanket package keeps,
which would only prevent useful optimization. Dependencies may contribute their own targeted rules.

The [standalone minified consumer](integration-tests/android-minified-consumer/README.md) resolves
the published AAR, enables full R8 optimization and resource shrinking with an empty application
rules file, and exercises parsing, timezone resolution, recurrence sets, and DST behavior from the
resulting release APK.

## Parsing and serialization

The parser accepts the value portion of an RRULE, case-insensitively:

```kotlin
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RecurrenceRuleSerializer

val rule = RecurrenceRuleParser.parse(
    "FREQ=WEEKLY;BYDAY=MO,WE,FR"
)

val canonical = RecurrenceRuleSerializer.serialize(rule)
// FREQ=WEEKLY;BYDAY=MO,WE,FR
```

Parsing is strict. Duplicate rule parts, unknown properties, empty values, malformed integers,
invalid ranges, and RFC-invalid rule-part combinations throw `RecurrenceParseException`. The
exception contains the input, property, invalid token, reason enum, and source position when
available. Direct construction throws the equivalent `RecurrenceValidationException`.

Serialization uses a stable uppercase order, sorts list values, omits default `INTERVAL=1` and
`WKST=MO`, and preserves parse/serialize/parse semantics.

### Recurrence content details

As shown in the quick start, `RecurrenceContentParser` handles the recurrence-related lines commonly
carried in API payloads. The result preserves local fields such as `21:00` and the `Europe/Paris`
`TZID`; timezone resolution occurs when the definition is bound to the recurrence engine. The
parser also supports `VALUE=DATE`, UTC and floating values, comma-separated `RDATE`/`EXDATE` values,
`RDATE;VALUE=PERIOD` values, single- and list-valued quoted or unquoted content parameters, and
folded content lines. Property and parameter names are case-insensitive.

This is deliberately a strict recurrence-content parser, not a general ICS parser. It accepts
`DTSTART`, at most one `RRULE`, and any number of `RDATE` and `EXDATE` lines. Unsupported content
properties and recognized standard parameters in invalid positions are explicit errors. As RFC
5545 requires, unrecognized IANA and `X-` parameters are ignored so vendor-decorated payloads remain
interoperable. RFC 5545 removed `EXRULE`; applications needing programmatic exclusion rules can
still supply `RecurrenceDefinition.exclusionRules` or construct a `RecurrenceSet` directly.

An RDATE period is preserved as either `RecurrencePeriod.Explicit(start, end)` or
`RecurrencePeriod.WithDuration(start, duration)` in `RecurrenceDefinition.additionalPeriods`.
Its start participates in recurrence-set ordering, deduplication, and exclusion exactly like an
ordinary RDATE; its instance-specific end or positive RFC duration remains available as metadata.
`RecurrencePeriodSerializer` produces the canonical PERIOD value when content must be rebuilt.
Nominal week/day duration components remain distinct from accurate hour/minute/second components,
which matters across timezone transitions.

## Evaluating one rule

Bind a validated rule to an explicit start value. This complete example uses floating local times,
so its query bounds use the same temporal domain:

```kotlin
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.recurrence
import kotlinx.datetime.LocalDateTime

val recurrence = RecurrenceRuleParser
    .parse("FREQ=WEEKLY;BYDAY=MO,WE,FR")
    .recurrence(
        RecurrenceDateTime.Floating(
            LocalDateTime(2026, 7, 20, 9, 0)
        )
    )

val nextOccurrences = recurrence
    .occurrences()
    .take(10)
    .toList()
```

`occurrences()` returns a cold `Sequence`; it never materializes an unbounded rule. Calendar
periods are generated according to `FREQ` and `INTERVAL`, filtered in RFC order, combined with time
components, sorted, and reduced by `BYSETPOS`.

Bounded queries stop at the final relevant period even when that period has no candidates:

```kotlin
val windowStart = RecurrenceDateTime.Floating(
    LocalDateTime(2026, 7, 22, 0, 0)
)
val windowEnd = RecurrenceDateTime.Floating(
    LocalDateTime(2026, 7, 27, 0, 0)
)

val occurrences = recurrence.between(
    startInclusive = windowStart,
    endExclusive = windowEnd,
    limit = 100
)

val next = recurrence.after(windowEnd, inclusive = false)
val previous = recurrence.before(windowStart, inclusive = false)
```

For rules without `COUNT`, `between`, `after`, and `before` jump directly to the relevant period. A
finite `UNTIL` is also used as an iteration ceiling, including when every intervening period is
empty. Counted queries index exact local-prefix cardinalities for date-only, floating, UTC, and
zoned timelines, including billion-candidate SECONDLY/MINUTELY prefixes and dense YEARLY
expansions. With the default resolver, dense counted prefixes whose per-period cardinality is
stable or follow a Gregorian calendar cycle remain indexed across timezone transitions:
nonexistent local candidates are removed from the count, while an overlap still represents one
occurrence under the selected ambiguity policy. This includes variable DAILY, WEEKLY, MONTHLY,
YEARLY, and YEARLY `BYWEEKNO` prefixes. `BYSETPOS` and arbitrary custom transitioning resolvers
without gap-range support retain a correctness-first finite-prefix scan when post-expansion
selection or unknown gap behavior could change `COUNT`.
Recurrence-set reverse queries preserve exclusion precedence without replaying unbounded inclusion
rules from `DTSTART`.

## DTSTART and recurrence-set semantics

A standalone `RuleRecurrence` returns `DTSTART` only when the start satisfies the rule's filters.
This keeps standalone rule expansion distinct from complete recurrence-set semantics.

An iCalendar recurrence set includes `DTSTART` independently. Use `RecurrenceSet` for that behavior:

```kotlin
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RecurrenceSet
import kotlinx.datetime.LocalDateTime

val eventStart = RecurrenceDateTime.Floating(
    LocalDateTime(2026, 7, 20, 9, 0)
)
val weeklyRule = RecurrenceRuleParser.parse(
    "FREQ=WEEKLY;BYDAY=MO,WE,FR"
)
val firstOfMonthRule = RecurrenceRuleParser.parse(
    "FREQ=MONTHLY;BYMONTHDAY=1"
)

val set = RecurrenceSet(
    start = eventStart,
    rules = listOf(weeklyRule, firstOfMonthRule),
    additionalDates = setOf(
        RecurrenceDateTime.Floating(
            LocalDateTime(2026, 7, 21, 9, 0)
        )
    ),
    excludedDates = setOf(
        RecurrenceDateTime.Floating(
            LocalDateTime(2026, 7, 22, 9, 0)
        )
    )
)

val occurrences = set.between(
    startInclusive = RecurrenceDateTime.Floating(
        LocalDateTime(2026, 7, 20, 0, 0)
    ),
    endExclusive = RecurrenceDateTime.Floating(
        LocalDateTime(2026, 8, 3, 0, 0)
    ),
    limit = 500
)
```

`RecurrenceSet` performs a lazy k-way ordered merge across `DTSTART`, all RRULEs, ordinary RDATE
values, and RDATE PERIOD starts. It eliminates duplicates while streaming. EXDATE and
exclusion-rule matches take precedence over every inclusion source. For an absolute recurrence
domain, UTC and TZID-bearing RDATE/EXDATE values may be mixed and are compared by resolved instant.
Every public collection is a detached read-only snapshot; `additionalPeriods` preserves the
period-specific end or duration without changing the start-only occurrence API.

RFC 5545 says `DTSTART` should be synchronized with the recurrence pattern and always defines the
first component instance. The result of a mismatched `DTSTART` and RRULE is otherwise undefined by
the RFC; the two APIs above make the distinction explicit.

## Timezone and DST semantics

The temporal model keeps four iCalendar domains distinct:

- `DateOnly` — a calendar date with no time of day.
- `Floating` — local date-time fields with no time zone.
- `Utc` — an absolute `kotlin.time.Instant`.
- `Zoned` — local date-time fields associated with an iCalendar `TZID`.

Expansion happens in calendar fields. Zoned candidates are resolved only after filters and
`BYSETPOS` have been applied, so invalid dates and generated DST-gap candidates are skipped rather
than silently normalized.

`KotlinxRecurrenceTimeZoneResolver` uses `kotlinx-datetime` and the platform timezone database. Its result is explicit:

```kotlin
sealed interface LocalTimeResolution {
    data class Valid(val instant: Instant)
    data class Ambiguous(val earlier: Instant, val later: Instant)
    data object Nonexistent
}
```

- A nonexistent local candidate generated by an RRULE is skipped and does not consume `COUNT`.
- An explicitly supplied `DTSTART`, `RDATE`, or `EXDATE` in a gap uses the UTC offset before the gap,
  as required by RFC 5545. Custom resolvers can opt out by returning `null` from
  `nonexistentInstant`, in which case such an explicit value is rejected.
- An ambiguous fall-back time is one recurrence instance. `AmbiguousTimePolicy.EARLIER` is the
  default; `LATER` is opt-in.
- The ambiguity policy controls instant ordering and UTC `UNTIL` comparison. The emitted `Zoned`
  value continues to preserve its local fields and `TZID`.
- A custom `RecurrenceTimeZoneResolver` can supply application-owned or VTIMEZONE-derived rules.
- A fixed-offset custom resolver can also implement `LinearLocalTimeZoneResolver` to opt into exact
  counted-prefix indexing. Its contract requires a one-to-one, order-preserving local timeline;
  transition zones must not claim it. The default resolver advertises it only for fixed offsets.
- A resolver should also implement `localDateTimeAt` when UTC or different-TZID query/`UNTIL`
  bounds may be far from `DTSTART`. Resolve-only implementations remain correct, but must scan
  forward and can be expensive for sub-daily rules.
- UTC and zoned `DTSTART` values require UTC `UNTIL`; floating and date-only starts require matching
  floating and date-only `UNTIL` values.

Floating values are never interpreted in the machine's current timezone. Date-only recurrences
reject sub-day frequencies. In accordance with RFC 5545, `BYHOUR`, `BYMINUTE`, and `BYSECOND` are
ignored when legacy input supplies them with a date-only `DTSTART`.

## Supported RFC feature matrix

| Feature | Status | Notes |
| --- | --- | --- |
| `FREQ` | Supported | SECONDLY through YEARLY |
| `INTERVAL`, `COUNT`, inclusive `UNTIL` | Supported | COUNT and UNTIL are mutually exclusive |
| `BYSECOND`, `BYMINUTE`, `BYHOUR` | Supported | Second 60 uses the RFC fallback to 59 |
| `BYDAY` | Supported | Plain and valid numeric ordinals |
| `BYMONTHDAY`, `BYYEARDAY` | Supported | Positive and negative |
| `BYWEEKNO` and `WKST` | Supported | RFC week-year rules, positive and negative |
| `BYMONTH`, `BYSETPOS` | Supported | Positive/negative set positions |
| Invalid generated dates/local times | Supported | Skipped and not counted; explicit gap values use the RFC offset-before-gap rule |
| DTSTART defaults | Supported | Missing date/time fields derive from start |
| Date-only, floating, UTC, TZID | Supported | Separate public variants |
| Multiple inclusion rules | Supported | Programmatic set API; lazy merge, deduplication, exclusion precedence |
| Multiple RDATE/EXDATE values | Supported | Content and programmatic set APIs |
| Exclusion rules | Supported | Programmatic API; RFC 5545 content does not include EXRULE |
| RRULE value parsing | Supported | Strict mode only |
| Recurrence content parsing | Supported | DTSTART/RRULE/RDATE/EXDATE, VALUE/TZID, PERIOD, and line unfolding |
| Full VCALENDAR/VEVENT parsing | Not included | Non-recurrence properties and component boundaries are out of scope |
| RDATE PERIOD values | Supported | Explicit end and positive duration forms; starts enter the recurrence set and metadata is preserved |
| Embedded VTIMEZONE parsing | Not yet included | Inject a custom resolver |

## Validation and strict RFC behavior

Construction and parsing enforce, among other constraints:

- positive `INTERVAL` and `COUNT`;
- no `COUNT` plus `UNTIL`;
- every RFC numeric range, including signed non-zero ranges;
- `BYWEEKNO` only with YEARLY;
- no `BYMONTHDAY` with WEEKLY;
- no `BYYEARDAY` with DAILY, WEEKLY, or MONTHLY;
- numeric `BYDAY` only in its valid MONTHLY/YEARLY contexts and never with YEARLY `BYWEEKNO`;
- `BYSETPOS` only with another BY part;
- whole-second stored RFC DATE-TIME values (query bounds may retain fractional precision);
- start-dependent DATE/frequency and `UNTIL` compatibility.

Unknown properties are rejected. There is currently no lenient compatibility mode. RFC 5545 is
authoritative, so the library also:

- preserves date-only, floating, UTC, and `TZID` values instead of collapsing them into instants;
- has no implicit `DTSTART=now` default;
- separates standalone rule expansion from recurrence sets that always include `DTSTART`;
- distinguishes skipped generated DST-gap candidates from explicit values that use the RFC
  offset-before-gap interpretation;
- applies a documented ambiguity policy to overlap values.

Cross-implementation differences are diagnostic rather than automatically considered defects. See
the [differential-testing guide](docs/differential-testing.md).

## Determinism, concurrency, and performance

Public models expose detached read-only collection snapshots, engines are stateless, and all
iterator state belongs to each returned sequence. Instances using the default resolver can be
shared across threads; an injected resolver must provide its own thread-safety. Iterators
themselves follow normal Kotlin iterator rules and should not be concurrently advanced.

The engine iterates calendar periods rather than seconds for daily-or-larger rules. YEARLY scans at
most one week-year, MONTHLY at most one month, and WEEKLY one week per interval. Smaller
frequencies jump directly across calendar and clock ranges that their limiting filters cannot
match. Count-aware candidate indexing avoids replaying dense prefixes. Variable calendar counts
use exact 400-year Gregorian-cycle cardinalities, including week-number rules, while small or
already-exhausted counts stop before building the complete cycle. For the default timezone
resolver, the index accounts for forward offset gaps from platform timezone data and uses
logarithmic instant lookup for reverse queries around overlaps. Absolute bounds on a non-selected
overlap branch use conservative overlap-width padding instead of restarting at `DTSTART`.
Candidate date-times are streamed in chronological order instead of materializing a period's
Cartesian product. `BYSETPOS` reads only as far as the most distant requested position from each
end of a period. Iteration ends at `COUNT`, `UNTIL`, a query bound, caller cancellation, or RFC
5545's four-digit year boundary; it has no application-defined horizon.

Performance smoke tests cover 100,000 daily instances, sparse leap-day and secondly rules,
maximum-cardinality yearly expansions, billion-candidate counted SECONDLY and large MINUTELY
prefixes, Gregorian-cycle DAILY/WEEKLY/MONTHLY/YEARLY and `BYWEEKNO` prefixes, far counted queries
across one-hour, half-hour, political, and skipped-date timezone transitions, far-future
monthly/yearly windows, and a 20,000-result multi-rule recurrence set.

## Known limitations

- RFC 5545 permits second `60`. Because Kotlin temporal types cannot represent a literal leap
  second, parsing and recurrence evaluation apply RFC 5545's recommended fallback and interpret it
  as second `59`. `BYSECOND=59,60` therefore yields one candidate before `BYSETPOS`; literal
  leap-second identity cannot be preserved or emitted.
- Timezone results depend on the platform timezone database unless a resolver is injected.
- Full VCALENDAR/VEVENT and VTIMEZONE parsing remain outside the current API. Recurrence content
  lines, unfolding, TZID parameters, and RDATE PERIOD values are supported.
- RFC 5545 removed EXRULE. The content parser rejects it, while the programmatic recurrence-set API
  retains exclusion rules for application-owned inputs.
- The engine proves common empty rules by checking clock congruence and a complete 400-year
  Gregorian cycle. An unbounded query for a more complex contradiction that is not covered by
  those proofs may still traverse to RFC 5545's four-digit year boundary. Bounded operations stop
  at their calendar bound.
- The library currently exposes strict RFC behavior only; no permissive compatibility mode is provided.

## Tests and evidence

Shared tests include:

- grouped examples from RFC 5545 section 3.8.5.3;
- edge cases for leap years, invalid month days, signed selectors, week 1/53, `WKST`, DST
  gaps/overlaps, all temporal kinds, and recurrence sets;
- parse/serialize/parse round trips and structured parser failures;
- deterministic generated invariants for ordering, uniqueness, bounds, and count;
- targeted RFC regression cases;
- performance workload smoke tests;
- a differential comparison harness for externally exported reference results.

The [conformance coverage map](docs/conformance-coverage.md) records the RFC behavior categories
covered by focused examples, regression tests, generated invariants, and cross-domain matrices.

Passing tests are evidence for the listed behavior, not a blanket certification of every possible RFC input.

The hourly example uses
[verified RFC erratum 3883](https://www.rfc-editor.org/errata/eid3883)
(`UNTIL=19970902T210000Z`), which corrects the published example's UTC boundary.

## Security

Report suspected vulnerabilities privately through the
[security policy](SECURITY.md). Please do not disclose a vulnerability in a
public issue or discussion before a fix can be coordinated.

## License

`rrule-kmp` is available under the [MIT License](LICENSE). You may use, copy, modify, merge,
publish, distribute, sublicense, and sell copies subject to the license notice. Selected regression
test and binary-runtime material retains its required terms in
[`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES) and [`LICENSES`](LICENSES/README.md).

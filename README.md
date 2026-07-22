# rrule-kmp

`rrule-kmp` is a Kotlin Multiplatform library for strict RFC 5545 recurrence-rule parsing, canonical serialization, lazy occurrence generation, and complete recurrence-set evaluation. Its implementation and tests are shared by JVM, Android, and iOS.

The temporal model deliberately keeps four iCalendar concepts distinct:

- `DateOnly` — a calendar date with no time of day.
- `Floating` — local date-time fields with no time zone.
- `Utc` — an absolute `kotlin.time.Instant`.
- `Zoned` — local date-time fields associated with an iCalendar `TZID`.

Expansion happens in calendar fields. Zoned candidates are resolved only after filters and `BYSETPOS` have been applied, so invalid dates and generated DST-gap candidates are skipped instead of normalized.

## Availability and quick start

The project is currently consumed from a source checkout: neither a public Maven repository nor a
remote Swift Package release is configured yet. The project is available under the permissive
[MIT License](LICENSE); canonical remote publication and signing still need to be configured.
The local build commands require JDK 17+ and a configured Android SDK. Apple artifacts additionally
require macOS and Xcode.

- Generate version `0.1.0` of the root KMP metadata, JVM and Android artifacts, and iOS KLIBs in
  `build/repository` with
  `./gradlew -PVERSION_NAME=0.1.0 publishAllPublicationsToLocalBuildRepository`, then add that
  directory as a Maven repository and depend on `io.github.yallain:rrule-kmp:0.1.0`.
- Pure Swift consumers can run `./gradlew :apple:rrule-kit:prepareLocalSwiftPackage` and add the
  generated `build/swift-package` directory to Xcode.

A KMP consumer can resolve the locally built artifact with:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("/absolute/path/to/rrule-kmp/build/repository") }
        google()
        mavenCentral()
    }
}
```

```kotlin
// Shared module build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yallain:rrule-kmp:0.1.0")
        }
    }
}
```

For a regular JVM or Android module, use the same repository and dependency coordinates:

```kotlin
// JVM or Android module build.gradle.kts
dependencies {
    implementation("io.github.yallain:rrule-kmp:0.1.0")
}
```

Android applications whose `minSdk` is below 26 must also enable the
[desugaring setup](#android-api-2125-consumer-setup).

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
    DTSTART;TZID=Europe/Paris:20170101T210000
    RRULE:FREQ=DAILY;WKST=MO
    """.trimIndent()
).recurrenceSet()

val nightStarts: List<Instant> = schedule.between(
    startInclusive = RecurrenceDateTime.Utc(Instant.parse("2017-01-01T00:00:00Z")),
    endExclusive = RecurrenceDateTime.Utc(Instant.parse("2017-01-04T00:00:00Z")),
    limit = 100,
).mapNotNull { it.toInstantOrNull() }
```

Use `RecurrenceRuleParser` when the input is only an RRULE value, or construct `RecurrenceSet`
directly when recurrence sources are already structured. Pure Swift applications use the smaller
Foundation-native API described in the [native iOS guide](docs/native-ios.md).

## Repository build and targets

The project uses Kotlin 2.3.20, `kotlinx-datetime` 0.8.0, Gradle 9.4.1, and the Android-KMP library
plugin. Targets are JVM 11+, Android API 21+, iOS device arm64, and iOS simulator arm64/x86_64. The
Android AAR metadata declares consumer compile SDK 21+ and AGP 8.0+; the checked-in build uses AGP
9.2.1 and compile SDK 36. Android consumers below API 26 must enable the desugaring setup documented
below. Building or testing Apple targets requires macOS and Xcode.

This repository does not currently publish Kotlin/JS or Wasm artifacts. A web frontend therefore
needs a dedicated RFC 5545 implementation while sharing payload and acceptance vectors with its
Kotlin consumers.

```shell
./gradlew jvmTest
./gradlew compileKotlinIosSimulatorArm64 compileTestKotlinIosSimulatorArm64
ANDROID_HOME=/path/to/android-sdk ./gradlew compileAndroidMain testAndroidHostTest
ANDROID_HOME=/path/to/android-sdk ./gradlew connectedAndroidDeviceTest
```

ABI baselines under `api/` are checked as part of `check`. All KMP Maven publications can be
generated and inspected without an external repository:

```shell
./gradlew check checkKotlinAbi
./gradlew -PVERSION_NAME=0.1.0 publishAllPublicationsToLocalBuildRepository
```

The second command writes the root metadata, JVM JAR, Android AAR, and iOS klibs beneath
`build/repository`. See [the release checklist](docs/releasing.md) for the external publication
steps that remain before publishing outside this repository.

### Native iOS and Swift

From a source checkout, pure Swift applications consume the generated `RRuleKit` local package
rather than the Kotlin-facing KLIB. The package wraps a static `RRuleKmpCore.xcframework` and
exposes Foundation dates, Swift collections, bounded queries, and catchable Swift errors. Its
instant-oriented facade accepts UTC or `TZID`-bearing `DTSTART` values; date-only and floating
recurrences remain available through the KMP API. It supports iOS device arm64 and simulator
arm64/x86_64 while leaving the existing KMP artifact unchanged.

```shell
./gradlew :apple:rrule-kit:iosSimulatorArm64Test
./gradlew :apple:rrule-kit:prepareLocalSwiftPackage
```

Add the generated `build/swift-package` directory—not the binary-less checked-in package fixture—as
a local package in Xcode and import `RRuleKit`. A remote Swift Package is not configured yet. The
framework declares an iOS 13 device target, but a real iOS 13 runtime/device test remains a release
prerequisite. See the [native iOS integration guide](docs/native-ios.md) for the Swift API,
elapsed-duration example, consumer tests, and remote binary-release procedure.

### Android API 21–25 consumer setup

The JVM/Android implementation of `kotlinx-datetime` uses `java.time`. Applications whose
`minSdk` is below 26 must therefore enable core-library desugaring in the Android application
module that produces the final APK or app bundle:

```kotlin
android {
    compileOptions {
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
The standalone minified consumer is verified locally on a current API; a protected release workflow
should additionally retain an API 21 runtime lane.

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

Parsing is strict. Duplicate rule parts, unknown properties, empty values, malformed integers, invalid ranges, and RFC-invalid rule-part combinations throw `RecurrenceParseException`. The exception contains the input, property, invalid token, reason enum, and source position when available. Direct construction throws the equivalent `RecurrenceValidationException`.

Serialization uses a stable uppercase order, sorts list values, omits default `INTERVAL=1` and `WKST=MO`, and preserves parse/serialize/parse semantics.

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

`occurrences()` returns a cold `Sequence`; it never materializes an unbounded rule. Calendar periods are generated according to `FREQ` and `INTERVAL`, filtered in RFC order, combined with time components, sorted, and reduced by `BYSETPOS`.

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
zoned timelines whose resolver implements `LinearLocalTimeZoneResolver` and opts in for that
`TZID`, including billion-candidate SECONDLY/MINUTELY prefixes and dense YEARLY expansions.
`BYSETPOS`, `BYWEEKNO`, transitioning zones, and arbitrary custom resolvers retain a
correctness-first finite-prefix scan when gaps or post-expansion selection could change `COUNT`.
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

RFC 5545 says `DTSTART` should be synchronized with the recurrence pattern and always defines the first component instance. The result of a mismatched `DTSTART` and RRULE is otherwise undefined by the RFC; the two APIs above make the distinction explicit.

## Timezone and DST semantics

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
- An ambiguous fall-back time is one recurrence instance. `AmbiguousTimePolicy.EARLIER` is the default; `LATER` is opt-in.
- The ambiguity policy controls instant ordering and UTC `UNTIL` comparison. The emitted `Zoned` value continues to preserve its local fields and `TZID`.
- A custom `RecurrenceTimeZoneResolver` can supply application-owned or VTIMEZONE-derived rules.
- A fixed-offset custom resolver can also implement `LinearLocalTimeZoneResolver` to opt into exact
  counted-prefix indexing. Its contract requires a one-to-one, order-preserving local timeline;
  transition zones must not claim it. The default resolver advertises it only for fixed offsets.
- A resolver should also implement `localDateTimeAt` when UTC or different-TZID query/`UNTIL`
  bounds may be far from `DTSTART`. Resolve-only implementations remain correct, but must scan
  forward and can be expensive for sub-daily rules.
- UTC and zoned `DTSTART` values require UTC `UNTIL`; floating and date-only starts require matching floating and date-only `UNTIL` values.

Floating values are never interpreted in the machine's current timezone. Date-only recurrences
reject sub-day frequencies. In accordance with RFC 5545, `BYHOUR`, `BYMINUTE`, and `BYSECOND` are
ignored when legacy input supplies them with a date-only `DTSTART`.

### Elapsed durations from application payloads

An application-specific field such as `duration: 32400` is not an RFC recurrence property and is
therefore intentionally not parsed by this library. Treating it as elapsed duration is
unambiguous: resolve each emitted start with the same resolver and ambiguity policy used by the
recurrence set, then add exactly `32400.seconds` to that start `Instant`.

The following example filters all payload entries tagged `night`, preserves multiple entries with
that tag, includes a night that began before the attendance, and clips each result to the
attendance interval:

```kotlin
import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.toInstantOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class ScheduleKind(
    val tags: Set<String>,
    val frequency: String,
    val durationSeconds: Long,
)

data class InstantInterval(
    val start: Instant,
    val endExclusive: Instant,
)

fun elapsedIntervals(
    scheduleKind: ScheduleKind,
    attendanceStart: Instant,
    attendanceEnd: Instant,
): List<InstantInterval> {
    require(scheduleKind.durationSeconds > 0)
    require(attendanceStart < attendanceEnd)

    val duration = scheduleKind.durationSeconds.seconds
    val schedule = RecurrenceContentParser
        .parse(scheduleKind.frequency)
        .recurrenceSet()

    return schedule.between(
        startInclusive = RecurrenceDateTime.Utc(
            attendanceStart - duration,
        ),
        endExclusive = RecurrenceDateTime.Utc(attendanceEnd),
    ).mapNotNull { occurrence ->
        val start = requireNotNull(
            occurrence.toInstantOrNull(
                timeZoneResolver = schedule.timeZoneResolver,
                ambiguousTimePolicy = schedule.ambiguousTimePolicy,
            ),
        ) {
            "Attendance schedules must use UTC or TZID date-times"
        }

        val clippedStart = maxOf(start, attendanceStart)
        val clippedEnd = minOf(start + duration, attendanceEnd)

        if (clippedStart < clippedEnd) {
            InstantInterval(clippedStart, clippedEnd)
        } else {
            null
        }
    }
}

fun nightIntervals(
    scheduleKinds: List<ScheduleKind>,
    attendanceStart: Instant,
    attendanceEnd: Instant,
): List<InstantInterval> = scheduleKinds
    .filter { "night" in it.tags }
    .flatMap { scheduleKind ->
        elapsedIntervals(
            scheduleKind = scheduleKind,
            attendanceStart = attendanceStart,
            attendanceEnd = attendanceEnd,
        )
    }
```

`toInstantOrNull` also applies the RFC offset-before-gap rule for an explicitly supplied gap value;
it returns `null` for date-only or floating values, which need application-owned context. It throws
`RecurrenceValidationException` when an absolute value cannot be resolved, such as an unknown
`TZID` or a custom resolver that cannot provide explicit-gap semantics. Across a DST change, the
displayed local end time may consequently differ from the usual wall-clock end; the elapsed
duration remains exactly nine hours.

The helper is for UTC or `TZID`-bearing schedules. Floating and date-only schedules need an
application-owned timezone before they can become attendance instants. Tags, durations, breaks,
and rates remain application policy. Normalize overlapping breaks, then union overlapping
half-open night intervals before subtracting break intersections so the same elapsed time is not
counted twice in one day/night classification. For rate calculation, retain the source schedule or
pricing metadata instead when premiums are intended to stack. The executable
[attendance example](src/commonTest/kotlin/io/github/yallain/rrule/usecase/ElapsedNightScheduleTest.kt)
covers a previous-day night, a break, and both DST transitions.

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

## Validation

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

Unknown properties are rejected. There is currently no lenient compatibility mode.

## Determinism, concurrency, and performance

Public models expose detached read-only collection snapshots, engines are stateless, and all
iterator state belongs to each returned sequence. Instances using the default resolver can be
shared across threads; an injected resolver must provide its own thread-safety. Iterators
themselves follow normal Kotlin iterator rules and should not be concurrently advanced.

The engine iterates calendar periods rather than seconds for daily-or-larger rules. YEARLY scans at
most one week-year, MONTHLY at most one month, and WEEKLY one week per interval. Smaller
frequencies jump directly across calendar and clock ranges that their limiting filters cannot
match. Count-aware candidate indexing avoids replaying dense linear prefixes, and absolute bounds
on the non-selected branch of a DST overlap use conservative overlap-width padding instead of
restarting at `DTSTART`. Candidate date-times are streamed in chronological order instead of
materializing a period's Cartesian product. `BYSETPOS` reads only as far as the most distant
requested position from each end of a period. Iteration ends at `COUNT`, `UNTIL`, a query bound,
caller cancellation, or RFC 5545's four-digit year boundary; it has no application-defined
horizon.

Performance smoke tests cover 100,000 daily instances, sparse leap-day and secondly rules,
maximum-cardinality yearly expansions, billion-candidate counted SECONDLY/MINUTELY prefixes,
far-future monthly/yearly windows, and a 20,000-result multi-rule recurrence set.

## Known limitations

- RFC 5545 permits second `60`. Because Kotlin temporal types cannot represent a literal leap
  second, parsing and recurrence evaluation apply RFC 5545's recommended fallback and interpret it
  as second `59`. `BYSECOND=59,60` therefore yields one candidate before `BYSETPOS`; literal
  leap-second identity cannot be preserved or emitted.
- Timezone results depend on the platform timezone database unless a resolver is injected.
- Full VCALENDAR/VEVENT and VTIMEZONE parsing remain outside the current API. Recurrence content
  lines, unfolding, TZID parameters, and RDATE PERIOD values are supported.
- RFC 5545 removed EXRULE. The content parser rejects it, while the programmatic recurrence-set API retains exclusion rules for application-owned inputs.
- The engine proves common empty rules by checking clock congruence and a complete 400-year
  Gregorian cycle. An unbounded query for a more complex contradiction that is not covered by
  those proofs may still traverse to RFC 5545's four-digit year boundary. Bounded operations stop
  at their calendar bound.
- The library currently exposes strict RFC behavior only; no permissive compatibility mode is provided.

## Strict RFC behavior

RFC 5545 is authoritative. This library deliberately:

- rejects RFC-invalid rule-part/frequency combinations instead of accepting every BY part everywhere;
- preserves date-only, floating, UTC, and TZID values instead of collapsing them into one instant representation;
- has no implicit `DTSTART=now` default;
- rejects duplicate rule parts and `COUNT` plus `UNTIL`;
- separates standalone rule expansion from a complete set that always includes `DTSTART`;
- distinguishes generated DST-gap candidates (skipped) from explicitly supplied gap values (the
  RFC offset-before-gap interpretation), and documents its overlap policy.

Cross-implementation differences are diagnostic rather than automatically considered defects. See
[differential testing](docs/differential-testing.md).

## Tests and evidence

Shared tests include:

- grouped examples from RFC 5545 section 3.8.5.3;
- edge cases for leap years, invalid month days, signed selectors, week 1/53, `WKST`, DST gaps/overlaps, all temporal kinds, and recurrence sets;
- parse/serialize/parse round trips and structured parser failures;
- deterministic generated invariants for ordering, uniqueness, bounds, and count;
- targeted RFC regression cases;
- performance workload smoke tests;
- a differential comparison harness for externally exported reference results.

The [conformance coverage map](docs/conformance-coverage.md) records the RFC behavior categories
covered by focused examples, regression tests, generated invariants, and cross-domain matrices.

Passing tests are evidence for the listed behavior, not a blanket certification of every possible RFC input.

The hourly example uses [verified RFC erratum 3883](https://www.rfc-editor.org/errata/eid3883) (`UNTIL=19970902T210000Z`), which corrects the published example's UTC boundary.

## License

`rrule-kmp` is available under the [MIT License](LICENSE). You may use, copy, modify, merge,
publish, distribute, sublicense, and sell copies subject to the license notice. Selected regression
test material retains its required terms in [`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES).

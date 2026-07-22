# Conformance coverage

RFC 5545 is the authoritative behavior specification for this project. Coverage combines focused
examples, regression vectors, deterministic generated invariants, recurrence-set algebra, and
cross-domain matrices rather than claiming that a finite fixture list certifies every possible
recurrence.

## Covered behavior categories

- All seven frequencies, intervals, `COUNT`, and RFC-compatible inclusive `UNTIL` boundaries.
- Calendar selectors: positive and negative month days, year days, week numbers, weekdays,
  ordinal weekdays, and set positions.
- Time expansions and limitations through `BYHOUR`, `BYMINUTE`, and `BYSECOND`.
- Week 53 and week-start-sensitive interval behavior.
- `before`, `after`, and half-open bounded queries for finite and infinite rules.
- Recurrence-set merging, `RDATE`, `EXDATE`, programmatic exclusion rules, duplicate removal, and
  bounded queries over infinite rules.
- Date-only, floating, UTC, and TZID-bearing values across daylight-saving gaps and overlaps.
- Dates before the Unix epoch and rules near RFC 5545's maximum four-digit year.
- RRULE and recurrence-content parsing, validation, serialization, and round trips.
- Iterator isolation, immutable public models, concurrency, and bounded-result safeguards.
- Sparse and billion-scale counted-query performance paths.

Focused regressions live under `src/commonTest/kotlin/io/github/yallain/rrule/regression`. Additional
RFC examples, generated properties, timezone matrices, parser tests, and recurrence-set algebra are
organized by their corresponding packages under `src/commonTest`.

## Deliberate scope boundaries

| Area | Contract |
| --- | --- |
| Result caching | No public mutable cache; lazy iterator isolation is tested instead. |
| Natural-language parsing or rendering | Outside the RRULE and recurrence-content APIs. |
| Platform-specific date helpers | Outside the shared temporal model. |
| Permissive parsing modes | Parsing is strict and rejects unsupported defaults and non-RFC properties. |
| Non-RFC calendar extensions | Not accepted as RRULE parts. |
| Subsecond stored recurrence values | RFC DATE-TIME values use whole-second precision; query bounds may retain fractional precision. |
| `COUNT` together with `UNTIL` | Rejected because RFC 5545 makes them mutually exclusive. |
| RFC-invalid BY-part/frequency combinations | Rejected by strict validation. |
| Inclusive bounded-query end points | The API deliberately uses `[startInclusive, endExclusive)`. |
| Mutable recurrence-set builders | `RecurrenceSet` is immutable and receives all sources at construction. |

Update this map whenever the public contract or a coverage category changes.

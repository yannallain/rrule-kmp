package io.github.yallain.rrule

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Shared timezone resolver backed by `kotlinx-datetime` and the platform timezone database.
 *
 * `kotlinx-datetime` chooses an offset for gaps and overlaps. This resolver round-trips that result
 * and probes the adjacent offsets so callers receive an explicit resolution instead.
 */
public object KotlinxRecurrenceTimeZoneResolver : LinearLocalTimeZoneResolver {
    override fun hasLinearLocalTimeline(timeZoneId: String): Boolean =
        TimeZone.of(timeZoneId) is FixedOffsetTimeZone

    override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
        val timeZone = TimeZone.of(timeZoneId)
        val defaultInstant = localDateTime.toInstant(timeZone)
        if (defaultInstant.toLocalDateTime(timeZone) != localDateTime) {
            return LocalTimeResolution.Nonexistent
        }

        val possibleInstants = listOf(
            timeZone.offsetAt(defaultInstant - 2.days),
            timeZone.offsetAt(defaultInstant),
            timeZone.offsetAt(defaultInstant + 2.days),
        )
            .distinct()
            .map { offset -> localDateTime.toInstant(offset) }
            .filter { instant -> instant.toLocalDateTime(timeZone) == localDateTime }
            .distinct()
            .sorted()

        return if (possibleInstants.size > 1) {
            LocalTimeResolution.Ambiguous(possibleInstants.first(), possibleInstants.last())
        } else {
            LocalTimeResolution.Valid(possibleInstants.singleOrNull() ?: defaultInstant)
        }
    }

    override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
        instant.toLocalDateTime(TimeZone.of(timeZoneId))

    override fun nonexistentInstant(localDateTime: LocalDateTime, timeZoneId: String): Instant =
        localDateTime.toInstant(TimeZone.of(timeZoneId))

    /** Returns complete local gaps using the same platform timezone database as [resolve]. */
    internal fun nonexistentLocalTimeRanges(
        timeZoneId: String,
        startInclusive: LocalDateTime,
        endInclusive: LocalDateTime,
    ): List<NonexistentLocalTimeRange> {
        if (startInclusive > endInclusive) return emptyList()
        val timeZone = TimeZone.of(timeZoneId)
        if (timeZone is FixedOffsetTimeZone) return emptyList()

        // Every UtcOffset is strictly less than 24 hours. Treating local fields as UTC and adding
        // two days on each side therefore contains every transition whose before/after local
        // projection can intersect the requested interval.
        val searchStart = startInclusive.toInstant(TimeZone.UTC) - 2.days
        val searchEnd = endInclusive.toInstant(TimeZone.UTC) + 2.days
        val ranges = platformTimeZoneTransitions(timeZoneId, searchStart, searchEnd)
            .asSequence()
            .filter { transition ->
                transition.offsetAfterSeconds > transition.offsetBeforeSeconds
            }
            .map { transition ->
                NonexistentLocalTimeRange(
                    startInclusive = transition.instant.toLocalDateTime(
                        FixedOffsetTimeZone(UtcOffset(seconds = transition.offsetBeforeSeconds)),
                    ),
                    endExclusive = transition.instant.toLocalDateTime(
                        FixedOffsetTimeZone(UtcOffset(seconds = transition.offsetAfterSeconds)),
                    ),
                )
            }
            .filter { range ->
                range.endExclusive > startInclusive && range.startInclusive <= endInclusive
            }
            .sortedBy(NonexistentLocalTimeRange::startInclusive)
            .toList()

        ranges.zipWithNext().forEach { (left, right) ->
            check(left.endExclusive <= right.startInclusive) {
                "Timezone gaps must be ordered and non-overlapping for $timeZoneId"
            }
        }
        return ranges
    }
}

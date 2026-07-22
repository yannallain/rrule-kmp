package io.github.yallain.rrule

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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
}

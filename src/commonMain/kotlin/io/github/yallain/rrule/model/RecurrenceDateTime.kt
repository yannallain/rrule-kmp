package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * A recurrence value with its iCalendar temporal semantics preserved.
 *
 * These variants are deliberately not interchangeable. In particular, floating values do not
 * identify an instant and zoned values retain their local calendar fields for recurrence expansion.
 */
public sealed interface RecurrenceDateTime {
    /** A calendar date without a time of day. */
    public data class DateOnly(public val date: LocalDate) : RecurrenceDateTime

    /** A local date-time that is not associated with any time zone. */
    public data class Floating(public val dateTime: LocalDateTime) : RecurrenceDateTime

    /** An absolute UTC instant. */
    public data class Utc(public val instant: Instant) : RecurrenceDateTime

    /** Local calendar fields associated with an iCalendar `TZID`. */
    public data class Zoned(
        public val dateTime: LocalDateTime,
        public val timeZoneId: String,
    ) : RecurrenceDateTime {
        init {
            if (timeZoneId.isBlank()) {
                throw RecurrenceValidationException(
                    propertyName = "TZID",
                    invalidToken = timeZoneId,
                    reason = RecurrenceErrorReason.EMPTY_VALUE,
                    detail = "TZID must not be blank",
                )
            }
        }
    }
}

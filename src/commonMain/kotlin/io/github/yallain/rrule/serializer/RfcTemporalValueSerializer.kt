package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Shared canonical formatting for RFC 5545 DATE and DATE-TIME values. */
internal object RfcTemporalValueSerializer {
    fun serialize(
        value: RecurrenceDateTime,
        propertyName: String,
        allowZoned: Boolean,
    ): String {
        validateRfcTemporalValue(propertyName, value)
        return when (value) {
            is RecurrenceDateTime.DateOnly -> formatDate(value.date)
            is RecurrenceDateTime.Floating -> formatDateTime(value.dateTime)
            is RecurrenceDateTime.Utc -> formatDateTime(value.instant.toLocalDateTime(TimeZone.UTC)) + "Z"
            is RecurrenceDateTime.Zoned -> if (allowZoned) {
                formatDateTime(value.dateTime)
            } else {
                throw RecurrenceValidationException(
                    propertyName = propertyName,
                    invalidToken = value.toString(),
                    reason = RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
                    detail = "An $propertyName value cannot carry a TZID",
                )
            }
        }
    }

    private fun formatDateTime(value: LocalDateTime): String =
        formatDate(value.date) + "T" + two(value.hour) + two(value.minute) + two(value.second)

    private fun formatDate(value: LocalDate): String =
        value.year.toString().padStart(4, '0') + two(value.month.ordinal + 1) + two(value.day)

    private fun two(value: Int): String = value.toString().padStart(2, '0')
}

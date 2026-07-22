package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal val RFC_YEAR_RANGE: IntRange = 0..9999
internal val FIRST_RFC_DATE: LocalDate = LocalDate(RFC_YEAR_RANGE.first, 1, 1)
internal val LAST_RFC_DATE: LocalDate = LocalDate(RFC_YEAR_RANGE.last, 12, 31)
internal val LAST_RFC_DATE_TIME: LocalDateTime =
    LocalDateTime(RFC_YEAR_RANGE.last, 12, 31, 23, 59, 59)

internal fun validateRfcTemporalValue(propertyName: String, value: RecurrenceDateTime) {
    validateRfcYear(propertyName, value)
    val hasSubsecondPrecision = when (value) {
        is RecurrenceDateTime.DateOnly -> false
        is RecurrenceDateTime.Floating -> value.dateTime.nanosecond != 0
        is RecurrenceDateTime.Utc -> value.instant.nanosecondsOfSecond != 0
        is RecurrenceDateTime.Zoned -> value.dateTime.nanosecond != 0
    }
    if (hasSubsecondPrecision) {
        throw RecurrenceValidationException(
            propertyName = propertyName,
            invalidToken = value.toString(),
            reason = RecurrenceErrorReason.INVALID_COMBINATION,
            detail = "RFC 5545 DATE-TIME values have whole-second precision",
        )
    }
}

internal fun validateRfcYear(propertyName: String, value: RecurrenceDateTime) {
    val year = runCatching {
        when (value) {
            is RecurrenceDateTime.DateOnly -> value.date.year
            is RecurrenceDateTime.Floating -> value.dateTime.year
            is RecurrenceDateTime.Utc -> value.instant.toLocalDateTime(TimeZone.UTC).year
            is RecurrenceDateTime.Zoned -> value.dateTime.year
        }
    }.getOrNull()
    if (year == null || year !in RFC_YEAR_RANGE) {
        throw RecurrenceValidationException(
            propertyName = propertyName,
            invalidToken = value.toString(),
            reason = RecurrenceErrorReason.OUT_OF_RANGE,
            detail = "RFC 5545 DATE values use a four-digit year",
        )
    }
}

internal fun validateRecurrenceTemporalDomain(
    start: RecurrenceDateTime,
    propertyName: String,
    value: RecurrenceDateTime,
) {
    val compatible = when (start) {
        is RecurrenceDateTime.DateOnly -> value is RecurrenceDateTime.DateOnly
        is RecurrenceDateTime.Floating -> value is RecurrenceDateTime.Floating
        is RecurrenceDateTime.Utc, is RecurrenceDateTime.Zoned ->
            value is RecurrenceDateTime.Utc || value is RecurrenceDateTime.Zoned
    }
    if (!compatible) {
        throw RecurrenceValidationException(
            propertyName = propertyName,
            invalidToken = value.toString(),
            reason = RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
            detail = "$propertyName values must use the DTSTART temporal domain",
        )
    }
}

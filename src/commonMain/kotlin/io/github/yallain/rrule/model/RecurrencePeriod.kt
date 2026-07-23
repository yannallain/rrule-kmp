package io.github.yallain.rrule

/**
 * A positive RFC 5545 duration used by an `RDATE;VALUE=PERIOD` value.
 *
 * Weeks and days are nominal calendar units; hours, minutes, and seconds are accurate clock
 * units. Components are intentionally not normalized because `P1D` and `PT24H` can have
 * different elapsed lengths across a timezone transition. RFC week form cannot be combined with
 * day or clock components.
 */
public data class RecurrenceDuration(
    public val weeks: Int = 0,
    public val days: Int = 0,
    public val hours: Int = 0,
    public val minutes: Int = 0,
    public val seconds: Int = 0,
) {
    init {
        val components = listOf(weeks, days, hours, minutes, seconds)
        if (components.any { it < 0 }) {
            throw RecurrenceValidationException(
                propertyName = "DURATION",
                invalidToken = toString(),
                reason = RecurrenceErrorReason.OUT_OF_RANGE,
                detail = "An RDATE PERIOD duration must not contain negative components",
            )
        }
        if (components.none { it > 0 }) {
            throw RecurrenceValidationException(
                propertyName = "DURATION",
                invalidToken = toString(),
                reason = RecurrenceErrorReason.OUT_OF_RANGE,
                detail = "An RDATE PERIOD duration must be positive",
            )
        }
        if (weeks > 0 && components.drop(1).any { it > 0 }) {
            throw RecurrenceValidationException(
                propertyName = "DURATION",
                invalidToken = toString(),
                reason = RecurrenceErrorReason.INVALID_COMBINATION,
                detail = "RFC 5545 week duration form cannot be combined with day or clock components",
            )
        }
    }
}

/**
 * A precise recurrence period supplied by `RDATE;VALUE=PERIOD`.
 *
 * PERIOD values always use DATE-TIME values, never DATE values. [Explicit] preserves an end value;
 * [WithDuration] preserves the RFC duration form. A period's [start] participates in the recurrence
 * set just like an ordinary `RDATE`; the remaining metadata describes that specific instance.
 */
public sealed interface RecurrencePeriod {
    /** Start DATE-TIME contributed to the recurrence set. */
    public val start: RecurrenceDateTime

    /** A period represented by an explicit start and end DATE-TIME. */
    public data class Explicit(
        override val start: RecurrenceDateTime,
        public val end: RecurrenceDateTime,
    ) : RecurrencePeriod {
        init {
            validatePeriodDateTime(start, role = "start")
            validatePeriodDateTime(end, role = "end")
            validateExplicitPeriodRepresentation(start, end)
            validateLocallyOrderedPeriod(start, end)
        }
    }

    /** A period represented by a start DATE-TIME and a positive [duration]. */
    public data class WithDuration(
        override val start: RecurrenceDateTime,
        public val duration: RecurrenceDuration,
    ) : RecurrencePeriod {
        init {
            validatePeriodDateTime(start, role = "start")
        }
    }
}

private fun validatePeriodDateTime(value: RecurrenceDateTime, role: String) {
    validateRfcTemporalValue("RDATE", value)
    if (value is RecurrenceDateTime.DateOnly) {
        throw RecurrenceValidationException(
            propertyName = "RDATE",
            invalidToken = value.toString(),
            reason = RecurrenceErrorReason.INVALID_COMBINATION,
            detail = "An RDATE PERIOD $role must be a DATE-TIME value",
        )
    }
}

private fun validateExplicitPeriodRepresentation(
    start: RecurrenceDateTime,
    end: RecurrenceDateTime,
) {
    val compatible = when (start) {
        is RecurrenceDateTime.DateOnly -> false
        is RecurrenceDateTime.Floating -> end is RecurrenceDateTime.Floating
        is RecurrenceDateTime.Utc -> end is RecurrenceDateTime.Utc
        is RecurrenceDateTime.Zoned -> end is RecurrenceDateTime.Zoned &&
            end.timeZoneId == start.timeZoneId
    }
    if (!compatible) {
        throw RecurrenceValidationException(
            propertyName = "RDATE",
            invalidToken = end.toString(),
            reason = RecurrenceErrorReason.INCOMPATIBLE_TEMPORAL_TYPE,
            detail = "An explicit RDATE PERIOD start and end must use the same temporal representation",
        )
    }
}

private fun validateLocallyOrderedPeriod(
    start: RecurrenceDateTime,
    end: RecurrenceDateTime,
) {
    val comparison = when (start) {
        is RecurrenceDateTime.DateOnly -> return
        is RecurrenceDateTime.Floating -> start.dateTime.compareTo((end as RecurrenceDateTime.Floating).dateTime)
        is RecurrenceDateTime.Utc -> start.instant.compareTo((end as RecurrenceDateTime.Utc).instant)
        is RecurrenceDateTime.Zoned -> start.dateTime.compareTo((end as RecurrenceDateTime.Zoned).dateTime)
    }
    if (comparison >= 0) {
        throw RecurrenceValidationException(
            propertyName = "RDATE",
            invalidToken = end.toString(),
            reason = RecurrenceErrorReason.OUT_OF_RANGE,
            detail = "An explicit RDATE PERIOD start must be before its end",
        )
    }
}

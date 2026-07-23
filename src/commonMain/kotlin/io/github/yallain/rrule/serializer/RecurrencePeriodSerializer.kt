package io.github.yallain.rrule

/** Canonical serializer for the value portion of an RFC 5545 PERIOD. */
public object RecurrencePeriodSerializer {
    /**
     * Serializes [period] without the surrounding `RDATE;VALUE=PERIOD:` content-line header.
     *
     * A zoned value's `TZID` is a content-line parameter and is therefore intentionally absent
     * from this value-only representation. Both endpoints have already been validated to use the
     * same temporal representation.
     */
    public fun serialize(period: RecurrencePeriod): String = when (period) {
        is RecurrencePeriod.Explicit ->
            serializeDateTime(period.start) + "/" + serializeDateTime(period.end)
        is RecurrencePeriod.WithDuration ->
            serializeDateTime(period.start) + "/" + serializeDuration(period.duration)
    }

    private fun serializeDateTime(value: RecurrenceDateTime): String =
        RfcTemporalValueSerializer.serialize(value, propertyName = "RDATE", allowZoned = true)

    private fun serializeDuration(value: RecurrenceDuration): String {
        if (value.weeks > 0) return "P${value.weeks}W"
        return buildString {
            append('P')
            if (value.days > 0) append(value.days).append('D')
            if (value.hours > 0 || value.minutes > 0 || value.seconds > 0) {
                append('T')
                if (value.hours > 0) append(value.hours).append('H')
                if (value.minutes > 0 || value.hours > 0 && value.seconds > 0) {
                    append(value.minutes).append('M')
                }
                if (value.seconds > 0) append(value.seconds).append('S')
            }
        }
    }
}

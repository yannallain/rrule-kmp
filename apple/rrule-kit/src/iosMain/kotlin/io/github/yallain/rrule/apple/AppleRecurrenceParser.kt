package io.github.yallain.rrule.apple

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime

/** Parses RFC recurrence content into the instant-oriented native Apple facade. */
public object AppleRecurrenceParser {
    /** Parses [content] using the earlier instant when a local time is ambiguous. */
    @Throws(AppleRecurrenceException::class)
    public fun parse(content: String): AppleRecurrenceSchedule =
        parse(content, preferLaterOffsetAtOverlap = false)

    /**
     * Parses [content] and optionally selects the later instant during a fall-back overlap.
     *
     * The Apple facade accepts only UTC or `TZID`-bearing starts because floating and date-only
     * values cannot be converted to Foundation dates without application-owned context.
     */
    @Throws(AppleRecurrenceException::class)
    public fun parse(
        content: String,
        preferLaterOffsetAtOverlap: Boolean,
    ): AppleRecurrenceSchedule = appleRecurrenceBoundary(
        fallbackCode = AppleRecurrenceErrorCode.INVALID_CONTENT,
    ) {
        val definition = RecurrenceContentParser.parse(content)
        val timeZoneIdentifier = when (val start = definition.start) {
            is RecurrenceDateTime.Utc -> null
            is RecurrenceDateTime.Zoned -> start.timeZoneId
            is RecurrenceDateTime.DateOnly,
            is RecurrenceDateTime.Floating,
            -> throw AppleRecurrenceException(
                code = AppleRecurrenceErrorCode.UNSUPPORTED_TEMPORAL_DOMAIN,
                inputValue = content,
                reason = null,
                propertyName = "DTSTART",
                invalidToken = null,
                position = null,
                maximumCount = null,
                message = "The Apple facade requires a UTC or TZID-bearing DTSTART",
            )
        }
        val ambiguityPolicy = if (preferLaterOffsetAtOverlap) {
            AmbiguousTimePolicy.LATER
        } else {
            AmbiguousTimePolicy.EARLIER
        }
        AppleRecurrenceSchedule(
            recurrenceSet = definition.recurrenceSet(
                ambiguousTimePolicy = ambiguityPolicy,
            ),
            timeZoneIdentifier = timeZoneIdentifier,
        )
    }
}

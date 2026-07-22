package io.github.yallain.rrule

/**
 * A weekday selector, optionally qualified by a non-zero ordinal.
 *
 * Whether an ordinal is legal also depends on the containing rule's frequency.
 */
public data class ByDay(
    public val weekday: Weekday,
    public val ordinal: Int? = null,
) {
    init {
        if (ordinal == 0 || ordinal != null && ordinal !in -53..53) {
            throw RecurrenceValidationException(
                propertyName = "BYDAY",
                invalidToken = ordinal.toString(),
                reason = RecurrenceErrorReason.OUT_OF_RANGE,
                detail = "A BYDAY ordinal must be in -53..-1 or 1..53",
            )
        }
    }
}

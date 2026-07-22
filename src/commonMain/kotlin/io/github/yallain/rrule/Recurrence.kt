package io.github.yallain.rrule

/** A lazily evaluated recurrence in one well-defined temporal domain. */
public interface Recurrence {
    /** Returns occurrences in chronological order without eagerly materializing the recurrence. */
    public fun occurrences(): Sequence<RecurrenceDateTime>

    /** Returns occurrences in `[startInclusive, endExclusive)`, stopping at [limit] when supplied. */
    public fun between(
        startInclusive: RecurrenceDateTime,
        endExclusive: RecurrenceDateTime,
        limit: Int? = null,
    ): List<RecurrenceDateTime>

    /** Returns the first occurrence after [value], or at [value] when [inclusive] is true. */
    public fun after(value: RecurrenceDateTime, inclusive: Boolean = false): RecurrenceDateTime?

    /** Returns the final occurrence before [value], or at [value] when [inclusive] is true. */
    public fun before(value: RecurrenceDateTime, inclusive: Boolean = false): RecurrenceDateTime?
}

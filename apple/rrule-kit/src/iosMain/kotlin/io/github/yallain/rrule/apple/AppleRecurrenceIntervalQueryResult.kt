package io.github.yallain.rrule.apple

/**
 * A bounded interval query result.
 *
 * [hasMore] is true when at least one additional overlapping interval exists beyond [intervals].
 * Native Swift consumers must not treat a truncated result as a complete payroll calculation.
 */
public class AppleRecurrenceIntervalQueryResult internal constructor(
    intervals: List<AppleRecurrenceInterval>,
    public val hasMore: Boolean,
) {
    /** A detached, read-only snapshot of the intervals returned by the query. */
    public val intervals: List<AppleRecurrenceInterval> = ImmutableAppleList(intervals)
}

private class ImmutableAppleList<Element>(elements: Iterable<Element>) : AbstractList<Element>() {
    private val elements: List<Element> = elements.toList()

    override val size: Int
        get() = elements.size

    override fun get(index: Int): Element = elements[index]
}

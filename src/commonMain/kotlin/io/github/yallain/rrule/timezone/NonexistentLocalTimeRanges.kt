package io.github.yallain.rrule

import kotlinx.datetime.LocalDateTime

/** A complete half-open interval of local fields skipped by a forward offset transition. */
internal data class NonexistentLocalTimeRange(
    val startInclusive: LocalDateTime,
    val endExclusive: LocalDateTime,
) {
    init {
        require(startInclusive < endExclusive) {
            "A nonexistent local-time range must have a positive duration"
        }
    }

    fun contains(value: LocalDateTime): Boolean =
        value >= startInclusive && value < endExclusive
}

/**
 * Internal exact-indexing capability for resolvers with a non-linear local timeline.
 *
 * Implementations return every complete gap intersecting the requested local interval, sorted and
 * non-overlapping. Returning complete rather than clipped gaps lets the count index jump across a
 * gap when its provisional ordinal lands inside that gap.
 */
internal fun interface NonexistentLocalTimeRangeProvider {
    fun nonexistentLocalTimeRanges(
        timeZoneId: String,
        startInclusive: LocalDateTime,
        endInclusive: LocalDateTime,
    ): List<NonexistentLocalTimeRange>
}

package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * A chronologically ordered candidate clock that can answer rank and address queries directly.
 *
 * This is the clock portion of counted-prefix indexing. It remains separate from recurrence
 * generation so [PeriodGenerator] can focus on RFC expansion and filtering.
 */
internal data class CountedCandidateClock(
    val hours: List<Int>,
    val minutes: List<Int>,
    val seconds: List<Int>,
) {
    val size: Long = hours.size.toLong() * minutes.size * seconds.size

    fun rank(dateTime: LocalDateTime, inclusive: Boolean): Long {
        if (size == 0L) return 0L
        val earlierHours = hours.count { it < dateTime.hour }.toLong()
        var result = earlierHours * minutes.size * seconds.size
        if (dateTime.hour !in hours) return result

        val earlierMinutes = minutes.count { it < dateTime.minute }.toLong()
        result += earlierMinutes * seconds.size
        if (dateTime.minute !in minutes) return result

        result += seconds.count { it < dateTime.second }
        if (dateTime.second in seconds && (inclusive || dateTime.nanosecond > 0)) result++
        return result
    }

    fun valueAt(oneBasedOrdinal: Long): Triple<Int, Int, Int>? {
        if (oneBasedOrdinal !in 1L..size) return null
        var index = oneBasedOrdinal - 1L
        val valuesPerHour = minutes.size.toLong() * seconds.size
        val hour = hours[(index / valuesPerHour).toInt()]
        index %= valuesPerHour
        val minute = minutes[(index / seconds.size).toInt()]
        val second = seconds[(index % seconds.size).toInt()]
        return Triple(hour, minute, second)
    }

    companion object {
        val EMPTY: CountedCandidateClock = CountedCandidateClock(
            hours = emptyList(),
            minutes = emptyList(),
            seconds = emptyList(),
        )
    }
}

/** Candidate dates and optional clock fields addressable by one-based chronological ordinal. */
internal class CountedCandidateSpace(
    private val dates: List<LocalDate>,
    private val clock: CountedCandidateClock? = null,
) {
    private val valuesPerDate: Long = clock?.size ?: 1L
    val size: Long = dates.size.toLong() * valuesPerDate

    fun rank(bound: LocalValue, inclusive: Boolean): Long {
        val earlierDateCount = dates.count { it < bound.date }.toLong()
        var result = earlierDateCount * valuesPerDate
        if (bound.date !in dates) return result
        if (clock == null) return result + if (inclusive) 1L else 0L
        return result + clock.rank(checkNotNull(bound.dateTime), inclusive)
    }

    fun candidateAt(oneBasedOrdinal: Long): LocalCandidate? {
        if (oneBasedOrdinal !in 1L..size || valuesPerDate == 0L) return null
        val zeroBasedOrdinal = oneBasedOrdinal - 1L
        val date = dates[(zeroBasedOrdinal / valuesPerDate).toInt()]
        val candidateClock = clock ?: return DateCandidate(date)
        val clockOrdinal = zeroBasedOrdinal % valuesPerDate + 1L
        val (hour, minute, second) = candidateClock.valueAt(clockOrdinal) ?: return null
        return DateTimeCandidate(localDateTime(date, hour, minute, second))
    }
}

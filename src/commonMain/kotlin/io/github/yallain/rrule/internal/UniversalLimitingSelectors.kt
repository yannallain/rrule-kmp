package io.github.yallain.rrule

/**
 * Normalizes a selector after removing only a complete domain used as an RFC limitation.
 *
 * The same selector is deliberately retained when it expands a coarser frequency. For example,
 * all 60 minutes limit a `MINUTELY` rule without changing it, but expand an `HOURLY` rule from one
 * default minute to 60 candidates. These narrow proofs are shared by recurrence-set equivalence
 * and counted-prefix indexing so correctness and performance use one definition of “no-op”.
 * Signed date selectors are universal only when they cover every position for every possible
 * month or year length.
 */
internal fun RecurrenceRule.normalizedNonUniversalBySecond(): Set<Int> {
    val normalized = bySecond.normalizeRfcSeconds()
    return if (frequency == Frequency.SECONDLY && normalized == ALL_SECONDS) emptySet() else normalized
}

internal fun RecurrenceRule.normalizedNonUniversalByMinute(): Set<Int> =
    if (frequency.limitsByMinute() && byMinute == ALL_MINUTES) emptySet() else byMinute

internal fun RecurrenceRule.normalizedNonUniversalByHour(): Set<Int> =
    if (frequency.limitsByHour() && byHour == ALL_HOURS) emptySet() else byHour

internal fun RecurrenceRule.normalizedNonUniversalByDay(): List<ByDay> =
    if (frequency.limitsByDay() && byDay.isEveryUnqualifiedWeekday()) emptyList() else byDay

internal fun RecurrenceRule.normalizedNonUniversalByMonthDay(): Set<Int> =
    if (frequency.limitsByMonthDay() && byMonthDay.coversEverySignedPosition(28..31)) {
        emptySet()
    } else {
        byMonthDay
    }

internal fun RecurrenceRule.normalizedNonUniversalByYearDay(): Set<Int> =
    if (frequency.limitsByYearDay() && byYearDay.coversEverySignedPosition(365..366)) {
        emptySet()
    } else {
        byYearDay
    }

internal fun RecurrenceRule.normalizedNonUniversalByMonth(): Set<Int> =
    if (frequency.limitsByMonth() && byMonth == ALL_MONTHS) emptySet() else byMonth

private fun Frequency.limitsByMinute(): Boolean = when (this) {
    Frequency.SECONDLY, Frequency.MINUTELY -> true
    Frequency.HOURLY, Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> false
}

private fun Frequency.limitsByHour(): Boolean = when (this) {
    Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY -> true
    Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> false
}

private fun Frequency.limitsByDay(): Boolean = when (this) {
    Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY, Frequency.DAILY -> true
    Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> false
}

private fun Frequency.limitsByMonthDay(): Boolean = when (this) {
    Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY, Frequency.DAILY -> true
    Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> false
}

private fun Frequency.limitsByYearDay(): Boolean = when (this) {
    Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY -> true
    Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY -> false
}

private fun Frequency.limitsByMonth(): Boolean = when (this) {
    Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY,
    Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY,
    -> true
    Frequency.YEARLY -> false
}

private fun List<ByDay>.isEveryUnqualifiedWeekday(): Boolean =
    size == Weekday.entries.size &&
        all { it.ordinal == null } &&
        mapTo(hashSetOf(), ByDay::weekday).size == Weekday.entries.size

private fun Set<Int>.coversEverySignedPosition(lengths: IntRange): Boolean =
    lengths.all { length ->
        (1..length).all { position ->
            position in this || position - length - 1 in this
        }
    }

private val ALL_SECONDS: Set<Int> = (0..59).toSet()
private val ALL_MINUTES: Set<Int> = ALL_SECONDS
private val ALL_HOURS: Set<Int> = (0..23).toSet()
private val ALL_MONTHS: Set<Int> = (1..12).toSet()

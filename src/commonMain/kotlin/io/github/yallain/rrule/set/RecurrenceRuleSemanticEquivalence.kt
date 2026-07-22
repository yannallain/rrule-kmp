package io.github.yallain.rrule

/**
 * Returns whether two rules are provably the same recurrence stream after removing selectors
 * that cover their complete limiting domain.
 *
 * This deliberately recognizes only narrow, mechanically provable identities. In particular, a
 * selector is not removed when RFC 5545 uses it to expand a coarser frequency: every second is a
 * no-op for `SECONDLY`, for example, but expands a `MINUTELY` rule. Keeping this proof conservative
 * prevents a recurrence set from cancelling inclusion and exclusion rules merely because they
 * happen to look similar.
 */
internal fun RecurrenceRule.isProvablySemanticallyEquivalentTo(other: RecurrenceRule): Boolean =
    frequency == other.frequency &&
        interval == other.interval &&
        count == other.count &&
        until == other.until &&
        weekStart == other.weekStart &&
        normalizedNonUniversalBySecond() == other.normalizedNonUniversalBySecond() &&
        normalizedNonUniversalByMinute() == other.normalizedNonUniversalByMinute() &&
        normalizedNonUniversalByHour() == other.normalizedNonUniversalByHour() &&
        normalizedNonUniversalByDay() == other.normalizedNonUniversalByDay() &&
        byMonthDay == other.byMonthDay &&
        byYearDay == other.byYearDay &&
        byWeekNumber == other.byWeekNumber &&
        normalizedNonUniversalByMonth() == other.normalizedNonUniversalByMonth() &&
        bySetPosition == other.bySetPosition

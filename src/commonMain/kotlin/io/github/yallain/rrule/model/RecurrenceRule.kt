package io.github.yallain.rrule

import io.github.yallain.rrule.internal.collections.immutableListCopyOf
import io.github.yallain.rrule.internal.collections.immutableSetCopyOf

/**
 * An immutable, validated RFC 5545 recurrence rule.
 *
 * Collection arguments are defensively copied into read-only implementations that cannot be
 * mutated through unchecked Kotlin casts or Java collection APIs. `UNTIL` compatibility with
 * `DTSTART` is validated when the rule is bound to a recurrence start, because a standalone RRULE
 * has no start value.
 */
public class RecurrenceRule(
    public val frequency: Frequency,
    public val interval: Int = 1,
    public val count: Int? = null,
    public val until: RecurrenceDateTime? = null,
    public val weekStart: Weekday = Weekday.MONDAY,
    bySecond: Set<Int> = emptySet(),
    byMinute: Set<Int> = emptySet(),
    byHour: Set<Int> = emptySet(),
    byDay: List<ByDay> = emptyList(),
    byMonthDay: Set<Int> = emptySet(),
    byYearDay: Set<Int> = emptySet(),
    byWeekNumber: Set<Int> = emptySet(),
    byMonth: Set<Int> = emptySet(),
    bySetPosition: Set<Int> = emptySet(),
) {
    public val bySecond: Set<Int> = immutableSetCopyOf(bySecond)
    public val byMinute: Set<Int> = immutableSetCopyOf(byMinute)
    public val byHour: Set<Int> = immutableSetCopyOf(byHour)
    public val byDay: List<ByDay> = immutableListCopyOf(
        byDay
            .distinct()
            .sortedWith(compareBy<ByDay>({ it.ordinal ?: 0 }, { it.weekday.ordinal })),
    )
    public val byMonthDay: Set<Int> = immutableSetCopyOf(byMonthDay)
    public val byYearDay: Set<Int> = immutableSetCopyOf(byYearDay)
    public val byWeekNumber: Set<Int> = immutableSetCopyOf(byWeekNumber)
    public val byMonth: Set<Int> = immutableSetCopyOf(byMonth)
    public val bySetPosition: Set<Int> = immutableSetCopyOf(bySetPosition)

    init {
        RecurrenceRuleValidator.validate(this)
    }

    /** Returns a validated rule with selected values replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        frequency: Frequency = this.frequency,
        interval: Int = this.interval,
        count: Int? = this.count,
        until: RecurrenceDateTime? = this.until,
        weekStart: Weekday = this.weekStart,
        bySecond: Set<Int> = this.bySecond,
        byMinute: Set<Int> = this.byMinute,
        byHour: Set<Int> = this.byHour,
        byDay: List<ByDay> = this.byDay,
        byMonthDay: Set<Int> = this.byMonthDay,
        byYearDay: Set<Int> = this.byYearDay,
        byWeekNumber: Set<Int> = this.byWeekNumber,
        byMonth: Set<Int> = this.byMonth,
        bySetPosition: Set<Int> = this.bySetPosition,
    ): RecurrenceRule = RecurrenceRule(
        frequency = frequency,
        interval = interval,
        count = count,
        until = until,
        weekStart = weekStart,
        bySecond = bySecond,
        byMinute = byMinute,
        byHour = byHour,
        byDay = byDay,
        byMonthDay = byMonthDay,
        byYearDay = byYearDay,
        byWeekNumber = byWeekNumber,
        byMonth = byMonth,
        bySetPosition = bySetPosition,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is RecurrenceRule &&
            frequency == other.frequency &&
            interval == other.interval &&
            count == other.count &&
            until == other.until &&
            weekStart == other.weekStart &&
            bySecond == other.bySecond &&
            byMinute == other.byMinute &&
            byHour == other.byHour &&
            byDay == other.byDay &&
            byMonthDay == other.byMonthDay &&
            byYearDay == other.byYearDay &&
            byWeekNumber == other.byWeekNumber &&
            byMonth == other.byMonth &&
            bySetPosition == other.bySetPosition

    override fun hashCode(): Int {
        var result = frequency.hashCode()
        result = 31 * result + interval
        result = 31 * result + (count ?: 0)
        result = 31 * result + (until?.hashCode() ?: 0)
        result = 31 * result + weekStart.hashCode()
        result = 31 * result + bySecond.hashCode()
        result = 31 * result + byMinute.hashCode()
        result = 31 * result + byHour.hashCode()
        result = 31 * result + byDay.hashCode()
        result = 31 * result + byMonthDay.hashCode()
        result = 31 * result + byYearDay.hashCode()
        result = 31 * result + byWeekNumber.hashCode()
        result = 31 * result + byMonth.hashCode()
        result = 31 * result + bySetPosition.hashCode()
        return result
    }

    override fun toString(): String = buildString {
        append("RecurrenceRule(frequency=").append(frequency)
        append(", interval=").append(interval)
        append(", count=").append(count)
        append(", until=").append(until)
        append(", weekStart=").append(weekStart)
        append(", bySecond=").append(bySecond)
        append(", byMinute=").append(byMinute)
        append(", byHour=").append(byHour)
        append(", byDay=").append(byDay)
        append(", byMonthDay=").append(byMonthDay)
        append(", byYearDay=").append(byYearDay)
        append(", byWeekNumber=").append(byWeekNumber)
        append(", byMonth=").append(byMonth)
        append(", bySetPosition=").append(bySetPosition)
        append(')')
    }
}

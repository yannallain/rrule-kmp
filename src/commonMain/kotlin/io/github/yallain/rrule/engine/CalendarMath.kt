package io.github.yallain.rrule

import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

internal fun LocalDate.plusDaysOrNull(days: Long): LocalDate? = try {
    plus(days, DateTimeUnit.DAY)
} catch (_: DateTimeArithmeticException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

internal fun daysInYear(year: Int): Int = if (isLeapYear(year)) 366 else 365

internal fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

internal fun weekday(date: LocalDate): Weekday = Weekday.entries[date.dayOfWeek.ordinal]

internal fun weekStart(date: LocalDate, weekStart: Weekday): LocalDate? {
    val backwards = floorMod(weekday(date).ordinal - weekStart.ordinal, 7)
    return date.plusDaysOrNull(-backwards.toLong())
}

internal fun firstWeekStart(year: Int, weekStart: Weekday): LocalDate? =
    runCatching { LocalDate(year, 1, 4) }.getOrNull()?.let { weekStart(it, weekStart) }

internal data class WeekInfo(val weekYear: Int, val weekNumber: Int, val weeksInYear: Int)

internal fun weekInfo(date: LocalDate, weekStart: Weekday): WeekInfo? {
    var weekYear = date.year
    var first = firstWeekStart(weekYear, weekStart) ?: return null
    if (date < first) {
        weekYear -= 1
        first = firstWeekStart(weekYear, weekStart) ?: return null
    } else {
        val next = firstWeekStart(weekYear + 1, weekStart) ?: return null
        if (date >= next) {
            weekYear += 1
            first = next
        }
    }
    val next = firstWeekStart(weekYear + 1, weekStart) ?: return null
    val weeks = ((next.toEpochDays() - first.toEpochDays()) / 7).toInt()
    val number = ((date.toEpochDays() - first.toEpochDays()) / 7).toInt() + 1
    return WeekInfo(weekYear, number, weeks)
}

internal fun localDateTime(
    date: LocalDate,
    hour: Int,
    minute: Int,
    second: Int,
): LocalDateTime = LocalDateTime(
    date.year,
    date.month.ordinal + 1,
    date.day,
    hour,
    minute,
    second,
)

internal fun floorMod(value: Int, divisor: Int): Int = ((value % divisor) + divisor) % divisor

internal fun floorDiv(value: Long, divisor: Long): Long {
    var quotient = value / divisor
    if (value % divisor != 0L && (value < 0) != (divisor < 0)) quotient--
    return quotient
}

internal fun floorMod(value: Long, divisor: Long): Long = value - floorDiv(value, divisor) * divisor

internal fun positiveProductOrNull(first: Long, second: Long): Long? {
    if (first < 0 || second < 0) return null
    if (first != 0L && second > Long.MAX_VALUE / first) return null
    return first * second
}

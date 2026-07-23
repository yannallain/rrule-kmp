package io.github.yallain.rrule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Finds reachable HOURLY, MINUTELY, and SECONDLY anchors without unit-by-unit scanning. */
internal class SubDailyPeriodNavigator(
    private val start: LocalDateTime,
    private val rule: RecurrenceRule,
    private val hasDateFilter: Boolean,
    private val matchesDate: (LocalDate) -> Boolean,
) {
    private val unitsPerDay: Int
    private val startUnit: Int
    private val effectiveBySecond: Set<Int> = rule.bySecond.normalizeRfcSeconds()

    init {
        when (rule.frequency) {
            Frequency.HOURLY -> {
                unitsPerDay = 24
                startUnit = start.hour
            }
            Frequency.MINUTELY -> {
                unitsPerDay = 1_440
                startUnit = start.hour * 60 + start.minute
            }
            Frequency.SECONDLY -> {
                unitsPerDay = 86_400
                startUnit = start.hour * 3_600 + start.minute * 60 + start.second
            }
            Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY ->
                error("Sub-daily navigation requires a sub-daily frequency")
        }
    }

    fun anchor(periodIndex: Long): SubDailyAnchor? {
        val elapsed = positiveProductOrNull(periodIndex, rule.interval.toLong()) ?: return null
        if (elapsed > Long.MAX_VALUE - startUnit) return null
        val total = startUnit + elapsed
        val date = start.date.plusDaysOrNull(floorDiv(total, unitsPerDay.toLong())) ?: return null
        if (date.year !in RFC_YEAR_RANGE) return null
        val unitOfDay = floorMod(total, unitsPerDay.toLong()).toInt()
        return SubDailyAnchor(date, unitOfDay)
    }

    fun hasNoReachableClockTime(): Boolean {
        val divisor = greatestCommonDivisor(rule.interval.toLong(), unitsPerDay.toLong())
        return (0 until unitsPerDay).none { unitOfDay ->
            matchesClock(unitOfDay) && floorMod(unitOfDay.toLong() - startUnit, divisor) == 0L
        }
    }

    /**
     * Proves joint calendar-and-clock reachability across the complete Gregorian cycle.
     *
     * Date and clock filters cannot be checked independently: advancing exactly one week from a
     * Monday can never satisfy `BYDAY=TU`. Calendar fields and weekdays repeat after 400 years, and
     * an anchor within that cycle is reachable exactly when its distance from DTSTART is divisible
     * by `gcd(INTERVAL, cycleUnits)`.
     */
    fun hasNoReachableCandidateInGregorianCycle(): Boolean {
        val cycleUnits = GREGORIAN_CYCLE_DAYS * unitsPerDay
        val divisor = greatestCommonDivisor(rule.interval.toLong(), cycleUnits)
        val matchingTimeResidues = hashSetOf<Long>()
        for (unitOfDay in 0 until unitsPerDay) {
            if (matchesClock(unitOfDay)) {
                matchingTimeResidues += floorMod(unitOfDay.toLong() - startUnit, divisor)
            }
        }
        if (matchingTimeResidues.isEmpty()) return true

        val normalizedYear = 2000 + floorMod(start.year.toLong(), 400L).toInt()
        var date = LocalDate(normalizedYear, start.month, start.day)
        repeat(GREGORIAN_CYCLE_DAYS.toInt()) { dayOffset ->
            if (matchesDate(date)) {
                val requiredTimeResidue = floorMod(-dayOffset.toLong() * unitsPerDay, divisor)
                if (requiredTimeResidue in matchingTimeResidues) return false
            }
            date = date.plusDaysOrNull(1) ?: return true
        }
        return true
    }

    fun nextRelevantPeriodIndex(periodIndex: Long): Long? {
        if (periodIndex == Long.MAX_VALUE) return null
        val nextIndex = periodIndex + 1
        if (!hasAnchorFilter()) return nextIndex
        val nextAnchor = anchor(nextIndex) ?: return null
        if (matchesAnchor(nextAnchor)) return nextIndex
        return findRelevantPeriod(nextAnchor, searchForward = true)
    }

    fun previousRelevantPeriodIndex(periodIndex: Long): Long? {
        if (periodIndex == 0L) return null
        val previousIndex = periodIndex - 1
        if (!hasAnchorFilter()) return previousIndex
        val previousAnchor = anchor(previousIndex) ?: return previousIndex
        if (matchesAnchor(previousAnchor)) return previousIndex
        return findRelevantPeriod(previousAnchor, searchForward = false)
    }

    private fun hasAnchorFilter(): Boolean = hasDateFilter || when (rule.frequency) {
        Frequency.HOURLY -> rule.byHour.isNotEmpty()
        Frequency.MINUTELY -> rule.byHour.isNotEmpty() || rule.byMinute.isNotEmpty()
        Frequency.SECONDLY ->
            rule.byHour.isNotEmpty() || rule.byMinute.isNotEmpty() || rule.bySecond.isNotEmpty()
        Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY ->
            error("Sub-daily navigation requires a sub-daily frequency")
    }

    private fun matchesAnchor(anchor: SubDailyAnchor): Boolean =
        matchesDate(anchor.date) && matchesClock(anchor.unitOfDay)

    private fun findRelevantPeriod(
        boundary: SubDailyAnchor,
        searchForward: Boolean,
    ): Long? {
        var date = boundary.date
        while (date.year in RFC_YEAR_RANGE && (searchForward || date >= start.date)) {
            if (matchesDate(date)) {
                val timeBoundary = when {
                    date != boundary.date && searchForward -> 0
                    date != boundary.date -> unitsPerDay - 1
                    else -> boundary.unitOfDay
                }
                findReachableTimeOnDate(date, timeBoundary, searchForward)?.let { return it }
            }
            date = date.plusDaysOrNull(if (searchForward) 1 else -1) ?: return null
        }
        return null
    }

    private fun findReachableTimeOnDate(
        date: LocalDate,
        boundaryUnit: Int,
        searchForward: Boolean,
    ): Long? {
        val interval = rule.interval.toLong()
        val dayOffset = date.toEpochDays() - start.date.toEpochDays()
        val unitsBeforeTime = dayOffset * unitsPerDay - startUnit
        val residue = floorMod(-unitsBeforeTime, interval)
        var time = if (searchForward) {
            residue + ceilingDivision((boundaryUnit.toLong() - residue).coerceAtLeast(0L), interval) * interval
        } else {
            if (residue > boundaryUnit) return null
            residue + (boundaryUnit.toLong() - residue) / interval * interval
        }

        while (time >= 0L && time < unitsPerDay) {
            if (matchesClock(time.toInt())) {
                val elapsedUnits = unitsBeforeTime + time
                if (elapsedUnits >= 0L) return elapsedUnits / interval
            }
            time += if (searchForward) interval else -interval
        }
        return null
    }

    private fun matchesClock(unitOfDay: Int): Boolean = when (rule.frequency) {
        Frequency.HOURLY -> rule.byHour.isEmpty() || unitOfDay in rule.byHour
        Frequency.MINUTELY -> {
            val hour = unitOfDay / 60
            val minute = unitOfDay % 60
            (rule.byHour.isEmpty() || hour in rule.byHour) &&
                (rule.byMinute.isEmpty() || minute in rule.byMinute)
        }
        Frequency.SECONDLY -> {
            val hour = unitOfDay / 3_600
            val minute = unitOfDay % 3_600 / 60
            val second = unitOfDay % 60
            (rule.byHour.isEmpty() || hour in rule.byHour) &&
                (rule.byMinute.isEmpty() || minute in rule.byMinute) &&
                (effectiveBySecond.isEmpty() || second in effectiveBySecond)
        }
        Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY ->
            error("Sub-daily navigation requires a sub-daily frequency")
    }

    private fun greatestCommonDivisor(first: Long, second: Long): Long {
        var left = first
        var right = second
        while (right != 0L) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }

    private fun ceilingDivision(dividend: Long, divisor: Long): Long =
        if (dividend == 0L) 0L else (dividend - 1L) / divisor + 1L

    private companion object {
        const val GREGORIAN_CYCLE_DAYS: Long = 146_097L
    }
}

internal data class SubDailyAnchor(
    val date: LocalDate,
    val unitOfDay: Int,
) {
    fun toPeriodAnchor(frequency: Frequency): PeriodAnchor {
        val secondsPerUnit = when (frequency) {
            Frequency.HOURLY -> 3_600
            Frequency.MINUTELY -> 60
            Frequency.SECONDLY -> 1
            Frequency.YEARLY, Frequency.MONTHLY, Frequency.WEEKLY, Frequency.DAILY ->
                error("A sub-daily anchor requires a sub-daily frequency")
        }
        val secondOfDay = unitOfDay * secondsPerUnit
        return PeriodAnchor(
            date = date,
            hour = secondOfDay / 3_600,
            minute = secondOfDay % 3_600 / 60,
            second = secondOfDay % 60,
            periodYear = date.year,
        )
    }
}

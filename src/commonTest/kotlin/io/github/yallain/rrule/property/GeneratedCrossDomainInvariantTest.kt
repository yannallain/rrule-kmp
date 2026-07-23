package io.github.yallain.rrule.property

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RecurrenceRuleSerializer
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RuleRecurrence
import io.github.yallain.rrule.Weekday
import io.github.yallain.rrule.toInstantOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedCrossDomainInvariantTest {
    @Test
    fun seededRulesRemainOrderedAndQueryEquivalentAcrossDateTimeDomains() {
        val random = Random(RULE_SEED)

        repeat(RULE_CASE_COUNT) { caseIndex ->
            val startFields = LocalDateTime(
                year = random.nextInt(1990, 2031),
                month = random.nextInt(1, 13),
                day = random.nextInt(1, 29),
                hour = 12,
                minute = 0,
            )
            val rule = randomDateTimeRule(random, caseIndex)
            val starts = listOf(
                RecurrenceDateTime.Floating(startFields),
                RecurrenceDateTime.Utc(startFields.toInstant(TimeZone.UTC)),
                RecurrenceDateTime.Zoned(startFields, "Europe/Paris"),
            )

            for (start in starts) {
                assertFiniteRuleInvariants(start, rule, "seed=$RULE_SEED case=$caseIndex")
            }
        }
    }

    @Test
    fun seededDateOnlyRulesCoverSignedOrdinalAndWeekSelectors() {
        val random = Random(DATE_SEED)

        repeat(DATE_CASE_COUNT) { caseIndex ->
            val start = RecurrenceDateTime.DateOnly(
                LocalDate(
                    year = random.nextInt(1990, 2031),
                    month = random.nextInt(1, 13),
                    day = random.nextInt(1, 29),
                ),
            )
            val count = random.nextInt(4, 12)
            val rule = when (caseIndex % 3) {
                0 -> RecurrenceRule(
                    frequency = Frequency.MONTHLY,
                    count = count,
                    byMonthDay = setOf(random.nextInt(1, 29), -1),
                    bySetPosition = setOf(1, -1),
                )
                1 -> RecurrenceRule(
                    frequency = Frequency.YEARLY,
                    count = count,
                    byMonth = setOf(random.nextInt(1, 13)),
                    byDay = listOf(ByDay(Weekday.entries.random(random), ordinal = -1)),
                )
                else -> RecurrenceRule(
                    frequency = Frequency.YEARLY,
                    count = count,
                    weekStart = Weekday.entries.random(random),
                    byWeekNumber = setOf(random.nextInt(1, 53)),
                    byDay = listOf(ByDay(Weekday.entries.random(random))),
                )
            }

            assertFiniteRuleInvariants(start, rule, "date seed=$DATE_SEED case=$caseIndex")
        }
    }

    @Test
    fun seededRecurrenceSetsMatchAnIndependentFiniteMergeOracle() {
        val random = Random(SET_SEED)

        repeat(SET_CASE_COUNT) { caseIndex ->
            val startDateTime = LocalDateTime(
                year = random.nextInt(1995, 2026),
                month = random.nextInt(1, 13),
                day = random.nextInt(1, 22),
                hour = 9,
                minute = 0,
            )
            val start = RecurrenceDateTime.Floating(startDateTime)
            val daily = RecurrenceRule(
                frequency = Frequency.DAILY,
                interval = random.nextInt(1, 4),
                count = random.nextInt(6, 15),
            )
            val weekly = RecurrenceRule(
                frequency = Frequency.WEEKLY,
                count = random.nextInt(4, 10),
                weekStart = Weekday.entries.random(random),
                byDay = listOf(ByDay(Weekday.entries.random(random))),
            )
            val exclusion = RecurrenceRule(
                frequency = Frequency.WEEKLY,
                count = random.nextInt(2, 6),
                byDay = listOf(ByDay(Weekday.entries.random(random))),
            )
            val additionalDates = (0 until 4).mapTo(linkedSetOf()) { offset ->
                val date = startDateTime.date.plus(offset + 2, DateTimeUnit.DAY)
                RecurrenceDateTime.Floating(
                    LocalDateTime(date.year, date.month, date.day, 9, 0),
                )
            }
            val excludedDates = additionalDates.shuffled(random).take(2).toSet()
            val set = RecurrenceSet(
                start = start,
                rules = listOf(daily, weekly),
                exclusionRules = listOf(exclusion),
                additionalDates = additionalDates,
                excludedDates = excludedDates,
            )

            val excludedByRule = RuleRecurrence(start, exclusion).occurrences().toSet()
            val expected = buildSet {
                add(start)
                addAll(RuleRecurrence(start, daily).occurrences())
                addAll(RuleRecurrence(start, weekly).occurrences())
                addAll(additionalDates)
            }.asSequence()
                .filterNot { it in excludedDates || it in excludedByRule }
                .sortedBy { (it as RecurrenceDateTime.Floating).dateTime }
                .toList()
            val actual = set.occurrences().toList()
            val context = "set seed=$SET_SEED case=$caseIndex"

            assertEquals(expected, actual, context)
            assertEquals(actual.distinct(), actual, context)
            assertOrdered(actual, context)
            if (actual.size >= 3) {
                val lower = actual[1]
                val upper = actual[actual.lastIndex]
                assertEquals(actual.subList(1, actual.lastIndex), set.between(lower, upper), context)
                assertEquals(actual[2], set.after(lower), context)
                assertEquals(actual[actual.lastIndex - 1], set.before(upper), context)
            }
        }
    }

    private fun randomDateTimeRule(random: Random, caseIndex: Int): RecurrenceRule {
        val count = random.nextInt(5, 14)
        return when (caseIndex % Frequency.entries.size) {
            0 -> RecurrenceRule(
                frequency = Frequency.YEARLY,
                interval = random.nextInt(1, 4),
                count = count,
                byMonth = setOf(random.nextInt(1, 13)),
                byDay = listOf(ByDay(Weekday.entries.random(random), ordinal = if (random.nextBoolean()) 1 else -1)),
            )
            1 -> RecurrenceRule(
                frequency = Frequency.MONTHLY,
                interval = random.nextInt(1, 4),
                count = count,
                byMonthDay = setOf(random.nextInt(1, 29), -1),
                byHour = setOf(6, 18),
                bySetPosition = setOf(1, -1),
            )
            2 -> RecurrenceRule(
                frequency = Frequency.WEEKLY,
                interval = random.nextInt(1, 4),
                count = count,
                weekStart = Weekday.entries.random(random),
                byDay = Weekday.entries.shuffled(random).take(2).map(::ByDay),
                byHour = setOf(6, 18),
                bySetPosition = setOf(1, -1),
            )
            3 -> RecurrenceRule(
                frequency = Frequency.DAILY,
                interval = random.nextInt(1, 4),
                count = count,
                byHour = setOf(2, 14),
                byMinute = setOf(15, 45),
                bySetPosition = setOf(1, -1),
            )
            4 -> RecurrenceRule(
                frequency = Frequency.HOURLY,
                interval = random.nextInt(1, 5),
                count = count,
                byMinute = setOf(10, 40),
                bySecond = setOf(5, 55),
                bySetPosition = setOf(1, -1),
            )
            5 -> RecurrenceRule(
                frequency = Frequency.MINUTELY,
                interval = random.nextInt(1, 8),
                count = count,
                bySecond = setOf(5, 59, 60),
                bySetPosition = setOf(1, -1),
            )
            else -> RecurrenceRule(
                frequency = Frequency.SECONDLY,
                interval = random.nextInt(1, 8),
                count = count,
                bySecond = setOf(0, 15, 30, 45),
            )
        }
    }

    private fun assertFiniteRuleInvariants(
        start: RecurrenceDateTime,
        rule: RecurrenceRule,
        contextPrefix: String,
    ) {
        val recurrence = RuleRecurrence(start, rule)
        val values = recurrence.occurrences().toList()
        val context = "$contextPrefix start=$start rule=$rule"

        assertEquals(rule.count, values.size, context)
        assertEquals(values.distinct(), values, context)
        assertOrdered(values, context)
        assertMatchesDirectSelectors(values, rule, context)
        assertEquals(rule, RecurrenceRuleParser.parse(RecurrenceRuleSerializer.serialize(rule)), context)
        if (values.size >= 3) {
            val lower = values[1]
            val upper = values[values.lastIndex]
            assertEquals(values.subList(1, values.lastIndex), recurrence.between(lower, upper), context)
            assertEquals(values[2], recurrence.after(lower), context)
            assertEquals(values[values.lastIndex - 1], recurrence.before(upper), context)
            assertEquals(lower, recurrence.after(lower, inclusive = true), context)
            assertEquals(upper, recurrence.before(upper, inclusive = true), context)
        }
    }

    private fun assertOrdered(values: List<RecurrenceDateTime>, context: String) {
        assertTrue(values.zipWithNext().all { (left, right) -> compare(left, right) < 0 }, context)
    }

    private fun assertMatchesDirectSelectors(
        values: List<RecurrenceDateTime>,
        rule: RecurrenceRule,
        context: String,
    ) {
        val normalizedSeconds = rule.bySecond.mapTo(linkedSetOf()) { second ->
            if (second == RFC_LEAP_SECOND) LAST_REPRESENTABLE_SECOND else second
        }

        values.forEachIndexed { index, value ->
            val fields = calendarFields(value)
            val occurrenceContext = "$context occurrence[$index]=$value"
            val monthNumber = fields.date.month.ordinal + 1

            assertTrue(
                rule.byMonth.isEmpty() || monthNumber in rule.byMonth,
                "$occurrenceContext does not satisfy BYMONTH=${rule.byMonth}",
            )
            assertTrue(
                rule.byMonthDay.isEmpty() || rule.byMonthDay.any { selector ->
                    matchesMonthDay(fields.date, selector)
                },
                "$occurrenceContext does not satisfy BYMONTHDAY=${rule.byMonthDay}",
            )
            assertTrue(
                rule.byDay.isEmpty() || rule.byDay.any { selector ->
                    matchesByDay(fields.date, selector, rule)
                },
                "$occurrenceContext does not satisfy BYDAY=${rule.byDay}",
            )

            fields.time?.let { time ->
                assertTrue(
                    rule.byHour.isEmpty() || time.hour in rule.byHour,
                    "$occurrenceContext does not satisfy BYHOUR=${rule.byHour}",
                )
                assertTrue(
                    rule.byMinute.isEmpty() || time.minute in rule.byMinute,
                    "$occurrenceContext does not satisfy BYMINUTE=${rule.byMinute}",
                )
                assertTrue(
                    normalizedSeconds.isEmpty() || time.second in normalizedSeconds,
                    "$occurrenceContext does not satisfy normalized BYSECOND=$normalizedSeconds",
                )
            }
        }
    }

    private fun calendarFields(value: RecurrenceDateTime): CalendarFields = when (value) {
        is RecurrenceDateTime.DateOnly -> CalendarFields(value.date, null)
        is RecurrenceDateTime.Floating -> CalendarFields(value.dateTime.date, value.dateTime.time)
        is RecurrenceDateTime.Utc -> value.instant.toLocalDateTime(TimeZone.UTC).let { dateTime ->
            CalendarFields(dateTime.date, dateTime.time)
        }
        is RecurrenceDateTime.Zoned -> CalendarFields(value.dateTime.date, value.dateTime.time)
    }

    private fun matchesMonthDay(date: LocalDate, selector: Int): Boolean {
        val daysInMonth = independentDaysInMonth(date.year, date.month.ordinal + 1)
        val resolvedDay = if (selector > 0) selector else daysInMonth + selector + 1
        return date.day == resolvedDay
    }

    private fun matchesByDay(
        date: LocalDate,
        selector: ByDay,
        rule: RecurrenceRule,
    ): Boolean {
        if (weekday(date) != selector.weekday) return false
        val ordinal = selector.ordinal ?: return true
        val actualOrdinal = if (rule.frequency == Frequency.YEARLY && rule.byMonth.isEmpty()) {
            ordinalWithinYear(date, ordinal)
        } else {
            ordinalWithinMonth(date, ordinal)
        }
        return actualOrdinal == ordinal
    }

    private fun ordinalWithinMonth(date: LocalDate, requestedOrdinal: Int): Int =
        if (requestedOrdinal > 0) {
            (date.day - 1) / DAYS_PER_WEEK + 1
        } else {
            val daysAfter = independentDaysInMonth(date.year, date.month.ordinal + 1) - date.day
            -(daysAfter / DAYS_PER_WEEK + 1)
        }

    private fun ordinalWithinYear(date: LocalDate, requestedOrdinal: Int): Int {
        val monthNumber = date.month.ordinal + 1
        val dayOfYear = (1 until monthNumber).sumOf { month ->
            independentDaysInMonth(date.year, month)
        } + date.day
        return if (requestedOrdinal > 0) {
            (dayOfYear - 1) / DAYS_PER_WEEK + 1
        } else {
            val daysAfter = independentDaysInYear(date.year) - dayOfYear
            -(daysAfter / DAYS_PER_WEEK + 1)
        }
    }

    private fun weekday(date: LocalDate): Weekday = when (date.dayOfWeek.ordinal) {
        0 -> Weekday.MONDAY
        1 -> Weekday.TUESDAY
        2 -> Weekday.WEDNESDAY
        3 -> Weekday.THURSDAY
        4 -> Weekday.FRIDAY
        5 -> Weekday.SATURDAY
        else -> Weekday.SUNDAY
    }

    private fun independentDaysInYear(year: Int): Int =
        if (independentIsLeapYear(year)) 366 else 365

    private fun independentDaysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (independentIsLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun independentIsLeapYear(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    private fun compare(left: RecurrenceDateTime, right: RecurrenceDateTime): Int = when (left) {
        is RecurrenceDateTime.DateOnly -> left.date.compareTo((right as RecurrenceDateTime.DateOnly).date)
        is RecurrenceDateTime.Floating ->
            left.dateTime.compareTo((right as RecurrenceDateTime.Floating).dateTime)
        is RecurrenceDateTime.Utc -> left.instant.compareTo((right as RecurrenceDateTime.Utc).instant)
        is RecurrenceDateTime.Zoned -> checkNotNull(left.toInstantOrNull()).compareTo(
            checkNotNull((right as RecurrenceDateTime.Zoned).toInstantOrNull()),
        )
    }

    private companion object {
        const val DAYS_PER_WEEK: Int = 7
        const val LAST_REPRESENTABLE_SECOND: Int = 59
        const val RFC_LEAP_SECOND: Int = 60
        const val RULE_SEED: Int = 0x5545_1001
        const val DATE_SEED: Int = 0x5545_1002
        const val SET_SEED: Int = 0x5545_1003
        const val RULE_CASE_COUNT: Int = 84
        const val DATE_CASE_COUNT: Int = 45
        const val SET_CASE_COUNT: Int = 45
    }

    private data class CalendarFields(
        val date: LocalDate,
        val time: LocalTime?,
    )
}

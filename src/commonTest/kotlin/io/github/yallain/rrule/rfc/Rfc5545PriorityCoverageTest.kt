package io.github.yallain.rrule.rfc

import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Regression coverage for RFC branches that are easy to miss in broad example matrices. */
class Rfc5545PriorityCoverageTest {
    @Test
    fun monthlyDefaultsSkipMonthsWithoutTheStartDay() {
        assertFloating(
            start = local(2024, 1, 29),
            rule = "FREQ=MONTHLY;COUNT=5",
            expected = listOf(
                local(2024, 1, 29),
                local(2024, 2, 29),
                local(2024, 3, 29),
                local(2024, 4, 29),
                local(2024, 5, 29),
            ),
        )
        assertFloating(
            start = local(2023, 1, 30),
            rule = "FREQ=MONTHLY;COUNT=5",
            expected = listOf(
                local(2023, 1, 30),
                local(2023, 3, 30),
                local(2023, 4, 30),
                local(2023, 5, 30),
                local(2023, 6, 30),
            ),
        )
    }

    @Test
    fun yearlyOrdinalWeekdayIsScopedByMonth() {
        assertFloating(
            start = local(1997, 9, 28),
            rule = "FREQ=YEARLY;COUNT=3;BYMONTH=9;BYDAY=-1SU",
            expected = listOf(
                local(1997, 9, 28),
                local(1998, 9, 27),
                local(1999, 9, 26),
            ),
        )
    }

    @Test
    fun weekNumberUsesTheDeclaredSundayWeekStart() {
        assertFloating(
            start = local(2019, 12, 29),
            rule = "FREQ=YEARLY;COUNT=3;BYWEEKNO=1;WKST=SU;BYDAY=SU",
            expected = listOf(
                local(2019, 12, 29),
                local(2021, 1, 3),
                local(2022, 1, 2),
            ),
        )
    }

    @Test
    fun negativeWeekNumberUsesTheDeclaredSundayWeekStart() {
        assertFloating(
            start = local(2020, 12, 27),
            rule = "FREQ=YEARLY;COUNT=4;BYWEEKNO=-1;WKST=SU;BYDAY=SU",
            expected = listOf(
                local(2020, 12, 27),
                local(2021, 12, 26),
                local(2022, 12, 25),
                local(2023, 12, 24),
            ),
        )
    }

    @Test
    fun weeklyAndMonthlyPeriodsExpandTimeSelectorsBeforeCount() {
        assertFloating(
            start = local(2024, 1, 1, 6),
            rule = "FREQ=WEEKLY;COUNT=5;BYDAY=MO,WE;BYHOUR=6,18",
            expected = listOf(
                local(2024, 1, 1, 6),
                local(2024, 1, 1, 18),
                local(2024, 1, 3, 6),
                local(2024, 1, 3, 18),
                local(2024, 1, 8, 6),
            ),
        )
        assertFloating(
            start = local(2024, 1, 1, 9, 15),
            rule = "FREQ=MONTHLY;COUNT=5;BYMONTHDAY=1;BYHOUR=9;BYMINUTE=15,45",
            expected = listOf(
                local(2024, 1, 1, 9, 15),
                local(2024, 1, 1, 9, 45),
                local(2024, 2, 1, 9, 15),
                local(2024, 2, 1, 9, 45),
                local(2024, 3, 1, 9, 15),
            ),
        )
    }

    @Test
    fun dateOnlyContentKeepsAllStoredValuesInTheDateDomain() {
        val values = RecurrenceContentParser.parse(
            """
            DTSTART;VALUE=DATE:20240101
            RRULE:FREQ=DAILY;UNTIL=20240104
            RDATE;VALUE=DATE:20240110
            EXDATE;VALUE=DATE:20240102
            """.trimIndent(),
        ).recurrenceSet().occurrences().toList()

        assertEquals(
            listOf(1, 3, 4, 10).map { day ->
                RecurrenceDateTime.DateOnly(LocalDate(2024, 1, day))
            },
            values,
        )
    }

    @Test
    fun rfcEquivalentDailyUntilFormIsCoveredExactly() {
        val values = zonedRecurrence(
            start = LocalDateTime(1998, 1, 1, 9, 0),
            rule = "FREQ=DAILY;UNTIL=20000131T140000Z;BYMONTH=1",
        ).occurrences().map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()

        assertEquals(93, values.size)
        assertEquals(LocalDateTime(1998, 1, 1, 9, 0), values.first())
        assertEquals(LocalDateTime(2000, 1, 31, 9, 0), values.last())
    }

    @Test
    fun rfcWeeklyUntilAndCountFormsAreCoveredExactly() {
        val weeklyUntil = zonedRecurrence(
            start = LocalDateTime(1997, 9, 2, 9, 0),
            rule = "FREQ=WEEKLY;UNTIL=19971224T000000Z",
        ).occurrences().map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()
        assertEquals(17, weeklyUntil.size)
        assertEquals(LocalDateTime(1997, 12, 23, 9, 0), weeklyUntil.last())

        val twiceWeekly = zonedRecurrence(
            start = LocalDateTime(1997, 9, 2, 9, 0),
            rule = "FREQ=WEEKLY;COUNT=10;WKST=SU;BYDAY=TU,TH",
        ).occurrences().map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()
        assertEquals(
            listOf(
                LocalDateTime(1997, 9, 2, 9, 0),
                LocalDateTime(1997, 9, 4, 9, 0),
                LocalDateTime(1997, 9, 9, 9, 0),
                LocalDateTime(1997, 9, 11, 9, 0),
                LocalDateTime(1997, 9, 16, 9, 0),
                LocalDateTime(1997, 9, 18, 9, 0),
                LocalDateTime(1997, 9, 23, 9, 0),
                LocalDateTime(1997, 9, 25, 9, 0),
                LocalDateTime(1997, 9, 30, 9, 0),
                LocalDateTime(1997, 10, 2, 9, 0),
            ),
            twiceWeekly,
        )
    }

    private fun assertFloating(
        start: RecurrenceDateTime.Floating,
        rule: String,
        expected: List<RecurrenceDateTime.Floating>,
    ) {
        assertEquals(
            expected,
            RuleRecurrence(start, RecurrenceRuleParser.parse(rule)).occurrences().toList(),
            rule,
        )
    }

    private fun zonedRecurrence(start: LocalDateTime, rule: String): RuleRecurrence = RuleRecurrence(
        start = RecurrenceDateTime.Zoned(start, "America/New_York"),
        rule = RecurrenceRuleParser.parse(rule),
    )

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 9,
        minute: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute),
    )
}

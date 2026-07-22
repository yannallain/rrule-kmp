package io.github.yallain.rrule.rfc

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Executable examples from RFC 5545 section 3.8.5.3 (pages 123-131). */
class Rfc5545ExamplesTest {
    @Test
    fun dailyExamples() {
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=DAILY;COUNT=10",
            expected = (2..11).map { date(1997, 9, it) },
        )
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=DAILY;INTERVAL=10;COUNT=5",
            expected = listOf(
                date(1997, 9, 2),
                date(1997, 9, 12),
                date(1997, 9, 22),
                date(1997, 10, 2),
                date(1997, 10, 12),
            ),
        )
    }

    @Test
    fun untilAndAlternatingDailyExamples() {
        val until = recurrence("1997-09-02T09:00:00", "FREQ=DAILY;UNTIL=19971224T000000Z")
            .occurrences().map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()
        assertEquals(113, until.size)
        assertEquals(date(1997, 9, 2), until.first())
        assertEquals(date(1997, 12, 23), until.last())

        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=DAILY;INTERVAL=2",
            expected = listOf(
                date(1997, 9, 2), date(1997, 9, 4), date(1997, 9, 6),
                date(1997, 9, 8), date(1997, 9, 10),
            ),
            take = 5,
        )

        val january = recurrence(
            "1998-01-01T09:00:00",
            "FREQ=YEARLY;UNTIL=20000131T140000Z;BYMONTH=1;BYDAY=SU,MO,TU,WE,TH,FR,SA",
        ).occurrences().map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()
        assertEquals(93, january.size)
        assertEquals(date(1998, 1, 1), january.first())
        assertEquals(date(2000, 1, 31), january.last())
    }

    @Test
    fun weeklyExamples() {
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=WEEKLY;COUNT=10",
            expected = listOf(2, 9, 16, 23, 30).map { date(1997, 9, it) } +
                listOf(7, 14, 21, 28).map { date(1997, 10, it) } + date(1997, 11, 4),
        )
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=WEEKLY;INTERVAL=2;COUNT=8;WKST=SU;BYDAY=TU,TH",
            expected = listOf(
                date(1997, 9, 2),
                date(1997, 9, 4),
                date(1997, 9, 16),
                date(1997, 9, 18),
                date(1997, 9, 30),
                date(1997, 10, 2),
                date(1997, 10, 14),
                date(1997, 10, 16),
            ),
        )
    }

    @Test
    fun weeklyUntilAndMultiDayExamples() {
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=WEEKLY;UNTIL=19971007T000000Z;WKST=SU;BYDAY=TU,TH",
            expected = listOf(
                date(1997, 9, 2), date(1997, 9, 4), date(1997, 9, 9), date(1997, 9, 11),
                date(1997, 9, 16), date(1997, 9, 18), date(1997, 9, 23), date(1997, 9, 25),
                date(1997, 9, 30), date(1997, 10, 2),
            ),
        )
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=WEEKLY;INTERVAL=2;WKST=SU",
            expected = listOf(
                date(1997, 9, 2), date(1997, 9, 16), date(1997, 9, 30),
                date(1997, 10, 14), date(1997, 10, 28), date(1997, 11, 11),
                date(1997, 11, 25), date(1997, 12, 9), date(1997, 12, 23),
            ),
            take = 9,
        )
        assertDates(
            start = "1997-09-01T09:00:00",
            rule = "FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;BYDAY=MO,WE,FR",
            expected = listOf(
                date(1997, 9, 1), date(1997, 9, 3), date(1997, 9, 5),
                date(1997, 9, 15), date(1997, 9, 17), date(1997, 9, 19),
                date(1997, 9, 29), date(1997, 10, 1), date(1997, 10, 3),
                date(1997, 10, 13), date(1997, 10, 15), date(1997, 10, 17),
                date(1997, 10, 27), date(1997, 10, 29), date(1997, 10, 31),
                date(1997, 11, 10), date(1997, 11, 12), date(1997, 11, 14),
                date(1997, 11, 24), date(1997, 11, 26), date(1997, 11, 28),
                date(1997, 12, 8), date(1997, 12, 10), date(1997, 12, 12), date(1997, 12, 22),
            ),
        )
    }

    @Test
    fun monthlyOrdinalExamples() {
        assertDates(
            start = "1997-09-05T09:00:00",
            rule = "FREQ=MONTHLY;COUNT=10;BYDAY=1FR",
            expected = listOf(
                date(1997, 9, 5), date(1997, 10, 3), date(1997, 11, 7), date(1997, 12, 5),
                date(1998, 1, 2), date(1998, 2, 6), date(1998, 3, 6), date(1998, 4, 3),
                date(1998, 5, 1), date(1998, 6, 5),
            ),
        )
        assertDates(
            start = "1997-09-07T09:00:00",
            rule = "FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1SU,-1SU",
            expected = listOf(
                date(1997, 9, 7), date(1997, 9, 28), date(1997, 11, 2), date(1997, 11, 30),
                date(1998, 1, 4), date(1998, 1, 25), date(1998, 3, 1), date(1998, 3, 29),
                date(1998, 5, 3), date(1998, 5, 31),
            ),
        )
        assertDates(
            start = "1997-09-22T09:00:00",
            rule = "FREQ=MONTHLY;COUNT=6;BYDAY=-2MO",
            expected = listOf(
                date(1997, 9, 22), date(1997, 10, 20), date(1997, 11, 17),
                date(1997, 12, 22), date(1998, 1, 19), date(1998, 2, 16),
            ),
        )
    }

    @Test
    fun monthlyMonthDayExamples() {
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=MONTHLY;COUNT=10;BYMONTHDAY=2,15",
            expected = listOf(
                date(1997, 9, 2), date(1997, 9, 15), date(1997, 10, 2), date(1997, 10, 15),
                date(1997, 11, 2), date(1997, 11, 15), date(1997, 12, 2), date(1997, 12, 15),
                date(1998, 1, 2), date(1998, 1, 15),
            ),
        )
        assertDates(
            start = "1997-09-30T09:00:00",
            rule = "FREQ=MONTHLY;COUNT=10;BYMONTHDAY=1,-1",
            expected = listOf(
                date(1997, 9, 30), date(1997, 10, 1), date(1997, 10, 31),
                date(1997, 11, 1), date(1997, 11, 30), date(1997, 12, 1),
                date(1997, 12, 31), date(1998, 1, 1), date(1998, 1, 31), date(1998, 2, 1),
            ),
        )
    }

    @Test
    fun monthlyIntervalAndWeekdayExamples() {
        assertDates(
            start = "1997-09-10T09:00:00",
            rule = "FREQ=MONTHLY;INTERVAL=18;COUNT=10;BYMONTHDAY=10,11,12,13,14,15",
            expected = listOf(
                date(1997, 9, 10), date(1997, 9, 11), date(1997, 9, 12),
                date(1997, 9, 13), date(1997, 9, 14), date(1997, 9, 15),
                date(1999, 3, 10), date(1999, 3, 11), date(1999, 3, 12), date(1999, 3, 13),
            ),
        )
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=MONTHLY;INTERVAL=2;BYDAY=TU",
            expected = listOf(
                date(1997, 9, 2), date(1997, 9, 9), date(1997, 9, 16),
                date(1997, 9, 23), date(1997, 9, 30),
                date(1997, 11, 4), date(1997, 11, 11), date(1997, 11, 18), date(1997, 11, 25),
            ),
            take = 9,
        )
        assertDates(
            start = "1997-09-05T09:00:00",
            rule = "FREQ=MONTHLY;UNTIL=19971224T000000Z;BYDAY=1FR",
            expected = listOf(
                date(1997, 9, 5), date(1997, 10, 3), date(1997, 11, 7), date(1997, 12, 5),
            ),
        )
        assertDates(
            start = "1997-09-28T09:00:00",
            rule = "FREQ=MONTHLY;BYMONTHDAY=-3",
            expected = listOf(
                date(1997, 9, 28), date(1997, 10, 29), date(1997, 11, 28),
                date(1997, 12, 29), date(1998, 1, 29), date(1998, 2, 26),
            ),
            take = 6,
        )
    }

    @Test
    fun yearlyDefaultsAndYearDayExamples() {
        assertDates(
            start = "1997-06-10T09:00:00",
            rule = "FREQ=YEARLY;COUNT=10;BYMONTH=6,7",
            expected = (1997..2001).flatMap { year -> listOf(date(year, 6, 10), date(year, 7, 10)) },
        )
        assertDates(
            start = "1997-01-01T09:00:00",
            rule = "FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200",
            expected = listOf(
                date(1997, 1, 1), date(1997, 4, 10), date(1997, 7, 19),
                date(2000, 1, 1), date(2000, 4, 9), date(2000, 7, 18),
                date(2003, 1, 1), date(2003, 4, 10), date(2003, 7, 19),
                date(2006, 1, 1),
            ),
        )
    }

    @Test
    fun yearlyMonthAndWeekdayFilterExamples() {
        assertDates(
            start = "1997-03-10T09:00:00",
            rule = "FREQ=YEARLY;INTERVAL=2;COUNT=10;BYMONTH=1,2,3",
            expected = listOf(
                date(1997, 3, 10),
                date(1999, 1, 10), date(1999, 2, 10), date(1999, 3, 10),
                date(2001, 1, 10), date(2001, 2, 10), date(2001, 3, 10),
                date(2003, 1, 10), date(2003, 2, 10), date(2003, 3, 10),
            ),
        )
        assertDates(
            start = "1997-03-13T09:00:00",
            rule = "FREQ=YEARLY;BYMONTH=3;BYDAY=TH",
            expected = listOf(
                date(1997, 3, 13), date(1997, 3, 20), date(1997, 3, 27),
                date(1998, 3, 5), date(1998, 3, 12), date(1998, 3, 19), date(1998, 3, 26),
            ),
            take = 7,
        )
        assertDates(
            start = "1997-06-05T09:00:00",
            rule = "FREQ=YEARLY;BYDAY=TH;BYMONTH=6,7,8",
            expected = listOf(
                date(1997, 6, 5), date(1997, 6, 12), date(1997, 6, 19), date(1997, 6, 26),
                date(1997, 7, 3), date(1997, 7, 10), date(1997, 7, 17), date(1997, 7, 24),
                date(1997, 7, 31), date(1997, 8, 7), date(1997, 8, 14), date(1997, 8, 21),
                date(1997, 8, 28),
            ),
            take = 13,
        )
    }

    @Test
    fun yearlyWeekdayAndWeekNumberExamples() {
        assertDates(
            start = "1997-05-19T09:00:00",
            rule = "FREQ=YEARLY;BYDAY=20MO",
            expected = listOf(date(1997, 5, 19), date(1998, 5, 18), date(1999, 5, 17)),
            take = 3,
        )
        assertDates(
            start = "1997-05-12T09:00:00",
            rule = "FREQ=YEARLY;BYWEEKNO=20;BYDAY=MO",
            expected = listOf(date(1997, 5, 12), date(1998, 5, 11), date(1999, 5, 17)),
            take = 3,
        )
    }

    @Test
    fun intersectingSelectorsAndSetPositionExamples() {
        assertDates(
            start = "1997-09-13T09:00:00",
            rule = "FREQ=MONTHLY;BYDAY=SA;BYMONTHDAY=7,8,9,10,11,12,13",
            expected = listOf(
                date(1997, 9, 13), date(1997, 10, 11), date(1997, 11, 8),
                date(1997, 12, 13), date(1998, 1, 10), date(1998, 2, 7), date(1998, 3, 7),
            ),
            take = 7,
        )
        assertDates(
            start = "1997-09-04T09:00:00",
            rule = "FREQ=MONTHLY;COUNT=3;BYDAY=TU,WE,TH;BYSETPOS=3",
            expected = listOf(date(1997, 9, 4), date(1997, 10, 7), date(1997, 11, 6)),
        )
        assertDates(
            start = "1997-09-29T09:00:00",
            rule = "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-2",
            expected = listOf(
                date(1997, 9, 29), date(1997, 10, 30), date(1997, 11, 27),
                date(1997, 12, 30), date(1998, 1, 29), date(1998, 2, 26), date(1998, 3, 30),
            ),
            take = 7,
        )
    }

    @Test
    fun fridayTheThirteenthAndElectionDayExamples() {
        val start = zoned("1997-09-02T09:00:00")
        val fridayThirteenth = RecurrenceSet(
            start = start,
            rules = listOf(RecurrenceRuleParser.parse("FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13")),
            excludedDates = setOf(start),
        ).occurrences().take(5).map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()
        assertEquals(
            listOf(
                date(1998, 2, 13), date(1998, 3, 13), date(1998, 11, 13),
                date(1999, 8, 13), date(2000, 10, 13),
            ),
            fridayThirteenth,
        )

        assertDates(
            start = "1996-11-05T09:00:00",
            rule = "FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8",
            expected = listOf(date(1996, 11, 5), date(2000, 11, 7), date(2004, 11, 2)),
            take = 3,
        )
    }

    @Test
    fun subDailyAndInvalidDateExamples() {
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=MINUTELY;INTERVAL=15;COUNT=6",
            expected = listOf(
                time(1997, 9, 2, 9, 0), time(1997, 9, 2, 9, 15),
                time(1997, 9, 2, 9, 30), time(1997, 9, 2, 9, 45),
                time(1997, 9, 2, 10, 0), time(1997, 9, 2, 10, 15),
            ),
        )
        val everyTwentyMinutes = (9..16).flatMap { hour ->
            listOf(0, 20, 40).map { minute -> time(1997, 9, 2, hour, minute) }
        }
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=DAILY;BYHOUR=9,10,11,12,13,14,15,16;BYMINUTE=0,20,40",
            expected = everyTwentyMinutes,
            take = 24,
        )
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=MINUTELY;INTERVAL=20;BYHOUR=9,10,11,12,13,14,15,16",
            expected = everyTwentyMinutes,
            take = 24,
        )
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=MINUTELY;INTERVAL=90;COUNT=4",
            expected = listOf(
                time(1997, 9, 2, 9, 0), time(1997, 9, 2, 10, 30),
                time(1997, 9, 2, 12, 0), time(1997, 9, 2, 13, 30),
            ),
        )

        // RFC 5545 verified erratum 3883 corrects UNTIL from 17:00Z to 21:00Z.
        assertDates(
            start = "1997-09-02T09:00:00",
            rule = "FREQ=HOURLY;INTERVAL=3;UNTIL=19970902T210000Z",
            expected = listOf(
                time(1997, 9, 2, 9, 0), time(1997, 9, 2, 12, 0), time(1997, 9, 2, 15, 0),
            ),
        )
        assertDates(
            start = "2007-01-15T09:00:00",
            rule = "FREQ=MONTHLY;BYMONTHDAY=15,30;COUNT=5",
            expected = listOf(
                date(2007, 1, 15), date(2007, 1, 30), date(2007, 2, 15),
                date(2007, 3, 15), date(2007, 3, 30),
            ),
        )
    }

    private fun assertDates(
        start: String,
        rule: String,
        expected: List<LocalDateTime>,
        take: Int = expected.size,
    ) {
        val actual = recurrence(start, rule).occurrences().take(take)
            .map { (it as RecurrenceDateTime.Zoned).dateTime }.toList()
        assertEquals(expected, actual, rule)
    }

    private fun recurrence(start: String, rule: String): RuleRecurrence = RuleRecurrence(
        zoned(start),
        RecurrenceRuleParser.parse(rule),
    )

    private fun zoned(value: String): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime.parse(value), "America/New_York")

    private fun date(year: Int, month: Int, day: Int): LocalDateTime = time(year, month, day, 9, 0)

    private fun time(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): LocalDateTime = LocalDateTime(year, month, day, hour, minute)
}

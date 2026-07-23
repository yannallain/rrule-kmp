package io.github.yallain.rrule.model

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceErrorReason
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceValidationException
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrenceRuleValidationTest {
    @Test
    fun acceptsBoundaryValues() {
        RecurrenceRule(
            frequency = Frequency.YEARLY,
            bySecond = setOf(0, 60),
            byMinute = setOf(0, 59),
            byHour = setOf(0, 23),
            byMonth = setOf(1, 12),
            byMonthDay = setOf(-31, -1, 1, 31),
            byYearDay = setOf(-366, -1, 1, 366),
            byWeekNumber = setOf(-53, -1, 1, 53),
            bySetPosition = setOf(-366, -1, 1, 366),
        )
    }

    @Test
    fun byDayAcceptsPublicOrdinalBoundaries() {
        assertEquals(53, ByDay(Weekday.MONDAY, 53).ordinal)
        assertEquals(-53, ByDay(Weekday.MONDAY, -53).ordinal)
    }

    @Test
    fun byDayRejectsPublicOrdinalBoundaryNeighbors() {
        listOf(0, 54, -54).forEach { ordinal ->
            val error = assertFailsWith<RecurrenceValidationException> {
                ByDay(Weekday.MONDAY, ordinal)
            }

            assertEquals("BYDAY", error.propertyName)
            assertEquals(ordinal.toString(), error.invalidToken)
            assertEquals(RecurrenceErrorReason.OUT_OF_RANGE, error.reason)
        }
    }

    @Test
    fun rejectsInvalidScalarAndListValues() {
        assertInvalid("INTERVAL") { RecurrenceRule(Frequency.DAILY, interval = 0) }
        assertInvalid("COUNT") { RecurrenceRule(Frequency.DAILY, count = 0) }
        assertInvalid("BYSECOND") { RecurrenceRule(Frequency.DAILY, bySecond = setOf(61)) }
        assertInvalid("BYMINUTE") { RecurrenceRule(Frequency.DAILY, byMinute = setOf(-1)) }
        assertInvalid("BYHOUR") { RecurrenceRule(Frequency.DAILY, byHour = setOf(24)) }
        assertInvalid("BYMONTH") { RecurrenceRule(Frequency.YEARLY, byMonth = setOf(0)) }
        assertInvalid("BYMONTHDAY") { RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(0)) }
        assertInvalid("BYYEARDAY") { RecurrenceRule(Frequency.YEARLY, byYearDay = setOf(0)) }
        assertInvalid("BYWEEKNO") { RecurrenceRule(Frequency.YEARLY, byWeekNumber = setOf(0)) }
        assertInvalid("BYSETPOS") {
            RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(1), bySetPosition = setOf(0))
        }
    }

    @Test
    fun rejectsBothNeighborsOutsideEveryNumericBoundary() {
        val cases = listOf<Pair<String, () -> Unit>>(
            "BYSECOND" to { RecurrenceRule(Frequency.DAILY, bySecond = setOf(-1)) },
            "BYSECOND" to { RecurrenceRule(Frequency.DAILY, bySecond = setOf(61)) },
            "BYMINUTE" to { RecurrenceRule(Frequency.DAILY, byMinute = setOf(-1)) },
            "BYMINUTE" to { RecurrenceRule(Frequency.DAILY, byMinute = setOf(60)) },
            "BYHOUR" to { RecurrenceRule(Frequency.DAILY, byHour = setOf(-1)) },
            "BYHOUR" to { RecurrenceRule(Frequency.DAILY, byHour = setOf(24)) },
            "BYMONTH" to { RecurrenceRule(Frequency.YEARLY, byMonth = setOf(0)) },
            "BYMONTH" to { RecurrenceRule(Frequency.YEARLY, byMonth = setOf(13)) },
            "BYMONTHDAY" to { RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(-32)) },
            "BYMONTHDAY" to { RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(32)) },
            "BYYEARDAY" to { RecurrenceRule(Frequency.YEARLY, byYearDay = setOf(-367)) },
            "BYYEARDAY" to { RecurrenceRule(Frequency.YEARLY, byYearDay = setOf(367)) },
            "BYWEEKNO" to { RecurrenceRule(Frequency.YEARLY, byWeekNumber = setOf(-54)) },
            "BYWEEKNO" to { RecurrenceRule(Frequency.YEARLY, byWeekNumber = setOf(54)) },
            "BYSETPOS" to {
                RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(1), bySetPosition = setOf(-367))
            },
            "BYSETPOS" to {
                RecurrenceRule(Frequency.MONTHLY, byMonthDay = setOf(1), bySetPosition = setOf(367))
            },
        )

        cases.forEach { (property, construct) -> assertInvalid(property, construct) }
    }

    @Test
    fun rejectsMutuallyExclusiveCountAndUntil() {
        val error = assertFailsWith<RecurrenceValidationException> {
            RecurrenceRule(
                frequency = Frequency.DAILY,
                count = 2,
                until = RecurrenceDateTime.Utc(Instant.parse("2025-01-02T00:00:00Z")),
            )
        }

        assertEquals(RecurrenceErrorReason.MUTUALLY_EXCLUSIVE, error.reason)

        assertInvalid("UNTIL") {
            RecurrenceRule(
                frequency = Frequency.DAILY,
                until = RecurrenceDateTime.Floating(LocalDateTime(2025, 1, 1, 0, 0, 0, 1)),
            )
        }
    }

    @Test
    fun rejectsUntilOutsideTheRfcFourDigitYearDomain() {
        assertInvalid("UNTIL") {
            RecurrenceRule(
                Frequency.DAILY,
                until = RecurrenceDateTime.DateOnly(LocalDate(-1, 12, 31)),
            )
        }
        assertInvalid("UNTIL") {
            RecurrenceRule(
                Frequency.DAILY,
                until = RecurrenceDateTime.Floating(LocalDateTime(10_000, 1, 1, 0, 0)),
            )
        }
    }

    @Test
    fun enforcesFrequencySpecificRestrictions() {
        assertInvalid("BYWEEKNO") {
            RecurrenceRule(Frequency.MONTHLY, byWeekNumber = setOf(1))
        }
        assertInvalid("BYDAY") {
            RecurrenceRule(Frequency.WEEKLY, byDay = listOf(ByDay(Weekday.MONDAY, 1)))
        }
        assertInvalid("BYDAY") {
            RecurrenceRule(
                Frequency.YEARLY,
                byWeekNumber = setOf(1),
                byDay = listOf(ByDay(Weekday.MONDAY, 1)),
            )
        }
        assertInvalid("BYSETPOS") {
            RecurrenceRule(Frequency.MONTHLY, bySetPosition = setOf(-1))
        }
        assertInvalid("BYMONTHDAY") {
            RecurrenceRule(Frequency.WEEKLY, byMonthDay = setOf(1))
        }
        assertInvalid("BYYEARDAY") {
            RecurrenceRule(Frequency.DAILY, byYearDay = setOf(1))
        }
    }

    @Test
    fun copiesMutableCollectionsAndCanonicalizesByDay() {
        val months = mutableSetOf(3)
        val rule = RecurrenceRule(
            frequency = Frequency.YEARLY,
            byMonth = months,
            byDay = listOf(
                ByDay(Weekday.FRIDAY),
                ByDay(Weekday.MONDAY),
                ByDay(Weekday.FRIDAY),
            ),
        )

        months += 4

        assertEquals(setOf(3), rule.byMonth)
        assertEquals(
            listOf(ByDay(Weekday.MONDAY), ByDay(Weekday.FRIDAY)),
            rule.byDay,
        )
        assertTrue(rule == rule.copy())
    }

    private fun assertInvalid(propertyName: String, block: () -> Unit) {
        val error = assertFailsWith<RecurrenceValidationException>(block = block)
        assertEquals(propertyName, error.propertyName)
    }
}

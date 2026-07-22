package io.github.yallain.rrule.set

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RecurrenceTimeZoneResolver
import io.github.yallain.rrule.RecurrenceValidationException
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RecurrenceSetTest {
    @Test
    fun includesDtStartEvenWhenItDoesNotMatchAnRrule() {
        val start = local(2024, 1, 1, 9) // Monday
        val set = RecurrenceSet(
            start = start,
            rules = listOf(
                RecurrenceRule(
                    Frequency.WEEKLY,
                    count = 2,
                    byDay = listOf(ByDay(Weekday.WEDNESDAY)),
                ),
            ),
        )

        assertEquals(
            listOf(start, local(2024, 1, 3, 9), local(2024, 1, 10, 9)),
            set.occurrences().toList(),
        )
    }

    @Test
    fun lazilyMergesRulesAndRdatesInOrderWithoutDuplicates() {
        val set = RecurrenceSet(
            start = local(2024, 1, 1, 9),
            rules = listOf(
                RecurrenceRule(Frequency.DAILY, interval = 2, count = 3),
                RecurrenceRule(Frequency.DAILY, interval = 3, count = 3),
            ),
            additionalDates = setOf(
                local(2024, 1, 2, 9),
                local(2024, 1, 3, 9),
            ),
        )

        assertEquals(
            listOf(
                local(2024, 1, 1, 9),
                local(2024, 1, 2, 9),
                local(2024, 1, 3, 9),
                local(2024, 1, 4, 9),
                local(2024, 1, 5, 9),
                local(2024, 1, 7, 9),
            ),
            set.occurrences().toList(),
        )
    }

    @Test
    fun exclusionsTakePrecedenceOverEveryInclusionSource() {
        val set = RecurrenceSet(
            start = local(2024, 1, 1, 9),
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 7)),
            exclusionRules = listOf(
                RecurrenceRule(
                    Frequency.WEEKLY,
                    count = 1,
                    byDay = listOf(ByDay(Weekday.WEDNESDAY)),
                ),
            ),
            additionalDates = setOf(local(2024, 1, 3, 9), local(2024, 1, 10, 9)),
            excludedDates = setOf(local(2024, 1, 1, 9), local(2024, 1, 5, 9)),
        )

        assertEquals(
            listOf(
                local(2024, 1, 2, 9),
                local(2024, 1, 4, 9),
                local(2024, 1, 6, 9),
                local(2024, 1, 7, 9),
                local(2024, 1, 10, 9),
            ),
            set.occurrences().toList(),
        )
    }

    @Test
    fun boundedQueriesStopTheMergedSources() {
        val set = RecurrenceSet(
            start = local(2000, 1, 1, 9),
            rules = listOf(
                RecurrenceRule(Frequency.DAILY),
                RecurrenceRule(Frequency.WEEKLY, byDay = listOf(ByDay(Weekday.MONDAY))),
            ),
        )

        assertEquals(
            listOf(local(2400, 2, 28, 9), local(2400, 2, 29, 9)),
            set.between(local(2400, 2, 28, 9), local(2400, 3, 2, 9), limit = 2),
        )
        assertEquals(local(2400, 2, 29, 9), set.after(local(2400, 2, 28, 9)))
        assertEquals(local(2000, 1, 1, 9), set.before(local(2000, 1, 2, 9)))
        assertNull(set.before(local(2000, 1, 1, 9)))
    }

    @Test
    fun beforeJumpsToFarBoundsAndWalksPastExclusions() {
        val set = RecurrenceSet(
            start = local(2000, 1, 1, 0, 0),
            rules = listOf(
                RecurrenceRule(Frequency.SECONDLY, bySecond = setOf(0)),
            ),
            exclusionRules = listOf(
                RecurrenceRule(
                    frequency = Frequency.DAILY,
                    byHour = setOf(0),
                    byMinute = setOf(0),
                    bySecond = setOf(0),
                ),
            ),
            excludedDates = setOf(local(2399, 12, 31, 23, 59)),
        )

        assertEquals(
            local(2399, 12, 31, 23, 58),
            set.before(local(2400, 1, 1, 0, 0, 30)),
        )

        val canceledRule = RecurrenceRule(Frequency.SECONDLY, bySecond = setOf(0))
        val fullyExcluded = RecurrenceSet(
            start = local(2000, 1, 1, 0),
            rules = listOf(canceledRule),
            exclusionRules = listOf(canceledRule),
        )
        assertEquals(emptyList(), fullyExcluded.occurrences().toList())
        assertNull(fullyExcluded.before(local(2400, 1, 1, 0)))
    }

    @Test
    fun explicitRdateInDstGapUsesTheOffsetBeforeTheGap() {
        val start = RecurrenceDateTime.Zoned(
            LocalDateTime(2024, 3, 30, 2, 30),
            "Europe/Paris",
        )
        val gap = RecurrenceDateTime.Zoned(
            LocalDateTime(2024, 3, 31, 2, 30),
            "Europe/Paris",
        )
        val set = RecurrenceSet(start = start, additionalDates = setOf(gap))

        assertEquals(listOf(start, gap), set.occurrences().toList())
    }

    @Test
    fun explicitGapDatesFailLoudlyWhenAResolverCannotApplyRfcGapSemantics() {
        val gapDateTime = LocalDateTime(2024, 3, 31, 2, 30)
        val resolver = RecurrenceTimeZoneResolver { dateTime, _ ->
            if (dateTime == gapDateTime) {
                LocalTimeResolution.Nonexistent
            } else {
                LocalTimeResolution.Valid(dateTime.toInstant(UtcOffset.ZERO))
            }
        }
        val start = RecurrenceDateTime.Zoned(LocalDateTime(2024, 3, 30, 2, 30), "Custom/Gap")
        val gap = RecurrenceDateTime.Zoned(gapDateTime, "Custom/Gap")

        val rdateError = assertFailsWith<RecurrenceValidationException> {
            RecurrenceSet(start = start, additionalDates = setOf(gap), timeZoneResolver = resolver)
        }
        assertEquals("RDATE", rdateError.propertyName)

        val exdateError = assertFailsWith<RecurrenceValidationException> {
            RecurrenceSet(start = start, excludedDates = setOf(gap), timeZoneResolver = resolver)
        }
        assertEquals("EXDATE", exdateError.propertyName)
    }

    @Test
    fun takingOneOccurrenceDoesNotReadTheFollowingRuleCandidate() {
        val resolver = RecurrenceTimeZoneResolver { dateTime, _ ->
            check(dateTime < LocalDateTime(2024, 1, 3, 9, 0)) {
                "The source was advanced beyond the value requested by the consumer"
            }
            LocalTimeResolution.Valid(dateTime.toInstant(UtcOffset.ZERO))
        }
        val start = customZoned(2024, 1, 1)
        val set = RecurrenceSet(
            start = start,
            rules = listOf(RecurrenceRule(Frequency.DAILY)),
            excludedDates = setOf(start),
            timeZoneResolver = resolver,
        )

        assertEquals(listOf(customZoned(2024, 1, 2)), set.occurrences().take(1).toList())
    }

    @Test
    fun oneReturnedSequenceKeepsIndependentExclusionStateForEveryIterator() {
        val set = RecurrenceSet(
            start = local(2024, 1, 1, 9),
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 4)),
            exclusionRules = listOf(RecurrenceRule(Frequency.DAILY, count = 1)),
        )
        val occurrences = set.occurrences()
        val firstIterator = occurrences.iterator()
        val secondIterator = occurrences.iterator()

        assertEquals(local(2024, 1, 2, 9), firstIterator.next())
        assertEquals(local(2024, 1, 2, 9), secondIterator.next())
        assertEquals(
            listOf(
                local(2024, 1, 2, 9),
                local(2024, 1, 3, 9),
                local(2024, 1, 4, 9),
            ),
            occurrences.toList(),
        )
    }

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute, second),
    )

    private fun customZoned(year: Int, month: Int, day: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, 9, 0), "Custom/UTC")
}

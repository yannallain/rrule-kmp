package io.github.yallain.rrule.set

import io.github.yallain.rrule.ByDay
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.LinearLocalTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.Weekday
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrenceSetSemanticEquivalenceTest {
    @Test
    fun beforeFarBoundCancelsAUniversalSecondSelectorWithoutWalkingExcludedCandidates() {
        val resolver = ResolutionBudgetResolver(maximumResolutionCalls = 40)
        val set = RecurrenceSet(
            start = zoned(2000, 1, 1),
            rules = listOf(RecurrenceRule(Frequency.SECONDLY)),
            exclusionRules = listOf(
                RecurrenceRule(
                    frequency = Frequency.SECONDLY,
                    bySecond = (0..59).toSet(),
                ),
            ),
            timeZoneResolver = resolver,
        )

        assertNull(set.before(zoned(2400, 1, 1)))
        assertTrue(
            resolver.resolutionCalls <= 40,
            "Equivalent rules walked excluded SECONDLY candidates from a far bound",
        )
    }

    @Test
    fun completeLimitingSelectorsHaveTheSameFiniteStreamsAsOmittedSelectors() {
        val cases = listOf(
            rule(Frequency.SECONDLY) to rule(Frequency.SECONDLY, bySecond = (0..60).toSet()),
            rule(Frequency.MINUTELY) to rule(Frequency.MINUTELY, byMinute = (0..59).toSet()),
            rule(Frequency.HOURLY) to rule(Frequency.HOURLY, byHour = (0..23).toSet()),
            rule(Frequency.DAILY) to rule(
                Frequency.DAILY,
                byDay = Weekday.entries.map(::ByDay),
            ),
            rule(Frequency.MONTHLY) to rule(Frequency.MONTHLY, byMonth = (1..12).toSet()),
            rule(Frequency.SECONDLY) to rule(
                Frequency.SECONDLY,
                byMonthDay = (1..28).toSet() + setOf(-3, -2, -1),
                byYearDay = (1..365).toSet() + -1,
            ),
        )

        cases.forEach { (plain, explicitlyUniversal) ->
            val plainOccurrences = finiteRuleOccurrences(plain)
            val universalOccurrences = finiteRuleOccurrences(explicitlyUniversal)

            assertEquals(plainOccurrences, universalOccurrences, explicitlyUniversal.toString())
            assertEquals(
                emptyList(),
                RecurrenceSet(
                    start = floating(2024, 1, 1),
                    rules = listOf(plain),
                    exclusionRules = listOf(explicitlyUniversal),
                ).occurrences().toList(),
                explicitlyUniversal.toString(),
            )
        }
    }

    @Test
    fun selectorsThatExpandACoarserFrequencyAreNotTreatedAsUniversal() {
        val minutely = RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(rule(Frequency.MINUTELY, count = 4, bySecond = (0..59).toSet())),
            exclusionRules = listOf(rule(Frequency.MINUTELY, count = 4)),
        )
        assertEquals(
            listOf(
                floating(2024, 1, 1, second = 1),
                floating(2024, 1, 1, second = 2),
                floating(2024, 1, 1, second = 3),
            ),
            minutely.occurrences().toList(),
        )

        val daily = RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(rule(Frequency.DAILY, count = 4, byHour = (0..23).toSet())),
            exclusionRules = listOf(rule(Frequency.DAILY, count = 4)),
        )
        assertEquals(
            listOf(
                floating(2024, 1, 1, hour = 1),
                floating(2024, 1, 1, hour = 2),
                floating(2024, 1, 1, hour = 3),
            ),
            daily.occurrences().toList(),
        )

        val monthly = RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(
                rule(
                    Frequency.MONTHLY,
                    count = 4,
                    byMonthDay = (1..28).toSet() + setOf(-3, -2, -1),
                ),
            ),
            exclusionRules = listOf(rule(Frequency.MONTHLY, count = 4)),
        )
        assertEquals(
            listOf(
                floating(2024, 1, 2),
                floating(2024, 1, 3),
                floating(2024, 1, 4),
            ),
            monthly.occurrences().toList(),
        )

        val yearly = RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(
                rule(
                    Frequency.YEARLY,
                    count = 4,
                    byYearDay = (1..365).toSet() + -1,
                ),
            ),
            exclusionRules = listOf(rule(Frequency.YEARLY, count = 4)),
        )
        assertEquals(
            listOf(
                floating(2024, 1, 2),
                floating(2024, 1, 3),
                floating(2024, 1, 4),
            ),
            yearly.occurrences().toList(),
        )
    }

    private fun finiteRuleOccurrences(rule: RecurrenceRule): List<RecurrenceDateTime> =
        RecurrenceSet(
            start = floating(2024, 1, 1),
            rules = listOf(rule),
        ).occurrences().toList()

    private fun rule(
        frequency: Frequency,
        count: Int = 8,
        bySecond: Set<Int> = emptySet(),
        byMinute: Set<Int> = emptySet(),
        byHour: Set<Int> = emptySet(),
        byDay: List<ByDay> = emptyList(),
        byMonthDay: Set<Int> = emptySet(),
        byYearDay: Set<Int> = emptySet(),
        byMonth: Set<Int> = emptySet(),
    ): RecurrenceRule = RecurrenceRule(
        frequency = frequency,
        count = count,
        bySecond = bySecond,
        byMinute = byMinute,
        byHour = byHour,
        byDay = byDay,
        byMonthDay = byMonthDay,
        byYearDay = byYearDay,
        byMonth = byMonth,
    )

    private fun floating(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
    ): RecurrenceDateTime.Floating = RecurrenceDateTime.Floating(
        LocalDateTime(year, month, day, hour, minute, second),
    )

    private fun zoned(year: Int, month: Int, day: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, 0, 0), "Custom/UTC")

    private class ResolutionBudgetResolver(
        private val maximumResolutionCalls: Int,
    ) : LinearLocalTimeZoneResolver {
        var resolutionCalls: Int = 0
            private set

        override fun resolve(localDateTime: LocalDateTime, timeZoneId: String): LocalTimeResolution {
            resolutionCalls++
            check(resolutionCalls <= maximumResolutionCalls) {
                "Recurrence-set reverse query exceeded its local-resolution budget"
            }
            return LocalTimeResolution.Valid(localDateTime.toInstant(UtcOffset.ZERO))
        }

        override fun localDateTimeAt(instant: Instant, timeZoneId: String): LocalDateTime =
            instant.toLocalDateTime(TimeZone.UTC)

        override fun hasLinearLocalTimeline(timeZoneId: String): Boolean = true
    }
}

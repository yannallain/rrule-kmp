package io.github.yallain.rrule.timezone

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.Frequency
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.LocalTimeResolution
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceRule
import io.github.yallain.rrule.RecurrenceSet
import io.github.yallain.rrule.RuleRecurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class TimeZoneTransitionMatrixTest {
    @Test
    fun resolvesThirtyMinuteGapAndOverlapInLordHowe() {
        assertIs<LocalTimeResolution.Nonexistent>(
            KotlinxRecurrenceTimeZoneResolver.resolve(
                LocalDateTime(2024, 10, 6, 2, 15),
                LORD_HOWE,
            ),
        )
        assertEquals(
            LocalTimeResolution.Ambiguous(
                earlier = Instant.parse("2024-04-06T14:45:00Z"),
                later = Instant.parse("2024-04-06T15:15:00Z"),
            ),
            KotlinxRecurrenceTimeZoneResolver.resolve(
                LocalDateTime(2024, 4, 7, 1, 45),
                LORD_HOWE,
            ),
        )
    }

    @Test
    fun skipsNonexistentOccurrencesWithoutCountingThemInNonHourGaps() {
        val recurrence = RuleRecurrence(
            start = zoned(2024, 10, 5, 2, 15, LORD_HOWE),
            rule = RecurrenceRule(Frequency.DAILY, count = 3),
        )

        assertEquals(
            listOf(
                zoned(2024, 10, 5, 2, 15, LORD_HOWE),
                zoned(2024, 10, 7, 2, 15, LORD_HOWE),
                zoned(2024, 10, 8, 2, 15, LORD_HOWE),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun skipsAnEntireDeletedCivilDateInApia() {
        val recurrence = RuleRecurrence(
            start = zoned(2011, 12, 29, 9, 0, APIA),
            rule = RecurrenceRule(Frequency.DAILY, count = 3),
        )

        assertEquals(
            listOf(
                zoned(2011, 12, 29, 9, 0, APIA),
                zoned(2011, 12, 31, 9, 0, APIA),
                zoned(2012, 1, 1, 9, 0, APIA),
            ),
            recurrence.occurrences().toList(),
        )
    }

    @Test
    fun ambiguityPolicyIsStableAcrossForwardAndReverseQueries() {
        val ambiguous = zoned(2024, 4, 7, 1, 45, LORD_HOWE)
        val rule = RecurrenceRule(Frequency.DAILY, count = 3)

        for (policy in AmbiguousTimePolicy.entries) {
            val recurrence = RuleRecurrence(
                start = zoned(2024, 4, 6, 1, 45, LORD_HOWE),
                rule = rule,
                ambiguousTimePolicy = policy,
            )
            assertEquals(ambiguous, recurrence.after(ambiguous, inclusive = true), policy.name)
            assertEquals(ambiguous, recurrence.before(ambiguous, inclusive = true), policy.name)
        }
    }

    @Test
    fun zonedSetExclusionsWinAtAnAmbiguousLocalTime() {
        val ambiguous = zoned(2024, 4, 7, 1, 45, LORD_HOWE)
        val set = RecurrenceSet(
            start = zoned(2024, 4, 6, 1, 45, LORD_HOWE),
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 3)),
            additionalDates = setOf(ambiguous),
            excludedDates = setOf(ambiguous),
        )

        assertEquals(
            listOf(
                zoned(2024, 4, 6, 1, 45, LORD_HOWE),
                zoned(2024, 4, 8, 1, 45, LORD_HOWE),
            ),
            set.occurrences().toList(),
        )
    }

    @Test
    fun utcExdateMatchesOnlyTheSelectedInstantOfAnAmbiguousZonedOccurrence() {
        val definition = RecurrenceContentParser.parse(
            "DTSTART;TZID=Europe/Paris:20241026T023000\n" +
                "RRULE:FREQ=DAILY;COUNT=2\n" +
                "EXDATE:20241027T003000Z",
        )
        val start = zoned(2024, 10, 26, 2, 30, "Europe/Paris")
        val ambiguous = zoned(2024, 10, 27, 2, 30, "Europe/Paris")

        assertEquals(
            listOf(start),
            definition.recurrenceSet(ambiguousTimePolicy = AmbiguousTimePolicy.EARLIER)
                .occurrences().toList(),
        )
        assertEquals(
            listOf(start, ambiguous),
            definition.recurrenceSet(ambiguousTimePolicy = AmbiguousTimePolicy.LATER)
                .occurrences().toList(),
        )
    }

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: String,
    ): RecurrenceDateTime.Zoned = RecurrenceDateTime.Zoned(
        LocalDateTime(year, month, day, hour, minute),
        zone,
    )

    private companion object {
        const val LORD_HOWE = "Australia/Lord_Howe"
        const val APIA = "Pacific/Apia"
    }
}

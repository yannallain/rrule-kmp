package io.github.yallain.rrule.integration.shrinker

import io.github.yallain.rrule.AmbiguousTimePolicy
import io.github.yallain.rrule.KotlinxRecurrenceTimeZoneResolver
import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.toInstantOrNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal object ShrinkerSmokeVerifier {
    fun verify() {
        val schedule = RecurrenceContentParser.parse(
            """
            DTSTART;TZID=Europe/Paris:20240330T210000
            RRULE:FREQ=DAILY;COUNT=3
            RDATE;TZID=Europe/Paris:20240402T210000
            EXDATE;TZID=Europe/Paris:20240331T210000
            """.trimIndent(),
        ).recurrenceSet()

        val actual = schedule.between(
            startInclusive = RecurrenceDateTime.Utc(Instant.parse("2024-03-30T00:00:00Z")),
            endExclusive = RecurrenceDateTime.Utc(Instant.parse("2024-04-03T00:00:00Z")),
            limit = 10,
        ).map { occurrence ->
            checkNotNull(
                occurrence.toInstantOrNull(
                    timeZoneResolver = KotlinxRecurrenceTimeZoneResolver,
                    ambiguousTimePolicy = AmbiguousTimePolicy.EARLIER,
                ),
            )
        }

        check(
            actual == listOf(
                Instant.parse("2024-03-30T20:00:00Z"),
                Instant.parse("2024-04-01T19:00:00Z"),
                Instant.parse("2024-04-02T19:00:00Z"),
            ),
        ) { "Unexpected minified recurrence result: $actual" }
    }
}

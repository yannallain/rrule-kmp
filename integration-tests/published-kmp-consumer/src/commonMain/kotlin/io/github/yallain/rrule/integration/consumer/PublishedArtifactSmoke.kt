package io.github.yallain.rrule.integration.consumer

import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceRuleParser
import io.github.yallain.rrule.recurrence
import kotlin.time.Instant

internal fun publishedArtifactOccurrences(): List<RecurrenceDateTime> =
    RecurrenceRuleParser
        .parse("FREQ=DAILY;COUNT=2")
        .recurrence(
            RecurrenceDateTime.Utc(
                Instant.parse("2026-07-22T09:00:00Z"),
            ),
        )
        .occurrences()
        .toList()

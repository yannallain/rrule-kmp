package io.github.yallain.rrule.integration.consumer

import io.github.yallain.rrule.RecurrenceDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PublishedArtifactSmokeTest {
    @Test
    fun executesThePublishedJvmVariant() {
        assertEquals(
            listOf(
                RecurrenceDateTime.Utc(Instant.parse("2026-07-22T09:00:00Z")),
                RecurrenceDateTime.Utc(Instant.parse("2026-07-23T09:00:00Z")),
            ),
            publishedArtifactOccurrences(),
        )
    }
}

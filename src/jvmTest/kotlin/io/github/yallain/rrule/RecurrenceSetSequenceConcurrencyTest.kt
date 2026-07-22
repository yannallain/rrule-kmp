package io.github.yallain.rrule

import java.util.concurrent.Executors
import kotlinx.datetime.LocalDateTime
import org.junit.Test
import kotlin.test.assertEquals

/** JVM stress coverage for the cold-sequence concurrency guarantee. */
class RecurrenceSetSequenceConcurrencyTest {
    @Test
    fun oneReturnedSequenceCanCreateConcurrentIndependentIterators() {
        val start = floating(2024, 1, 1)
        val set = RecurrenceSet(
            start = start,
            rules = listOf(RecurrenceRule(Frequency.DAILY, count = 4)),
            exclusionRules = listOf(RecurrenceRule(Frequency.DAILY, count = 1)),
        )
        val occurrences = set.occurrences()
        val expected = listOf(
            floating(2024, 1, 2),
            floating(2024, 1, 3),
            floating(2024, 1, 4),
        )
        val executor = Executors.newFixedThreadPool(8)

        try {
            val results = (1..64).map {
                executor.submit<List<RecurrenceDateTime>> { occurrences.toList() }
            }.map { it.get() }

            results.forEach { assertEquals(expected, it) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun floating(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}

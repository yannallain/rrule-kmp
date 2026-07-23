package io.github.yallain.rrule.differential

import io.github.yallain.rrule.RecurrenceDateTime
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DifferentialHarnessTest {
    @Test
    fun reportsReferenceMatchesAndDifferencesWithoutAssigningCorrectness() {
        val case = DifferentialCase(
            name = "negative month day",
            start = local(1997, 9, 2),
            ruleValue = "FREQ=MONTHLY;COUNT=3;BYMONTHDAY=-1",
            occurrenceCount = 3,
        )
        val reference = listOf(local(1997, 9, 30), local(1997, 10, 31), local(1997, 11, 30))

        assertTrue(DifferentialHarness.compare(case, "matching oracle", reference).matches)

        val divergent = DifferentialHarness.compare(case, "divergent oracle", reference.dropLast(1))
        assertFalse(divergent.matches)
        assertEquals(1, divergent.differences.size)
        assertEquals(2, divergent.differences.single().index)
    }

    private fun local(year: Int, month: Int, day: Int): RecurrenceDateTime.Floating =
        RecurrenceDateTime.Floating(LocalDateTime(year, month, day, 9, 0))
}

package io.github.yallain.rrule

import android.os.Build
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidRuntimeSmokeTest {
    @Test
    fun evaluatesCalendarAndTimezoneCodeOnTheAndroidRuntime() {
        assertTrue(Build.VERSION.SDK_INT >= 21)

        val recurrence = RuleRecurrence(
            start = RecurrenceDateTime.Zoned(
                LocalDateTime(2024, 3, 30, 2, 30),
                "Europe/Paris",
            ),
            rule = RecurrenceRule(Frequency.DAILY, count = 3),
        )

        assertEquals(
            listOf(
                zoned(2024, 3, 30),
                zoned(2024, 4, 1),
                zoned(2024, 4, 2),
            ),
            recurrence.occurrences().toList(),
        )
    }

    private fun zoned(year: Int, month: Int, day: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(
            LocalDateTime(year, month, day, 2, 30),
            "Europe/Paris",
        )
}

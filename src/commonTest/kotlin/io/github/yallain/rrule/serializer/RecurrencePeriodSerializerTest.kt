package io.github.yallain.rrule.serializer

import io.github.yallain.rrule.RecurrenceContentParser
import io.github.yallain.rrule.RecurrenceDateTime
import io.github.yallain.rrule.RecurrenceDuration
import io.github.yallain.rrule.RecurrencePeriod
import io.github.yallain.rrule.RecurrencePeriodSerializer
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RecurrencePeriodSerializerTest {
    @Test
    fun serializesCanonicalExplicitAndDurationForms() {
        assertEquals(
            "19970101T180000Z/19970102T070000Z",
            RecurrencePeriodSerializer.serialize(
                RecurrencePeriod.Explicit(
                    utc("1997-01-01T18:00:00Z"),
                    utc("1997-01-02T07:00:00Z"),
                ),
            ),
        )
        assertEquals(
            "19970101T180000Z/PT5H30M",
            RecurrencePeriodSerializer.serialize(
                RecurrencePeriod.WithDuration(
                    utc("1997-01-01T18:00:00Z"),
                    RecurrenceDuration(hours = 5, minutes = 30),
                ),
            ),
        )
        assertEquals(
            "19970101T180000Z/P15DT5H0M20S",
            RecurrencePeriodSerializer.serialize(
                RecurrencePeriod.WithDuration(
                    utc("1997-01-01T18:00:00Z"),
                    RecurrenceDuration(days = 15, hours = 5, seconds = 20),
                ),
            ),
        )
        assertEquals(
            "19970101T180000Z/P7W",
            RecurrencePeriodSerializer.serialize(
                RecurrencePeriod.WithDuration(
                    utc("1997-01-01T18:00:00Z"),
                    RecurrenceDuration(weeks = 7),
                ),
            ),
        )
    }

    @Test
    fun valueSerializationRoundTripsWhenTheContentLineRestoresTzid() {
        val period = RecurrencePeriod.Explicit(
            zoned(2024, 3, 30, 21),
            zoned(2024, 3, 31, 6),
        )
        val value = RecurrencePeriodSerializer.serialize(period)

        val parsed = RecurrenceContentParser.parse(
            "DTSTART;TZID=Europe/Paris:20240329T210000\n" +
                "RDATE;VALUE=PERIOD;TZID=Europe/Paris:$value",
        )

        assertEquals("20240330T210000/20240331T060000", value)
        assertEquals(setOf(period), parsed.additionalPeriods)
    }

    private fun zoned(year: Int, month: Int, day: Int, hour: Int): RecurrenceDateTime.Zoned =
        RecurrenceDateTime.Zoned(LocalDateTime(year, month, day, hour, 0), "Europe/Paris")

    private fun utc(value: String): RecurrenceDateTime.Utc = RecurrenceDateTime.Utc(Instant.parse(value))
}

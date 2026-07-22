package io.github.yallain.rrule

/** Canonical serializer for the value portion of an RFC 5545 `RRULE` property. */
public object RecurrenceRuleSerializer {
    /** Serializes [rule] with uppercase names and a stable rule-part/list order. */
    public fun serialize(rule: RecurrenceRule): String = buildList {
        add("FREQ=${rule.frequency.name}")
        rule.until?.let { add("UNTIL=${serializeUntil(it)}") }
        rule.count?.let { add("COUNT=$it") }
        if (rule.interval != 1) add("INTERVAL=${rule.interval}")
        addIntSet("BYSECOND", rule.bySecond)
        addIntSet("BYMINUTE", rule.byMinute)
        addIntSet("BYHOUR", rule.byHour)
        if (rule.byDay.isNotEmpty()) add("BYDAY=${rule.byDay.joinToString(",") { it.toRfcToken() }}")
        addIntSet("BYMONTHDAY", rule.byMonthDay)
        addIntSet("BYYEARDAY", rule.byYearDay)
        addIntSet("BYWEEKNO", rule.byWeekNumber)
        addIntSet("BYMONTH", rule.byMonth)
        addIntSet("BYSETPOS", rule.bySetPosition)
        if (rule.weekStart != Weekday.MONDAY) add("WKST=${rule.weekStart.rfcCode}")
    }.joinToString(";")

    private fun MutableList<String>.addIntSet(name: String, values: Set<Int>) {
        if (values.isNotEmpty()) add("$name=${values.sorted().joinToString(",")}")
    }

    private fun serializeUntil(value: RecurrenceDateTime): String =
        RfcTemporalValueSerializer.serialize(value, propertyName = "UNTIL", allowZoned = false)
}

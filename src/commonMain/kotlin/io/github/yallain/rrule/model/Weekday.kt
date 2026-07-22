package io.github.yallain.rrule

/** A weekday in RFC order, beginning with Monday. */
public enum class Weekday(public val rfcCode: String) {
    MONDAY("MO"),
    TUESDAY("TU"),
    WEDNESDAY("WE"),
    THURSDAY("TH"),
    FRIDAY("FR"),
    SATURDAY("SA"),
    SUNDAY("SU");

    public companion object {
        /** Returns the weekday represented by [code], ignoring ASCII case. */
        public fun fromRfcCode(code: String): Weekday? =
            entries.firstOrNull { it.rfcCode == code.uppercase() }
    }
}

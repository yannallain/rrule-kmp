package io.github.yallain.rrule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;

/** Verifies the collection boundary exactly as a Java library consumer observes it. */
public final class PublicModelImmutabilityJavaTest {
    @Test
    public void javaMutationApisCannotChangePublicModelState() {
        Fixture fixture = fixture();
        int ruleHashCode = fixture.firstRule.hashCode();
        int definitionHashCode = fixture.definition.hashCode();

        assertListIsReadOnly(fixture.firstRule.getByDay());
        assertSetIsReadOnly(fixture.firstRule.getBySecond());
        assertSetIsReadOnly(fixture.firstRule.getByMinute());
        assertSetIsReadOnly(fixture.firstRule.getByHour());
        assertSetIsReadOnly(fixture.firstRule.getByMonthDay());
        assertSetIsReadOnly(fixture.firstRule.getByYearDay());
        assertSetIsReadOnly(fixture.firstRule.getByWeekNumber());
        assertSetIsReadOnly(fixture.firstRule.getByMonth());
        assertSetIsReadOnly(fixture.firstRule.getBySetPosition());

        assertListIsReadOnly(fixture.definition.getRules());
        assertListIsReadOnly(fixture.definition.getExclusionRules());
        assertSetIsReadOnly(fixture.definition.getAdditionalDates());
        assertSetIsReadOnly(fixture.definition.getAdditionalPeriods());
        assertSetIsReadOnly(fixture.definition.getExcludedDates());

        assertListIsReadOnly(fixture.recurrenceSet.getRules());
        assertListIsReadOnly(fixture.recurrenceSet.getExclusionRules());
        assertSetIsReadOnly(fixture.recurrenceSet.getAdditionalDates());
        assertSetIsReadOnly(fixture.recurrenceSet.getAdditionalPeriods());
        assertSetIsReadOnly(fixture.recurrenceSet.getExcludedDates());

        assertEquals(ruleHashCode, fixture.firstRule.hashCode());
        assertEquals(definitionHashCode, fixture.definition.hashCode());
        assertEquals(fixture.expectedOccurrences, occurrences(fixture.recurrenceSet));
    }

    @Test
    public void oneRecurrenceSetCanBeReadConcurrentlyWhileMutationIsRejected() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Callable<Void> verification = () -> {
                for (int iteration = 0; iteration < 100; iteration++) {
                    assertThrows(UnsupportedOperationException.class, fixture.definition.getRules()::clear);
                    assertThrows(UnsupportedOperationException.class, fixture.recurrenceSet.getAdditionalDates()::clear);
                    assertThrows(UnsupportedOperationException.class, fixture.recurrenceSet.getAdditionalPeriods()::clear);
                    assertEquals(fixture.expectedOccurrences, occurrences(fixture.recurrenceSet));
                    assertEquals(fixture.definitionHashCode, fixture.definition.hashCode());
                }
                return null;
            };
            List<Future<Void>> futures = executor.invokeAll(
                Arrays.asList(
                    verification,
                    verification,
                    verification,
                    verification,
                    verification,
                    verification,
                    verification,
                    verification
                )
            );
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Fixture fixture() {
        RecurrenceRule firstRule = RecurrenceRuleParser.INSTANCE.parse(
            "FREQ=YEARLY;COUNT=4;BYSECOND=1,2;BYMINUTE=3,4;BYHOUR=5,6;"
                + "BYDAY=MO,TU;BYMONTHDAY=7,8;BYYEARDAY=9,10;BYWEEKNO=11,12;"
                + "BYMONTH=1,2;BYSETPOS=1,2"
        );
        RecurrenceRule dailyRule = RecurrenceRuleParser.INSTANCE.parse("FREQ=DAILY;COUNT=4");
        RecurrenceRule monthlyExclusion = RecurrenceRuleParser.INSTANCE.parse(
            "FREQ=MONTHLY;COUNT=2;BYMONTHDAY=20,21"
        );
        RecurrenceRule yearlyExclusion = RecurrenceRuleParser.INSTANCE.parse(
            "FREQ=YEARLY;COUNT=2;BYMONTH=7,8"
        );
        RecurrenceDefinition parsed = RecurrenceContentParser.INSTANCE.parse(
            "DTSTART:20240101T090000\n"
                + "RDATE:20240110T090000,20240111T090000\n"
                + "RDATE;VALUE=PERIOD:20240106T090000/PT1H,"
                + "20240107T090000/20240107T100000\n"
                + "EXDATE:20240102T090000,20240103T090000"
        );
        RecurrenceDefinition definition = new RecurrenceDefinition(
            parsed.getStart(),
            Arrays.asList(dailyRule, firstRule),
            Arrays.asList(monthlyExclusion, yearlyExclusion),
            parsed.getAdditionalDates(),
            parsed.getExcludedDates(),
            parsed.getAdditionalPeriods()
        );
        RecurrenceSet recurrenceSet = definition.recurrenceSet(
            KotlinxRecurrenceTimeZoneResolver.INSTANCE,
            AmbiguousTimePolicy.EARLIER
        );
        List<RecurrenceDateTime> expectedOccurrences = occurrences(recurrenceSet);
        return new Fixture(
            firstRule,
            definition,
            recurrenceSet,
            expectedOccurrences,
            definition.hashCode()
        );
    }

    private static List<RecurrenceDateTime> occurrences(RecurrenceSet recurrenceSet) {
        RecurrenceDefinition bounds = RecurrenceContentParser.INSTANCE.parse(
            "DTSTART:20240101T090000\nRDATE:20240112T090000"
        );
        return recurrenceSet.between(
            bounds.getStart(),
            bounds.getAdditionalDates().iterator().next(),
            null
        );
    }

    private static void assertListIsReadOnly(List<?> values) {
        assertTrue("Regression needs a multi-element list", values.size() >= 2);
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertThrows(UnsupportedOperationException.class, () -> values.remove(0));
        assertThrows(
            UnsupportedOperationException.class,
            () -> {
                java.util.ListIterator<?> iterator = values.listIterator();
                iterator.next();
                iterator.remove();
            }
        );
    }

    private static void assertSetIsReadOnly(Set<?> values) {
        assertTrue("Regression needs a multi-element set", values.size() >= 2);
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertThrows(UnsupportedOperationException.class, () -> values.remove(values.iterator().next()));
        assertThrows(
            UnsupportedOperationException.class,
            () -> {
                java.util.Iterator<?> iterator = values.iterator();
                iterator.next();
                iterator.remove();
            }
        );
    }

    private static final class Fixture {
        private final RecurrenceRule firstRule;
        private final RecurrenceDefinition definition;
        private final RecurrenceSet recurrenceSet;
        private final List<RecurrenceDateTime> expectedOccurrences;
        private final int definitionHashCode;

        private Fixture(
            RecurrenceRule firstRule,
            RecurrenceDefinition definition,
            RecurrenceSet recurrenceSet,
            List<RecurrenceDateTime> expectedOccurrences,
            int definitionHashCode
        ) {
            this.firstRule = firstRule;
            this.definition = definition;
            this.recurrenceSet = recurrenceSet;
            this.expectedOccurrences = expectedOccurrences;
            this.definitionHashCode = definitionHashCode;
        }
    }
}

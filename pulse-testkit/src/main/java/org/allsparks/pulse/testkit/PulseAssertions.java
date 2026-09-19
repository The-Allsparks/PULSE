package org.allsparks.pulse.testkit;

import org.allsparks.contracts.input.Sample;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.ReadPlan;
import org.allsparks.pulse.ReadPlanEntry;

/** Assertions for read counts, freshness, and compiled schedules. */
public final class PulseAssertions {
    private PulseAssertions() {}

    public static void assertReadCount(CountingIntSource source, int expected) {
        if (source.reads != expected) {
            throw new AssertionError("expected " + expected + " physical reads, was " + source.reads);
        }
    }

    public static void assertFresh(Sample<?> sample) {
        if (!sample.isFresh()) {
            throw new AssertionError("expected a fresh sample, was " + sample);
        }
    }

    public static void assertNotFresh(Sample<?> sample) {
        if (sample.isFresh()) {
            throw new AssertionError("expected a non-fresh sample, was " + sample);
        }
    }

    public static void assertDue(SamplingPolicy schedule, long cycleId, boolean due) {
        if (schedule.isDue(cycleId) != due) {
            throw new AssertionError(
                    "schedule " + schedule + " due=" + schedule.isDue(cycleId) + " at cycle " + cycleId);
        }
    }

    public static void assertPlanContains(Pulse pulse, String qualifiedName) {
        ReadPlan plan = pulse.readPlan();
        for (int i = 0; i < plan.size(); i++) {
            ReadPlanEntry entry = plan.entryAt(i);
            if (entry.key().qualifiedName().equals(qualifiedName)) {
                return;
            }
        }
        throw new AssertionError("read plan missing " + qualifiedName + ":\n" + plan.describe());
    }

    public static void assertPlansEqual(ReadPlan left, ReadPlan right) {
        if (!left.describe().equals(right.describe())) {
            throw new AssertionError("read plans differ:\n" + left.describe() + "\n---\n" + right.describe());
        }
    }
}

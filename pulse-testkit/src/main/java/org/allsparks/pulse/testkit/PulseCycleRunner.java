package org.allsparks.pulse.testkit;

import org.allsparks.pulse.Pulse;

/** Drives strictly increasing capture cycles against a fake clock. */
public final class PulseCycleRunner {
    private final Pulse pulse;
    private final FakeClock clock;
    private long cycleId;
    private final long stepNanos;

    public PulseCycleRunner(Pulse pulse, FakeClock clock) {
        this(pulse, clock, 10_000_000L);
    }

    public PulseCycleRunner(Pulse pulse, FakeClock clock, long stepNanos) {
        this.pulse = pulse;
        this.clock = clock;
        this.stepNanos = stepNanos;
    }

    public long capture() {
        clock.advanceNanos(stepNanos);
        cycleId++;
        pulse.capture(cycleId, clock.nowNanos());
        return cycleId;
    }

    public long cycleId() {
        return cycleId;
    }
}

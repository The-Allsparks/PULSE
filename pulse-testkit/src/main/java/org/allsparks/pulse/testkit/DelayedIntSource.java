package org.allsparks.pulse.testkit;

import java.util.function.IntSupplier;

/** Physical source that advances a fake clock, simulating a slow Hub read. */
public final class DelayedIntSource implements IntSupplier {
    private final FakeClock clock;
    private final long delayNanos;
    public int reads;
    public int value;

    public DelayedIntSource(FakeClock clock, long delayNanos, int value) {
        this.clock = clock;
        this.delayNanos = delayNanos;
        this.value = value;
    }

    @Override
    public int getAsInt() {
        reads++;
        clock.advanceNanos(delayNanos);
        return value;
    }
}

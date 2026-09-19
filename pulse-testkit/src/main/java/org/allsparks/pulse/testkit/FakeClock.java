package org.allsparks.pulse.testkit;

import org.allsparks.contracts.time.FakeMonotonicClock;
import org.allsparks.contracts.time.MonotonicClock;

/**
 * Deterministic clock for PULSE tests. Wraps contracts
 * {@link FakeMonotonicClock}; time only moves when {@link #advanceNanos(long)}
 * is called.
 */
public final class FakeClock implements MonotonicClock {
    private final FakeMonotonicClock inner;

    public FakeClock() {
        this(0L);
    }

    public FakeClock(long initialNanos) {
        this.inner = new FakeMonotonicClock(initialNanos);
    }

    @Override
    public long nowNanos() {
        return inner.nowNanos();
    }

    public void advanceNanos(long deltaNanos) {
        inner.advanceNanos(deltaNanos);
    }
}

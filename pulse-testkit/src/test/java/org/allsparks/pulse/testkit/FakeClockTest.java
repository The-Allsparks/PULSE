package org.allsparks.pulse.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FakeClockTest {
    @Test
    void clockOnlyMovesWhenAdvanced() {
        FakeClock clock = new FakeClock(10L);
        assertEquals(10L, clock.nowNanos());
        clock.advanceNanos(5L);
        assertEquals(15L, clock.nowNanos());
    }
}

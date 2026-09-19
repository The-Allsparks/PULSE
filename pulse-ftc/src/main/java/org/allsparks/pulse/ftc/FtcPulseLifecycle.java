package org.allsparks.pulse.ftc;

import org.allsparks.contracts.time.MonotonicClock;
import org.allsparks.contracts.time.SystemMonotonicClock;
import org.allsparks.pulse.Pulse;

/**
 * Maps FTC init / loop / stop onto PULSE freeze, capture, and stop.
 *
 * <p>Register and bind signals before {@link #onInit()}. The loop thread must
 * call {@link #onLoop()} once per control cycle. Do not access Hub hardware
 * from a background thread.
 */
public final class FtcPulseLifecycle {
    private final Pulse pulse;
    private final MonotonicClock clock;
    private long cycleId;

    public FtcPulseLifecycle(Pulse pulse) {
        this(pulse, SystemMonotonicClock.INSTANCE);
    }

    public FtcPulseLifecycle(Pulse pulse, MonotonicClock clock) {
        this.pulse = pulse;
        this.clock = clock;
    }

    public void onInit() {
        pulse.freeze();
        pulse.start();
    }

    public void onLoop() {
        cycleId++;
        pulse.capture(cycleId, clock.nowNanos());
    }

    public void onStop() {
        pulse.stop();
    }

    public long cycleId() {
        return cycleId;
    }

    public Pulse pulse() {
        return pulse;
    }
}

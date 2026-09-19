package org.allsparks.pulse;

/**
 * Explicit PULSE session lifecycle.
 *
 * <p>Registration is legal only in {@link #CONFIGURING}. {@link Pulse#capture(long, long)}
 * is legal only in {@link #RUNNING}. Cached readers may be invoked after
 * {@link #STOPPED}; they never call hardware.
 */
public enum PulseLifecycle {
    CONFIGURING,
    FROZEN,
    RUNNING,
    STOPPED
}

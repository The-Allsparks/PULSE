package org.allsparks.pulse;

/**
 * Low-allocation capture timing hooks. Implementations must not format
 * telemetry, write files, or perform network I/O. TRACE should copy
 * primitives from {@link PulseMetrics} after {@link Pulse#capture(long, long)}.
 */
public interface ReadTimingListener {

    ReadTimingListener NOOP = new ReadTimingListener() {};

    default void onCaptureStarted(long cycleId, long nowNanos) {}

    default void onReaderFinished(int slotIndex, long durationNanos, boolean failed) {}

    default void onGroupFinished(int groupIndex, long durationNanos) {}

    default void onCaptureFinished(long cycleId, long durationNanos) {}
}

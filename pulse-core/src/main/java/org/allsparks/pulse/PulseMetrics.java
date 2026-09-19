package org.allsparks.pulse;

import java.util.Arrays;

/**
 * Bounded capture metrics. Fields are overwritten in place each cycle.
 * TRACE may copy primitives after {@link Pulse#capture(long, long)}. This
 * object does not format telemetry, write files, or allocate on the capture
 * path.
 */
public final class PulseMetrics {

    private long captureDurationNanos;
    private long maxCaptureDurationNanos;
    private long rollingCaptureDurationNanos;
    private long cycleId = -1L;
    private int phase;
    private int scheduledReads;
    private int executedReads;
    private int deferredReads;
    private int failedReads;
    private int staleSamples;
    private int oneShotScheduled;
    private int oneShotExecuted;
    private int oneShotDeferred;
    private String lastOneShotKey = "";
    private long[] readerDurationsNanos = new long[0];
    private long[] groupDurationsNanos = new long[0];
    private String[] groupIds = new String[0];
    private String readPlanDescription = "";
    private int hubCount;

    public PulseMetrics() {}

    public void prepare(int readerCount, String[] groupIds, String readPlanDescription, int hubCount) {
        this.readerDurationsNanos = new long[readerCount];
        this.groupIds = groupIds == null ? new String[0] : groupIds.clone();
        this.groupDurationsNanos = new long[this.groupIds.length];
        this.readPlanDescription = readPlanDescription == null ? "" : readPlanDescription;
        this.hubCount = hubCount;
    }

    public void record(
            long cycleId,
            int phase,
            long captureDurationNanos,
            int scheduledReads,
            int executedReads,
            int deferredReads,
            int failedReads,
            int staleSamples) {
        record(
                cycleId,
                phase,
                captureDurationNanos,
                scheduledReads,
                executedReads,
                deferredReads,
                failedReads,
                staleSamples,
                0,
                0,
                0,
                "");
    }

    public void record(
            long cycleId,
            int phase,
            long captureDurationNanos,
            int scheduledReads,
            int executedReads,
            int deferredReads,
            int failedReads,
            int staleSamples,
            int oneShotScheduled,
            int oneShotExecuted,
            int oneShotDeferred,
            String lastOneShotKey) {
        this.cycleId = cycleId;
        this.phase = phase;
        this.captureDurationNanos = captureDurationNanos;
        this.scheduledReads = scheduledReads;
        this.executedReads = executedReads;
        this.deferredReads = deferredReads;
        this.failedReads = failedReads;
        this.staleSamples = staleSamples;
        this.oneShotScheduled = oneShotScheduled;
        this.oneShotExecuted = oneShotExecuted;
        this.oneShotDeferred = oneShotDeferred;
        this.lastOneShotKey = lastOneShotKey == null ? "" : lastOneShotKey;
        if (captureDurationNanos > maxCaptureDurationNanos) {
            maxCaptureDurationNanos = captureDurationNanos;
        }
        if (rollingCaptureDurationNanos == 0L) {
            rollingCaptureDurationNanos = captureDurationNanos;
        } else {
            rollingCaptureDurationNanos = (rollingCaptureDurationNanos * 7L + captureDurationNanos) / 8L;
        }
    }

    public void setReaderDuration(int index, long durationNanos) {
        if (index >= 0 && index < readerDurationsNanos.length) {
            readerDurationsNanos[index] = durationNanos;
        }
    }

    public void setGroupDuration(int index, long durationNanos) {
        if (index >= 0 && index < groupDurationsNanos.length) {
            groupDurationsNanos[index] = durationNanos;
        }
    }

    public String[] groupIds() {
        return Arrays.copyOf(groupIds, groupIds.length);
    }

    /**
     * Copy of per-group durations. Allocates. Call after capture, never from
     * inside {@link Pulse#capture(long, long)}.
     */
    public long[] groupDurationsNanos() {
        return Arrays.copyOf(groupDurationsNanos, groupDurationsNanos.length);
    }

    public long captureDurationNanos() {
        return captureDurationNanos;
    }

    public long maxCaptureDurationNanos() {
        return maxCaptureDurationNanos;
    }

    public long rollingCaptureDurationNanos() {
        return rollingCaptureDurationNanos;
    }

    public long cycleId() {
        return cycleId;
    }

    public int phase() {
        return phase;
    }

    public int scheduledReads() {
        return scheduledReads;
    }

    public int executedReads() {
        return executedReads;
    }

    public int deferredReads() {
        return deferredReads;
    }

    public int failedReads() {
        return failedReads;
    }

    public int staleSamples() {
        return staleSamples;
    }

    public int oneShotScheduled() {
        return oneShotScheduled;
    }

    public int oneShotExecuted() {
        return oneShotExecuted;
    }

    public int oneShotDeferred() {
        return oneShotDeferred;
    }

    /**
     * Qualified name of the last on-demand read that executed this capture,
     * or empty when none ran. TRACE plots the counts, not this string.
     */
    public String lastOneShotKey() {
        return lastOneShotKey;
    }

    public int hubCount() {
        return hubCount;
    }

    /**
     * Copy of per-reader durations. Allocates. Call after capture, never from
     * inside {@link Pulse#capture(long, long)}.
     */
    public long[] readerDurationsNanos() {
        return Arrays.copyOf(readerDurationsNanos, readerDurationsNanos.length);
    }

    public String readPlanDescription() {
        return readPlanDescription;
    }
}

package org.allsparks.pulse;

import java.util.Objects;
import org.allsparks.contracts.time.MonotonicClock;
import org.allsparks.contracts.time.SystemMonotonicClock;

/**
 * Immutable construction settings for {@link Pulse}.
 */
public final class PulseSettings {

    private final MonotonicClock clock;
    private final HubCacheControl hubCacheControl;
    private final FailurePolicy failurePolicy;
    private final long cycleBudgetNanos;
    private final int maxOnDemandOtherPerCapture;
    private final long onDemandOtherBudgetNanos;
    private final ReadTimingListener readTimingListener;

    private PulseSettings(Builder builder) {
        this.clock = builder.clock;
        this.hubCacheControl = builder.hubCacheControl;
        this.failurePolicy = builder.failurePolicy;
        this.cycleBudgetNanos = builder.cycleBudgetNanos;
        this.maxOnDemandOtherPerCapture = builder.maxOnDemandOtherPerCapture;
        this.onDemandOtherBudgetNanos = builder.onDemandOtherBudgetNanos;
        this.readTimingListener = builder.readTimingListener;
    }

    public static PulseSettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public MonotonicClock clock() {
        return clock;
    }

    public HubCacheControl hubCacheControl() {
        return hubCacheControl;
    }

    public FailurePolicy failurePolicy() {
        return failurePolicy;
    }

    /**
     * Nanoseconds allowed for optional readers after critical and normal work.
     * {@code 0} means no budget (run every due optional reader).
     */
    public long cycleBudgetNanos() {
        return cycleBudgetNanos;
    }

    /**
     * Maximum on-demand {@link ReadBus#OTHER} physical reads admitted per
     * capture. Extras stay queued for a later cycle. {@code 0} means no cap.
     */
    public int maxOnDemandOtherPerCapture() {
        return maxOnDemandOtherPerCapture;
    }

    /**
     * Skip admitted on-demand {@link ReadBus#OTHER} reads (except
     * {@link org.allsparks.contracts.input.InputPriority#CRITICAL}) when
     * capture has already used this much time. The mark stays queued.
     * {@code 0} means no deadline.
     */
    public long onDemandOtherBudgetNanos() {
        return onDemandOtherBudgetNanos;
    }

    public ReadTimingListener readTimingListener() {
        return readTimingListener;
    }

    public static final class Builder {
        private MonotonicClock clock = SystemMonotonicClock.INSTANCE;
        private HubCacheControl hubCacheControl = NoOpHubCacheControl.INSTANCE;
        private FailurePolicy failurePolicy = FailurePolicy.defaults();
        private long cycleBudgetNanos;
        private int maxOnDemandOtherPerCapture = 1;
        private long onDemandOtherBudgetNanos;
        private ReadTimingListener readTimingListener = ReadTimingListener.NOOP;

        public Builder clock(MonotonicClock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder hubCacheControl(HubCacheControl hubCacheControl) {
            this.hubCacheControl = Objects.requireNonNull(hubCacheControl, "hubCacheControl");
            return this;
        }

        public Builder failurePolicy(FailurePolicy failurePolicy) {
            this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
            return this;
        }

        public Builder cycleBudgetNanos(long cycleBudgetNanos) {
            if (cycleBudgetNanos < 0L) {
                throw new IllegalArgumentException("cycleBudgetNanos must be >= 0");
            }
            this.cycleBudgetNanos = cycleBudgetNanos;
            return this;
        }

        public Builder maxOnDemandOtherPerCapture(int maxOnDemandOtherPerCapture) {
            if (maxOnDemandOtherPerCapture < 0) {
                throw new IllegalArgumentException("maxOnDemandOtherPerCapture must be >= 0");
            }
            this.maxOnDemandOtherPerCapture = maxOnDemandOtherPerCapture;
            return this;
        }

        public Builder onDemandOtherBudgetNanos(long onDemandOtherBudgetNanos) {
            if (onDemandOtherBudgetNanos < 0L) {
                throw new IllegalArgumentException("onDemandOtherBudgetNanos must be >= 0");
            }
            this.onDemandOtherBudgetNanos = onDemandOtherBudgetNanos;
            return this;
        }

        public Builder readTimingListener(ReadTimingListener readTimingListener) {
            this.readTimingListener = readTimingListener == null ? ReadTimingListener.NOOP : readTimingListener;
            return this;
        }

        public PulseSettings build() {
            return new PulseSettings(this);
        }
    }
}

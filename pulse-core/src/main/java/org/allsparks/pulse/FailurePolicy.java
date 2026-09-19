package org.allsparks.pulse;

/**
 * How a failed or skipped read is retained.
 *
 * <p>Failed optional reads never abort {@link Pulse#capture(long, long)} unless
 * {@link #optionalFailuresFatal()} is true. Critical due reads are never
 * skipped for budget; they may still fail and keep last-known-good with
 * {@code INVALID} validity.
 */
public final class FailurePolicy {

    private final int maxConsecutiveFailures;
    private final int maxAgeCycles;
    private final boolean optionalFailuresFatal;
    private final boolean criticalFailuresFatal;

    private FailurePolicy(Builder builder) {
        this.maxConsecutiveFailures = builder.maxConsecutiveFailures;
        this.maxAgeCycles = builder.maxAgeCycles;
        this.optionalFailuresFatal = builder.optionalFailuresFatal;
        this.criticalFailuresFatal = builder.criticalFailuresFatal;
    }

    public static FailurePolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Consecutive physical failures before the reader is disabled. {@code 0}
     * means never disable.
     */
    public int maxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    /**
     * After this many cycles without a successful capture, a retained value is
     * labeled {@code STALE} even if it was previously {@code VALID}.
     */
    public int maxAgeCycles() {
        return maxAgeCycles;
    }

    public boolean optionalFailuresFatal() {
        return optionalFailuresFatal;
    }

    public boolean criticalFailuresFatal() {
        return criticalFailuresFatal;
    }

    public static final class Builder {
        private int maxConsecutiveFailures = 8;
        private int maxAgeCycles = 1;
        private boolean optionalFailuresFatal;
        private boolean criticalFailuresFatal;

        public Builder maxConsecutiveFailures(int maxConsecutiveFailures) {
            if (maxConsecutiveFailures < 0) {
                throw new IllegalArgumentException("maxConsecutiveFailures must be >= 0");
            }
            this.maxConsecutiveFailures = maxConsecutiveFailures;
            return this;
        }

        public Builder maxAgeCycles(int maxAgeCycles) {
            if (maxAgeCycles < 1) {
                throw new IllegalArgumentException("maxAgeCycles must be >= 1");
            }
            this.maxAgeCycles = maxAgeCycles;
            return this;
        }

        public Builder optionalFailuresFatal(boolean optionalFailuresFatal) {
            this.optionalFailuresFatal = optionalFailuresFatal;
            return this;
        }

        public Builder criticalFailuresFatal(boolean criticalFailuresFatal) {
            this.criticalFailuresFatal = criticalFailuresFatal;
            return this;
        }

        public FailurePolicy build() {
            return new FailurePolicy(this);
        }
    }
}

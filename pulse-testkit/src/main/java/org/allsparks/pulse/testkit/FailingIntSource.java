package org.allsparks.pulse.testkit;

import java.util.function.IntSupplier;

/** Physical source that throws on every read. */
public final class FailingIntSource implements IntSupplier {
    public int reads;
    private final RuntimeException failure;

    public FailingIntSource() {
        this(new RuntimeException("hardware fault"));
    }

    public FailingIntSource(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public int getAsInt() {
        reads++;
        throw failure;
    }
}

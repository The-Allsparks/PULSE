package org.allsparks.pulse.testkit;

import java.util.function.IntSupplier;

/** Physical int source that counts invocations. */
public final class CountingIntSource implements IntSupplier {
    public int reads;
    public int value;

    public CountingIntSource() {
        this(0);
    }

    public CountingIntSource(int value) {
        this.value = value;
    }

    @Override
    public int getAsInt() {
        reads++;
        return value;
    }
}

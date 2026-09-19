package org.allsparks.pulse.testkit;

import java.util.function.IntSupplier;

/** Fake physical input that counts reads. */
public final class FakeIntSource implements IntSupplier {
    private final CountingIntSource inner;

    public FakeIntSource() {
        this(0);
    }

    public FakeIntSource(int value) {
        this.inner = new CountingIntSource(value);
    }

    @Override
    public int getAsInt() {
        return inner.getAsInt();
    }

    public int reads() {
        return inner.reads;
    }

    public void setValue(int value) {
        inner.value = value;
    }
}

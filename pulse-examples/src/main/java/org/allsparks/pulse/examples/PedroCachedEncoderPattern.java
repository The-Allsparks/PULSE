package org.allsparks.pulse.examples;

import java.util.function.IntSupplier;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;

/**
 * How Pedro (or any pathing library) can consume cached encoder ticks without
 * a compile dependency on PULSE. The library stores {@link IntSupplier}.
 * Standalone robots pass {@code motor::getCurrentPosition}. Composed robots
 * pass the supplier returned by {@code pulse.registerInt}.
 */
public final class PedroCachedEncoderPattern {
    public static final SignalKey<Integer> LEFT_ENCODER = SignalKey.intKey("drive", "leftEncoder");

    private final IntSupplier leftEncoder;
    private final IntSupplier rightEncoder;

    public PedroCachedEncoderPattern(IntSupplier leftEncoder, IntSupplier rightEncoder) {
        this.leftEncoder = leftEncoder;
        this.rightEncoder = rightEncoder;
    }

    public int[] localizationInputs() {
        return new int[] {leftEncoder.getAsInt(), rightEncoder.getAsInt()};
    }

    public static SamplingPolicy encoderPolicy() {
        return SamplingPolicy.everyCycle();
    }
}

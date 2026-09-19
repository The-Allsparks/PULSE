package org.allsparks.pulse.examples;

import java.util.function.IntSupplier;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.InputRegistrar;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.pulse.Pulse;

/**
 * Two independently usable libraries request the same encoder. PULSE performs
 * exactly one physical read per cycle.
 *
 * <p>This example is platform-neutral. It does not depend on the FTC SDK.
 */
public final class TwoLibrariesOneSignalExample {
    public static final SignalKey<Integer> ELEVATOR_POSITION = SignalKey.intKey("elevator", "position");

    private TwoLibrariesOneSignalExample() {}

    /** Standalone library: accepts any {@link IntSupplier}. */
    public static final class MimicLike {
        private final IntSupplier position;

        public MimicLike(IntSupplier position) {
            this.position = position;
        }

        public void declareInputs(InputRegistrar registrar) {
            registrar.require(ELEVATOR_POSITION, SamplingPolicy.everyCycle(), InputPriority.NORMAL);
        }

        public int update() {
            return position.getAsInt();
        }
    }

    public static final class AmperLike {
        private final IntSupplier position;

        public AmperLike(IntSupplier position) {
            this.position = position;
        }

        public void declareInputs(InputRegistrar registrar) {
            registrar.require(ELEVATOR_POSITION, SamplingPolicy.everyCycle(), InputPriority.NORMAL);
        }

        public int update() {
            return position.getAsInt();
        }
    }

    public static int physicalReadsForTwoUpdates() {
        EncoderMotor motor = new EncoderMotor(42);
        Pulse pulse = new Pulse();
        MimicLike mimic = new MimicLike(pulse.registerInt(ELEVATOR_POSITION, motor, SamplingPolicy.everyCycle()));
        AmperLike amper = new AmperLike(pulse.cachedInt(ELEVATOR_POSITION));
        mimic.declareInputs(pulse);
        amper.declareInputs(pulse);
        pulse.start();
        pulse.capture(1L, 0L);
        if (mimic.update() != 42 || amper.update() != 42) {
            throw new IllegalStateException("cached values diverged");
        }
        return motor.reads;
    }

    /** Stand-in for a motor encoder getter. Not a robot device name. */
    public static final class EncoderMotor implements IntSupplier {
        public int reads;
        public int position;

        public EncoderMotor(int position) {
            this.position = position;
        }

        @Override
        public int getAsInt() {
            reads++;
            return position;
        }
    }

    public static void main(String[] args) {
        System.out.println("physical reads=" + physicalReadsForTwoUpdates());
    }
}

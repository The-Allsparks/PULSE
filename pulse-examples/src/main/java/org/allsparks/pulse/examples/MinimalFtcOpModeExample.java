package org.allsparks.pulse.examples;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import java.util.function.IntSupplier;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.ftc.FtcPulseLifecycle;
import org.allsparks.pulse.ftc.PulseFtc;

/**
 * Minimal FTC integration. Device names here are placeholders, not a team's
 * hardware map. Replace {@code encoder::getAsInt} with a real motor getter
 * such as {@code motor::getCurrentPosition}.
 */
public class MinimalFtcOpModeExample extends OpMode {
    public static final SignalKey<Integer> LEFT_ENCODER = SignalKey.intKey("drive", "leftEncoder");

    private Pulse pulse;
    private FtcPulseLifecycle lifecycle;
    private IntSupplier leftPosition;
    private final FakeEncoder encoder = new FakeEncoder();

    @Override
    public void init() {
        pulse = PulseFtc.create(hardwareMap);
        leftPosition = pulse.registerInt(LEFT_ENCODER, encoder::getAsInt, SamplingPolicy.everyCycle());
        lifecycle = new FtcPulseLifecycle(pulse);
        lifecycle.onInit();
    }

    @Override
    public void loop() {
        lifecycle.onLoop();
        int ticks = leftPosition.getAsInt();
        if (ticks == Integer.MIN_VALUE) {
            return;
        }
    }

    @Override
    public void stop() {
        if (lifecycle != null) {
            lifecycle.onStop();
        }
    }

    /** Placeholder encoder. TeamCode would use a hardware map motor instead. */
    static final class FakeEncoder {
        int getAsInt() {
            return 0;
        }
    }
}

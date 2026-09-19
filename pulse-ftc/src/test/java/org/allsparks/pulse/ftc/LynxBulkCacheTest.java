package org.allsparks.pulse.ftc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.function.IntSupplier;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.testkit.CountingIntSource;
import org.junit.jupiter.api.Test;

class LynxBulkCacheTest {

    private static final SignalKey<Integer> ENCODER = SignalKey.intKey("drive", "leftEncoder");

    @Test
    void eachHubIsClearedOncePerCapture() {
        HardwareMap hardwareMap = new HardwareMap();
        LynxModule control = new LynxModule("Control Hub", "hub-0");
        LynxModule expansion = new LynxModule("Expansion Hub", "hub-1");
        hardwareMap.put("control", control);
        hardwareMap.put("expansion", expansion);

        CountingIntSource encoder = new CountingIntSource(12);
        Pulse pulse = PulseFtc.create(hardwareMap);
        IntSupplier cached = pulse.registerInt(ENCODER, encoder, SamplingPolicy.everyCycle());
        FtcPulseLifecycle lifecycle = new FtcPulseLifecycle(pulse);
        lifecycle.onInit();
        assertEquals(LynxModule.BulkCachingMode.MANUAL, control.mode);
        assertEquals(LynxModule.BulkCachingMode.MANUAL, expansion.mode);
        assertEquals(1, control.configureCount);
        assertEquals(1, expansion.configureCount);

        lifecycle.onLoop();
        lifecycle.onLoop();
        assertEquals(2, control.clearCount);
        assertEquals(2, expansion.clearCount);
        assertEquals(12, cached.getAsInt());
        assertEquals(2, encoder.reads);

        lifecycle.onStop();
        assertEquals(2, encoder.reads);
        assertSame(pulse, lifecycle.pulse());
    }
}

package org.allsparks.pulse.ftc;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.MotorSignals;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.ReadBus;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

/**
 * Binds per-motor {@code getCurrent} as on-demand {@link ReadBus#OTHER} reads.
 *
 * <p>Current is not in the Lynx bulk packet. The slot stays dormant until
 * {@link Pulse#requestOnce} so Drive does not poll amps every loop. Android 7
 * missing methods become a failed sample, not an INIT crash.
 *
 * <p>{@link PulseFtc#create(HardwareMap)} calls {@link #bind(Pulse, HardwareMap)}.
 */
public final class MotorCurrentFill {
    private MotorCurrentFill() {}

    public static SignalKey<Double> currentAmps(String deviceName) {
        return MotorSignals.currentAmps(deviceName);
    }

    /**
     * Discover motors and bind on-demand current. Safe to call with an empty
     * map. Failures on one device skip that device so INIT still finishes.
     *
     * @return number of signals bound
     */
    public static int bind(Pulse pulse, HardwareMap hardwareMap) {
        if (pulse == null || hardwareMap == null) {
            return 0;
        }
        List<DcMotorEx> motors;
        try {
            motors = hardwareMap.getAll(DcMotorEx.class);
        } catch (RuntimeException ex) {
            return 0;
        }
        int bound = 0;
        for (int i = 0; i < motors.size(); i++) {
            DcMotorEx motor = motors.get(i);
            String name = deviceName(hardwareMap, motor);
            if (name == null) {
                continue;
            }
            SignalKey<Double> key = currentAmps(name);
            try {
                pulse.bindDouble(key, safeCurrent(motor), ReadBus.OTHER);
                pulse.require(key, SamplingPolicy.onDemand(), InputPriority.NORMAL);
                bound++;
            } catch (RuntimeException ignored) {
                // One bad motor must not prevent the rest of the on-demand bind.
            } catch (Error ignored) {
                // Android 7 missing method on this device: skip it.
            }
        }
        return bound;
    }

    private static DoubleSupplier safeCurrent(DcMotorEx motor) {
        return () -> {
            try {
                return motor.getCurrent(CurrentUnit.AMPS);
            } catch (Error error) {
                throw new RuntimeException(error);
            }
        };
    }

    private static String deviceName(HardwareMap hardwareMap, HardwareDevice device) {
        try {
            Set<String> names = hardwareMap.getNamesOf(device);
            if (names != null) {
                for (String name : names) {
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to unnamed skip.
        }
        return null;
    }
}

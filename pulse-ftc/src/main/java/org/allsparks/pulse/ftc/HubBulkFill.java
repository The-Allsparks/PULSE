package org.allsparks.pulse.ftc;

import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.MotorSignals;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.ReadBus;

/**
 * Binds every REV bulk-cached field that is already in {@code
 * cmdGetBulkInputData} once TeamCode (or the first encoder getter) has paid
 * for the packet.
 *
 * <p>Included: motor velocity, {@code isBusy}, {@code isOverCurrent}, analog
 * ports, digital channels. Excluded: encoder position (TeamCode binds those
 * keys), hub voltage (AMPER), per-motor {@code getCurrent} (see
 * {@link MotorCurrentFill}; not bulk), IMU / Pinpoint (I²C).
 *
 * <p>{@link PulseFtc#create(HardwareMap)} calls {@link #bind(Pulse, HardwareMap)}
 * so a later analog or extra motor in the RC config is sampled automatically.
 */
public final class HubBulkFill {
    public static final String NAMESPACE = "lynx-bulk";
    public static final String GROUP_ID = "lynx-bulk";

    private HubBulkFill() {}

    public static SignalKey<Double> velocity(String deviceName) {
        return SignalKey.doubleKey(NAMESPACE, property(deviceName, "velocity"));
    }

    public static SignalKey<Boolean> busy(String deviceName) {
        return SignalKey.booleanKey(NAMESPACE, property(deviceName, "busy"));
    }

    public static SignalKey<Boolean> overCurrent(String deviceName) {
        return MotorSignals.overCurrent(deviceName);
    }

    public static SignalKey<Double> analog(String deviceName) {
        return SignalKey.doubleKey(NAMESPACE, property(deviceName, "analog"));
    }

    public static SignalKey<Boolean> digital(String deviceName) {
        return SignalKey.booleanKey(NAMESPACE, property(deviceName, "digital"));
    }

    /**
     * Discover devices and bind free bulk fields. Safe to call with an empty
     * map. Failures on one device skip that device so INIT still finishes.
     *
     * @return number of signals bound
     */
    public static int bind(Pulse pulse, HardwareMap hardwareMap) {
        if (pulse == null || hardwareMap == null) {
            return 0;
        }
        List<SignalKey<?>> members = new ArrayList<>();
        bindMotors(pulse, hardwareMap, members);
        bindAnalogs(pulse, hardwareMap, members);
        bindDigitals(pulse, hardwareMap, members);
        if (members.size() >= 2) {
            pulse.requireGroup(
                    GROUP_ID, SamplingPolicy.everyCycle(), InputPriority.NORMAL, members.toArray(new SignalKey<?>[0]));
        }
        return members.size();
    }

    private static void bindMotors(Pulse pulse, HardwareMap hardwareMap, List<SignalKey<?>> members) {
        List<DcMotorEx> motors;
        try {
            motors = hardwareMap.getAll(DcMotorEx.class);
        } catch (RuntimeException ex) {
            return;
        }
        for (int i = 0; i < motors.size(); i++) {
            DcMotorEx motor = motors.get(i);
            String name = deviceName(hardwareMap, motor);
            if (name == null) {
                continue;
            }
            SignalKey<Double> velocityKey = velocity(name);
            SignalKey<Boolean> busyKey = busy(name);
            SignalKey<Boolean> overCurrentKey = overCurrent(name);
            try {
                pulse.bindDouble(velocityKey, motor::getVelocity, ReadBus.HUB_BULK);
                pulse.bindBoolean(busyKey, motor::isBusy, ReadBus.HUB_BULK);
                pulse.bindBoolean(overCurrentKey, motor::isOverCurrent, ReadBus.HUB_BULK);
                members.add(velocityKey);
                members.add(busyKey);
                members.add(overCurrentKey);
            } catch (RuntimeException ignored) {
                // One bad motor must not prevent the rest of the bulk fill.
            } catch (Error ignored) {
                // Android 7 missing method on this device: skip it.
            }
        }
    }

    private static void bindAnalogs(Pulse pulse, HardwareMap hardwareMap, List<SignalKey<?>> members) {
        List<AnalogInput> analogs;
        try {
            analogs = hardwareMap.getAll(AnalogInput.class);
        } catch (RuntimeException ex) {
            return;
        }
        for (int i = 0; i < analogs.size(); i++) {
            AnalogInput analogInput = analogs.get(i);
            String name = deviceName(hardwareMap, analogInput);
            if (name == null) {
                continue;
            }
            SignalKey<Double> key = analog(name);
            try {
                pulse.bindDouble(key, analogInput::getVoltage, ReadBus.HUB_BULK);
                members.add(key);
            } catch (RuntimeException ignored) {
                // Skip this analog port.
            } catch (Error ignored) {
                // Android 7 missing method on this device: skip it.
            }
        }
    }

    private static void bindDigitals(Pulse pulse, HardwareMap hardwareMap, List<SignalKey<?>> members) {
        List<DigitalChannel> channels;
        try {
            channels = hardwareMap.getAll(DigitalChannel.class);
        } catch (RuntimeException ex) {
            return;
        }
        for (int i = 0; i < channels.size(); i++) {
            DigitalChannel channel = channels.get(i);
            String name = deviceName(hardwareMap, channel);
            if (name == null) {
                continue;
            }
            SignalKey<Boolean> key = digital(name);
            try {
                pulse.bindBoolean(key, channel::getState, ReadBus.HUB_BULK);
                members.add(key);
            } catch (RuntimeException ignored) {
                // Skip this digital channel.
            } catch (Error ignored) {
                // Android 7 missing method on this device: skip it.
            }
        }
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

    private static String property(String deviceName, String field) {
        return deviceName + "/" + field;
    }
}

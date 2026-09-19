package org.allsparks.pulse.ftc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.allsparks.contracts.input.MotorSignals;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.ReadBus;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.junit.jupiter.api.Test;

class HubBulkFillTest {

    @Test
    void createBindsFreeBulkFieldsAndNotPosition() {
        HardwareMap hardwareMap = new HardwareMap();
        hardwareMap.put("control", new LynxModule("Control Hub", "hub-0"));
        FakeMotor motor = new FakeMotor();
        hardwareMap.put("front_left_drive", motor);
        hardwareMap.put("pot", new AnalogInput("pot", 1.25d));
        hardwareMap.put("limit", new FakeDigital(true));

        Pulse pulse = PulseFtc.create(hardwareMap);
        FtcPulseLifecycle lifecycle = new FtcPulseLifecycle(pulse);
        lifecycle.onInit();
        assertEquals(6, pulse.readPlan().size());
        lifecycle.onLoop();

        assertEquals(0, motor.positionReads);
        assertEquals(1, motor.velocityReads);
        assertEquals(1, motor.busyReads);
        assertEquals(1, motor.overCurrentReads);
        assertEquals(0, motor.currentReads);
        assertEquals(40.0d, pulse.getDouble(HubBulkFill.velocity("front_left_drive")), 0.0d);
        assertFalse(pulse.getBoolean(HubBulkFill.busy("front_left_drive")));
        assertFalse(pulse.getBoolean(HubBulkFill.overCurrent("front_left_drive")));
        assertEquals(MotorSignals.overCurrent("front_left_drive"), HubBulkFill.overCurrent("front_left_drive"));
        assertEquals(1.25d, pulse.getDouble(HubBulkFill.analog("pot")), 0.0d);
        assertTrue(pulse.getBoolean(HubBulkFill.digital("limit")));
        assertEquals(ReadBus.HUB_BULK, pulse.readPlan().entryAt(0).bus());

        pulse.requestOnce(MotorCurrentFill.currentAmps("front_left_drive"));
        lifecycle.onLoop();
        assertEquals(1, motor.currentReads);
        assertEquals(3.25d, pulse.getDouble(MotorCurrentFill.currentAmps("front_left_drive")), 0.0d);
    }

    @Test
    void emptyMapBindsNothing() {
        HardwareMap hardwareMap = new HardwareMap();
        Pulse pulse = PulseFtc.create(hardwareMap);
        pulse.freeze();
        assertEquals(0, pulse.readPlan().size());
    }

    private static final class FakeMotor implements DcMotorEx {
        int positionReads;
        int velocityReads;
        int busyReads;
        int overCurrentReads;
        int currentReads;

        @Override
        public int getCurrentPosition() {
            positionReads++;
            return 7;
        }

        @Override
        public boolean isBusy() {
            busyReads++;
            return false;
        }

        @Override
        public double getVelocity() {
            velocityReads++;
            return 40.0d;
        }

        @Override
        public boolean isOverCurrent() {
            overCurrentReads++;
            return false;
        }

        @Override
        public double getCurrent(CurrentUnit unit) {
            currentReads++;
            return 3.25d;
        }

        @Override
        public String getDeviceName() {
            return "front_left_drive";
        }

        @Override
        public String getConnectionInfo() {
            return "motor";
        }
    }

    private static final class FakeDigital implements DigitalChannel {
        private final boolean state;

        FakeDigital(boolean state) {
            this.state = state;
        }

        @Override
        public boolean getState() {
            return state;
        }

        @Override
        public String getDeviceName() {
            return "limit";
        }

        @Override
        public String getConnectionInfo() {
            return "digital";
        }
    }
}

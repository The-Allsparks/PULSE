package org.allsparks.pulse.ftc;

import com.qualcomm.robotcore.hardware.HardwareMap;
import org.allsparks.contracts.time.MonotonicClock;
import org.allsparks.contracts.time.SystemMonotonicClock;
import org.allsparks.pulse.Pulse;
import org.allsparks.pulse.PulseSettings;

/**
 * FTC composition helpers. No robot-specific device names live here.
 * {@link #create(HardwareMap)} binds {@link HubBulkFill} so velocity, busy,
 * over-current flags, analog, and digital ride in the same MANUAL packet, and
 * {@link MotorCurrentFill} so per-motor current is on-demand {@code OTHER}.
 * On-demand OTHER is capped at one physical read per capture; extras stay
 * queued. FTC create also skips that read when capture has already used 15 ms
 * so a late loop does not add {@code getCurrent} on top.
 */
public final class PulseFtc {
    private PulseFtc() {}

    public static Pulse create(HardwareMap hardwareMap) {
        return create(hardwareMap, SystemMonotonicClock.INSTANCE);
    }

    public static Pulse create(HardwareMap hardwareMap, MonotonicClock clock) {
        Pulse pulse = new Pulse(PulseSettings.builder()
                .clock(clock)
                .hubCacheControl(LynxHubDiscovery.cacheControl(hardwareMap))
                .onDemandOtherBudgetNanos(15_000_000L)
                .build());
        // Bind velocity/busy/overCurrent/analog/digital already in the Lynx bulk packet.
        HubBulkFill.bind(pulse, hardwareMap);
        // Bind getCurrent as on-demand OTHER. AMPER requestOnce after an over-current flag.
        MotorCurrentFill.bind(pulse, hardwareMap);
        return pulse;
    }

    public static PulseSettings.Builder settings(HardwareMap hardwareMap) {
        return PulseSettings.builder().hubCacheControl(LynxHubDiscovery.cacheControl(hardwareMap));
    }
}

package org.allsparks.pulse.ftc;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.ArrayList;
import java.util.List;
import org.allsparks.pulse.HubCacheControl;
import org.allsparks.pulse.ListHubCacheControl;

/**
 * Discovers every REV Hub in a hardware map and wraps them for PULSE.
 *
 * <p>PULSE configures {@code BulkCachingMode.MANUAL} once and clears each Hub
 * exactly once at the start of {@code capture()}. Libraries must not call
 * {@code clearBulkCache()} on PULSE-owned Hubs.
 */
public final class LynxHubDiscovery {
    private LynxHubDiscovery() {}

    public static List<LynxHubModule> discover(HardwareMap hardwareMap) {
        List<LynxModule> modules = hardwareMap.getAll(LynxModule.class);
        List<LynxHubModule> hubs = new ArrayList<>(modules.size());
        for (int i = 0; i < modules.size(); i++) {
            hubs.add(new LynxHubModule(modules.get(i)));
        }
        return hubs;
    }

    public static HubCacheControl cacheControl(HardwareMap hardwareMap) {
        List<LynxHubModule> hubs = discover(hardwareMap);
        return new ListHubCacheControl(new ArrayList<>(hubs));
    }
}

package org.allsparks.pulse.ftc;

import com.qualcomm.hardware.lynx.LynxModule;
import org.allsparks.pulse.HubModule;

/** Adapts one REV Hub to {@link HubModule}. */
public final class LynxHubModule implements HubModule {
    private final LynxModule module;

    public LynxHubModule(LynxModule module) {
        this.module = module;
    }

    @Override
    public String id() {
        String connection = module.getConnectionInfo();
        if (connection != null && !connection.trim().isEmpty()) {
            return connection;
        }
        return module.getDeviceName();
    }

    @Override
    public void configureManual() {
        module.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
    }

    @Override
    public void clear() {
        module.clearBulkCache();
    }

    public LynxModule lynxModule() {
        return module;
    }
}

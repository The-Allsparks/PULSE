package org.allsparks.pulse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link HubCacheControl} over an explicit Hub list. Used by the FTC adapter
 * and by tests.
 */
public final class ListHubCacheControl implements HubCacheControl {

    private final HubModule[] hubs;
    private boolean configured;

    public ListHubCacheControl(List<? extends HubModule> hubs) {
        Objects.requireNonNull(hubs, "hubs");
        this.hubs = hubs.toArray(new HubModule[0]);
    }

    public ListHubCacheControl(HubModule... hubs) {
        this.hubs = hubs == null ? new HubModule[0] : hubs.clone();
    }

    @Override
    public void configure() {
        for (int i = 0; i < hubs.length; i++) {
            hubs[i].configureManual();
        }
        configured = true;
    }

    @Override
    public void clearAll() {
        if (!configured) {
            configure();
        }
        for (int i = 0; i < hubs.length; i++) {
            hubs[i].clear();
        }
    }

    @Override
    public int hubCount() {
        return hubs.length;
    }

    public List<HubModule> hubs() {
        List<HubModule> copy = new ArrayList<>(hubs.length);
        for (int i = 0; i < hubs.length; i++) {
            copy.add(hubs[i]);
        }
        return copy;
    }
}

package org.allsparks.pulse.testkit;

import org.allsparks.pulse.HubModule;

/** Hub stand-in that counts configure and clear calls. */
public final class CountingHubModule implements HubModule {
    private final String id;
    public int configures;
    public int clears;
    public boolean manual;

    public CountingHubModule(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void configureManual() {
        configures++;
        manual = true;
    }

    @Override
    public void clear() {
        clears++;
    }
}

package org.allsparks.pulse;

/** No Hubs. Capture still runs; nothing is cleared. */
public final class NoOpHubCacheControl implements HubCacheControl {

    public static final NoOpHubCacheControl INSTANCE = new NoOpHubCacheControl();

    private NoOpHubCacheControl() {}

    @Override
    public void configure() {}

    @Override
    public void clearAll() {}

    @Override
    public int hubCount() {
        return 0;
    }
}

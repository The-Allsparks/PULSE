package org.allsparks.pulse;

/**
 * One Hub-sized cache that PULSE can configure and clear.
 *
 * <p>FTC adapters wrap a REV {@code LynxModule}. Tests wrap a counter.
 */
public interface HubModule {

    String id();

    void configureManual();

    void clear();
}

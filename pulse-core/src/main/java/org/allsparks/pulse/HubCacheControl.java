package org.allsparks.pulse;

/**
 * Platform-neutral REV/Hub bulk-cache control.
 *
 * <p>PULSE calls {@link #configure()} once at freeze and {@link #clearAll()}
 * exactly once at the start of each {@link Pulse#capture(long, long)}.
 * Individual libraries must not clear PULSE-owned caches.
 *
 * <p>Clearing a Hub cache is not the same as turning unrelated I²C operations
 * into one transaction. PULSE only deduplicates logical getter calls.
 */
public interface HubCacheControl {

    void configure();

    void clearAll();

    int hubCount();
}

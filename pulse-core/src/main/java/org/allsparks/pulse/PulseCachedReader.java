package org.allsparks.pulse;

/**
 * Marker for library-facing cached suppliers returned by {@link Pulse}.
 * Binding one of these as a physical source is a configuration error: it
 * would either recurse or hide the real hardware callback.
 */
public interface PulseCachedReader {}

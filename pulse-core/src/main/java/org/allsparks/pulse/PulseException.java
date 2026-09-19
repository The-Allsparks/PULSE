package org.allsparks.pulse;

/**
 * Configuration or lifecycle error. These fail during initialization or when
 * a caller uses PULSE out of sequence. They are not used for a failed
 * optional hardware read during {@link Pulse#capture(long, long)}.
 */
public final class PulseException extends IllegalStateException {
    public PulseException(String message) {
        super(message);
    }

    public PulseException(String message, Throwable cause) {
        super(message, cause);
    }
}

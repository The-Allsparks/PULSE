package org.allsparks.pulse;

/**
 * Physical bus class for capture order. PULSE does not issue Lynx bulk
 * commands; the SDK fills one MANUAL cache packet on the first hub-bulk
 * getter after {@code clearBulkCache()}.
 *
 * <p>Required hub-bulk getters run before I²C so encoder ticks and hub
 * voltage share that packet. Hub IMU yaw is I²C today. A future Pinpoint
 * localizer should also be {@link #I2C}, not {@link #HUB_BULK}.
 */
public enum ReadBus {
    /** REV bulk-cached Lynx properties: motor encoders, hub voltage, analog, digital. */
    HUB_BULK,
    /** Untagged or a Lynx call that is not part of the motor bulk packet. */
    OTHER,
    /** I²C devices (Hub IMU now; Pinpoint later). Not in the motor bulk packet. */
    I2C;

    public int rank() {
        switch (this) {
            case HUB_BULK:
                return 0;
            case OTHER:
                return 1;
            default:
                return 2;
        }
    }
}

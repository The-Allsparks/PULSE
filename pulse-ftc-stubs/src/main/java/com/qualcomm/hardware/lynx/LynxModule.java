package com.qualcomm.hardware.lynx;

import com.qualcomm.robotcore.hardware.HardwareDevice;

/**
 * Compile-only stand-in for REV Hub {@code LynxModule}. Robot builds use the
 * official FTC SDK class of the same name.
 */
public class LynxModule implements HardwareDevice {
    public enum BulkCachingMode {
        OFF,
        AUTO,
        MANUAL
    }

    private final String name;
    private final String connection;
    public BulkCachingMode mode = BulkCachingMode.OFF;
    public int clearCount;
    public int configureCount;

    public LynxModule(String name, String connection) {
        this.name = name;
        this.connection = connection;
    }

    public void setBulkCachingMode(BulkCachingMode mode) {
        this.mode = mode;
        configureCount++;
    }

    public void clearBulkCache() {
        clearCount++;
    }

    @Override
    public String getDeviceName() {
        return name;
    }

    @Override
    public String getConnectionInfo() {
        return connection;
    }
}

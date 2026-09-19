package com.qualcomm.robotcore.hardware;

/** Compile-only stub matching FTC analog ports (bulk-cached). */
public class AnalogInput implements HardwareDevice {
    private final String name;
    private final double voltage;

    public AnalogInput(String name, double voltage) {
        this.name = name;
        this.voltage = voltage;
    }

    public double getVoltage() {
        return voltage;
    }

    @Override
    public String getDeviceName() {
        return name;
    }

    @Override
    public String getConnectionInfo() {
        return name;
    }
}

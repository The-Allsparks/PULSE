package com.qualcomm.robotcore.eventloop.opmode;

import com.qualcomm.robotcore.hardware.HardwareMap;

/** Compile-only stub matching FTC {@code OpMode} lifecycle callbacks. */
public abstract class OpMode {
    public HardwareMap hardwareMap = new HardwareMap();

    public abstract void init();

    public void init_loop() {}

    public void start() {}

    public abstract void loop();

    public void stop() {}
}

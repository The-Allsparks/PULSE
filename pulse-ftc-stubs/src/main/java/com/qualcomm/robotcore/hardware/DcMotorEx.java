package com.qualcomm.robotcore.hardware;

import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

/**
 * Compile-only stub matching FTC {@code DcMotorEx}.
 * {@code getCurrent} is not bulk-cached; PULSE binds it on-demand only.
 */
public interface DcMotorEx extends DcMotor {
    double getVelocity();

    boolean isOverCurrent();

    double getCurrent(CurrentUnit unit);
}

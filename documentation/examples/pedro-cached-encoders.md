# Pedro cached encoder sources without depending on PULSE

Pedro core should not import `org.allsparks.pulse` or FTC SDK types. Wheel localizers accept encoder callbacks:

```java
public PedroLocalizer(IntSupplier left, IntSupplier right, IntSupplier strafe) {
    this.left = left;
    this.right = right;
    this.strafe = strafe;
}
```

Standalone:

```java
new PedroLocalizer(
        leftMotor::getCurrentPosition,
        rightMotor::getCurrentPosition,
        strafeMotor::getCurrentPosition);
```

With PULSE, TeamCode binds once and passes cached suppliers. Pedro still compiles against `IntSupplier` only.

BumbleBee's `DriveEncoderLocalizer(config, fl, fr, bl, br)` is the FTC entry point. Do not import `org.allsparks.pulse` from Pedro.

I2C pose localizers (Pinpoint, OTOS, OctoQuad) take a `MotionStateSource` instead of ticks. TeamCode captures the device once on `ReadBus.I2C` and passes the cached pose/velocity. Pedro must not call `pinpoint.update()`, `otos.getPosition()`, or `octoQuad.readLocalizerData()` in that loop.

```java
new PinpointLocalizer(
        () -> MotionState.ofVelocity(
                new Pose(pulse.pinpointX, pulse.pinpointY, pulse.pinpointHeading),
                new Velocity(pulse.pinpointVx, pulse.pinpointVy, pulse.pinpointOmega)));
```

For localization that must stay coherent, TeamCode (not Pedro) should register a PULSE group. BumbleBee splits **hub-bulk** (drive encoders + AMPER voltage, `ReadBus.HUB_BULK`) from Hub IMU yaw (`ReadBus.I2C`) so the MANUAL packet fills before I²C. Pinpoint, OTOS, and OctoQuad should also be I²C when they are added.

```java
pulse.requireGroup(
        "hub-bulk",
        SamplingPolicy.everyCycle(),
        InputPriority.CRITICAL,
        LEFT, RIGHT, STRAFE);
pulse.bindInt(LEFT, leftMotor::getCurrentPosition, ReadBus.HUB_BULK);
pulse.require(HEADING, SamplingPolicy.everyCycle(), InputPriority.CRITICAL);
pulse.bindDouble(HEADING, imu::yaw, ReadBus.I2C);
```

See `pulse-examples` `PedroCachedEncoderPattern`.

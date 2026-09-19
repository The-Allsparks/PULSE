# Pedro cached encoder sources without depending on PULSE

Pedro core should not import `org.allsparks.pulse` or FTC SDK types. It should accept encoder callbacks:

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

BumbleBee's `FollowerBuilder.driveEncoderLocalizer(constants, fl, fr, bl, br)` is the FTC entry point. Do not import `org.allsparks.pulse` from Pedro.

For localization that must stay coherent, TeamCode (not Pedro) should register a PULSE group. BumbleBee splits **hub-bulk** (drive encoders + AMPER voltage, `ReadBus.HUB_BULK`) from Hub IMU yaw (`ReadBus.I2C`) so the MANUAL packet fills before I²C. Pinpoint should also be I²C when it is added.

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

# Install

## Student / TeamCode (`includeBuild`)

Checkout PULSE next to `FtcRobotController` and `allsparks-contracts`:

```text
The Allsparks/
  allsparks-contracts/
  PULSE/
  FtcRobotController/
```

In `FtcRobotController/settings.gradle`:

```text
includeBuild('../PULSE')
includeBuild('../allsparks-contracts')
```

Depend on `org.allsparks:pulse-core` and `org.allsparks:pulse-ftc`. Do not depend on `pulse-ftc-stubs` or `pulse-testkit` on the robot.

Laptop Java is not live until the Hub APK is deployed.

## Hub floor test (TeamCode, not this repo)

`.\gradlew.bat check` is a desktop proof. It does not run on a Control Hub.

After TeamCode composes `pulse-ftc` (`PulseFtc` / `LynxHubDiscovery`) and deploys the RC APK:

1. Confirm INIT sets each discovered Hub to `BulkCachingMode.MANUAL` once.
2. Confirm PLAY `capture()` clears each Hub once per cycle.
3. Confirm two consumers of one `SignalKey` do not double the physical getter count.
4. Pull TRACE / loop-time logs if the loop stretches; do not add Sparkee hardware names to this repository.

PULSE itself has no robot OpMode. Floor validation lives in the private TeamCode composition root.

## Library developers

```text
./gradlew check
```

Windows: `.\gradlew.bat check`.

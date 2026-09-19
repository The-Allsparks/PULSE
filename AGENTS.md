# Agent and contributor engineering rules

PULSE is a sampling runtime, not a command scheduler, path planner, or vision stack.

## Commands

```powershell
.\gradlew.bat check
.\gradlew.bat spotlessApply
.\gradlew.bat javadocAll
.\gradlew.bat compileAgainstFtcSdk
```

`check` compiles all modules, runs tests, Spotless, javadoc, example compile, RobotCore 11.2.0 compile, and robot-artifact assertions.

## Boundaries

| May exist | Must not exist |
| --------- | -------------- |
| `pulse-core` pure Java sampling | FTC SDK or Android in `pulse-core` |
| `pulse-ftc` Hub cache + lifecycle | Sparkee/BumbleBee/TeamCode device names |
| Contracts `SignalKey` / `InputRegistrar` | Libraries depending on `org.allsparks.pulse` |
| Double-buffer publish | Background threads touching Hub hardware |
| Bounded `PulseMetrics` | JSON/telemetry/file I/O inside `capture()` |
| Optional cycle budget | Skipping due CRITICAL inputs |
| On-demand OTHER cap / budget | Skipping CRITICAL on-demand OTHER under that budget |

Hub Android 7: do not call `File.toPath()` or `java.nio.file.Files` on the robot capture path. Tests may use `nio` to scan sources.

Default git branch for this library is `main`. TeamCode remains on `bumblebee` and must not be added here.

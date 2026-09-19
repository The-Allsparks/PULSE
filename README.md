# PULSE — Prioritized Unified Loop Sampling Engine

PULSE gives FTC and other robotics runtimes **deterministic, deduplicated, scheduled input sampling**. Independently usable libraries such as MIMIC, AMPER, Pedro, HELM, TRACE, and ViDAR declare the inputs they need. PULSE compiles those declarations into a read plan, captures each required physical signal **at most once when due**, and returns cached values to every consumer for the rest of the cycle.

Libraries stay independently usable without PULSE. They depend on [`allsparks-contracts`](https://github.com/The-Allsparks/allsparks-contracts) (`SignalKey`, `SamplingPolicy`, `InputRegistrar`), not on this runtime. PULSE implements those contracts.

## Modules

| Module | Runs on robot | FTC SDK | Role |
| ------ | ------------- | ------- | ---- |
| `pulse-core` | yes | no | Registration, plan, cache, freshness, metrics |
| `pulse-ftc` | yes | yes (compile) | REV Hub MANUAL bulk cache, FTC lifecycle |
| `pulse-testkit` | no | no | Fake clock, counting/delayed/failing readers, assertions |
| `pulse-examples` | compile-only | stubs | Minimal examples, no team hardware names |
| `pulse-ftc-stubs` | never | stand-ins | Desktop compile only; not published |

Platform-neutral users depend on `pulse-core` only.

## What PULSE solves

- Duplicate encoder / voltage / switch reads stretching the control loop
- Libraries clearing REV bulk caches independently
- Freshness lies (stale data labeled valid)
- Ad-hoc even/odd/third-cycle sampling that does not compose

## What PULSE does not solve

Command scheduling, path planning, mechanism orchestration, vision processing, output arbitration, or turning unrelated I²C operations into one physical transaction. PULSE v1 owns **input sampling, deduplication, scheduling, caching, freshness, and observability**.

## Direct callback versus cached callback

A library should accept `IntSupplier` (or `LongSupplier` / `DoubleSupplier` / `BooleanSupplier`):

```java
public MimicLift(IntSupplier position) {
    this.position = position;
}
```

Standalone:

```java
new MimicLift(motor::getCurrentPosition);
```

Composed with PULSE:

```java
IntSupplier cached = pulse.registerInt(
        ELEVATOR_POSITION, motor::getCurrentPosition, SamplingPolicy.everyCycle());
new MimicLift(cached);
```

`motor::getCurrentPosition` is retained privately by PULSE. The returned supplier reads the published cache and **never** invokes the physical getter during an active cycle. Binding that cached supplier as a physical source fails at configuration time.

## Build

```text
./gradlew check
./gradlew javadocAll
```

Windows: `.\gradlew.bat check`.

Java 11. `check` runs tests, Spotless, javadoc, example compile, FTC SDK compile against RobotCore 11.2.0, and robot-artifact classpath assertions.

Zero-auth student path: keep `allsparks-contracts` as a sibling checkout (`includeBuild`).

## License

MIT. See [LICENSE](LICENSE).

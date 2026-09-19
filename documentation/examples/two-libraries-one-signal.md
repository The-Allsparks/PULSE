# Two libraries, one physical read

```java
SignalKey<Integer> ELEVATOR_POSITION = SignalKey.intKey("elevator", "position");

IntSupplier cached = pulse.registerInt(
        ELEVATOR_POSITION, motor::getCurrentPosition, SamplingPolicy.everyCycle());

MimicLike mimic = new MimicLike(cached);
AmperLike amper = new AmperLike(cached);
pulse.start();
pulse.capture(cycleId, nowNanos);
mimic.update();
amper.update();
```

`motor.getCurrentPosition()` ran once. Both libraries saw the same cached ticks.

Compile-checked copy: `pulse-examples` `TwoLibrariesOneSignalExample`.

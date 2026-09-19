# Minimal FTC integration

No team-specific device names. Replace the placeholder encoder with a hardware-map motor getter.

```java
Pulse pulse = PulseFtc.create(hardwareMap);
IntSupplier left = pulse.registerInt(
        SignalKey.intKey("drive", "leftEncoder"),
        motor::getCurrentPosition,
        SamplingPolicy.everyCycle());
FtcPulseLifecycle lifecycle = new FtcPulseLifecycle(pulse);
// after every library has declared/bound:
lifecycle.onInit();   // freeze + start; Hub MANUAL cache configured

// each loop:
lifecycle.onLoop();   // clear each Hub once, then due reads, then publish
int ticks = left.getAsInt();

// stop:
lifecycle.onStop();   // no further hardware reads
```

Compile-checked copy: `pulse-examples` `MinimalFtcOpModeExample`.

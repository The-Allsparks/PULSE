# TRACE metrics consumption

TRACE must not be a PULSE dependency. After `pulse.capture(...)`, TeamCode `TracePulseAdapter.record(metrics)` copies primitives. BumbleBee Drive PULSE calls it from the OpMode thread, not from `capture()`.

```java
TracePulseAdapter.record(pulse.metrics(), cycle);
```

Read-plan text: `pulse.readPlan().describe()`. Call that after freeze, not inside `capture()`.

`readerDurationsNanos()` and `groupDurationsNanos()` copy arrays and **allocate**. Do not call them from inside `capture()`.

A `ReadTimingListener` may copy primitives during capture. It must not format telemetry or write files.

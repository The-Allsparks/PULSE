# Changelog

## [Unreleased]

### Added

- Initial PULSE v1: `pulse-core`, `pulse-ftc`, `pulse-testkit`, examples, and documentation.
- `HubBulkFill` / `PulseFtc.create`: auto-bind motor velocity, busy, over-current flags, analog, and digital (fields already in the Lynx bulk packet). `MotorCurrentFill` binds per-motor `getCurrent` as on-demand `OTHER` (not bulk). `requestOnce` / `isRequested` / `tryGet` / `tryGetDouble` / `tryGetBoolean` / `contains` implement the contracts demand and cache-peek SPI. Default cap is one on-demand OTHER read per capture; extras stay queued. `PulseFtc.create` skips that read when capture has already used 15 ms.
- `PulseMetrics` one-shot scheduled / executed / deferred counts and last executed key so TRACE can correlate a loop-duration spike with `getCurrent`.
- `ReadBus` (`HUB_BULK` / `OTHER` / `I2C`): capture runs required hub-bulk getters before I²C so encoder ticks and hub voltage can share one REV MANUAL packet. Pinpoint should use `I2C` when added.
- Deterministic read-plan compilation, period/phase unions, `everyNanos` time scheduling, automatic spreading, coherent groups, double-buffered snapshots, optional cycle budget, REV MANUAL bulk-cache adapter.
- Cached and derived suppliers for int, long, double, boolean, and object keys; `ReadTimingListener`; per-group capture durations on `PulseMetrics`.
- TeamCode copies `PulseMetrics` primitives after `capture()` (BumbleBee `TracePulseAdapter`). PULSE core stays TRACE-free.
- Depends on `allsparks-contracts` input SPI ([ADR-0003](https://github.com/The-Allsparks/allsparks-contracts/blob/main/docs/architecture/ADR-0003-input-sampling-spi.md)).
- Capture-path `Reason` reuse: a static reader-disabled fault, and the first `read-failed` `Reason` is kept for a failure streak. `InputValues.validity` / `isFresh` / capture timestamp and cycle id are implemented without allocating a `Sample`.

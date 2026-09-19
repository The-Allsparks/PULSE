# PULSE architecture

## Problem

FTC control loops pay for every `getCurrentPosition`, `getVoltage`, and digital read. Independently written libraries each call the same getters. REV Hub `BulkCachingMode.AUTO` still repeats logical calls. `MANUAL` helps only if **one owner** clears each Hub **once** per cycle.

PULSE is that owner for *inputs*. It is not a robot framework.

## Dependency diagram

```text
allsparks-contracts   (SignalKey, SamplingPolicy, Sample, InputRegistrar, Validity, MonotonicClock)
        ^
   pulse-core         (no FTC SDK)
        ^
   pulse-ftc          (LynxModule MANUAL cache, FtcPulseLifecycle)
        ^
   TeamCode / OpMode  (composition root; binds hardware)

pulse-testkit -> pulse-core
pulse-examples -> pulse-core + pulse-ftc (compile-only stubs on desktop)
```

Forbidden edges:

```text
pulse-core  -X->  FTC SDK, Android, AMPER, TRACE, MIMIC, HELM, ViDAR, FORGE, TeamCode
MIMIC/AMPER/Pedro/HELM/TRACE/ViDAR  -X->  org.allsparks.pulse
```

## Lifecycle

```text
CONFIGURING  --freeze()-->  FROZEN  --start()/first capture-->  RUNNING  --stop()-->  STOPPED
```

Robot loop:

```java
long cycleId = nextCycleId();
long now = clock.nowNanos();
pulse.capture(cycleId, now);
mimic.update();
amper.update();
pedro.update();
helm.update();
trace.record();
```

`capture()`:

1. Validate strictly increasing `cycleId`
2. Clear each configured Hub cache **exactly once**
3. Compute due from schedules, then merge coherent groups
4. Admit queued `requestOnce` marks (after group merge, so a current demand does not recapture hub-bulk). At most one on-demand `OTHER` physical read per capture by default; extras stay queued
5. Execute due **critical** readers
6. Execute due **normal** readers; skip admitted on-demand `OTHER` (not CRITICAL) if `onDemandOtherBudgetNanos` has already elapsed and leave the mark queued
7. Execute due **optional** readers only while the cycle budget remains
8. Compute due derived signals from the write buffer
9. Publish the completed snapshot (double buffer swap)
10. Record bounded metrics, including one-shot scheduled / executed / deferred

Consumers never see a mixed current-cycle snapshot. Cached readers always read the **published** frame, including if a physical callback accidentally reads PULSE during capture (they see the previous complete cycle).

## Registration and binding

Configuration-time only. Fail fast:

| Situation | Behavior |
| --------- | -------- |
| Read before first capture | `MISSING`, primitive `0`, not fresh |
| Read after stop | Last published values; `capture()` refused |
| Failed physical read | Last-known-good retained, `INVALID`, not fresh, fault `Reason` |
| Duplicate bindings | Fail unless the same supplier instance |
| Duplicate requirements | Policy union, strongest priority wins |
| Registration after freeze | `PulseException` |
| Missing required bindings | `PulseException` at freeze |
| Type mismatch | `PulseException` |
| Cached reader bound as physical | `PulseException` |
| `requestOnce` / `isRequested` before freeze / unknown key | `PulseException` |
| `tryGet` / `contains` unknown key | `Sample.missing()` / false; no hardware |
| `tryGetDouble` / `tryGetBoolean` unknown key | `NaN` / false; no hardware |

Do not deduplicate raw lambdas. Two `motor::getCurrentPosition` references are not guaranteed equal. Dedup is by `SignalKey`.

## Period / phase schedules

Stored as period and phase, or a compiled predicate set:

| Name | Period | Phase |
| ---- | ------ | ----- |
| Every cycle | 1 | 0 |
| Every-even | 2 | 0 |
| Every-odd | 2 | 1 |
| Every-3rd-A | 3 | 0 |
| Every-3rd-B | 3 | 1 |
| Every-3rd-C | 3 | 2 |

`SamplingPolicy.spreadAcrossCycles(3)` leaves phase unassigned. At freeze, PULSE assigns phases with a deterministic count-based load balancer. Explicit phases are preserved.

Unions:

- Even + odd = every cycle
- Third-A + B + C = every cycle
- Third-A + C = phases A and C (predicate set, no unbounded LCM)

`everyNanos(period)` is due when the elapsed monotonic time since the last **valid or stale** sample is at least `period`, or the signal has never been captured. It unions with cycle predicates: the signal is due if **either** schedule matches. Coherent groups still share one due decision (any member due captures the whole group). Time-based due uses `SamplingPolicy.isDue(cycleId, nowNanos, lastCaptureNanos)` and does not allocate per cycle.

## Coherent groups

```java
requirements.requireGroup(
        "drive-localization",
        SamplingPolicy.everyCycle(),
        LEFT_ENCODER, RIGHT_ENCODER, STRAFE_ENCODER, IMU_HEADING);
```

All members share one due decision. Member schedules are unioned. Overlapping groups merge policies so localization/safety inputs are not independently phase-shifted.

## Freshness

`Sample` uses contracts `Validity`:

- `VALID` + `updatedThisCycle` = fresh
- skipped / deferred retained values are **not** fresh; age is `currentCycleId - captureCycleId`
- failed reads are `INVALID` with last-known-good and a `Reason`
- never present stale data as fresh

Prefer `getInt` / `getDouble` plus `validity()` / `isFresh()` / `captureTimestampNanos()` on the loop. `get()` allocates a `Sample` and boxes primitives. Cached suppliers always read the published frame.

## REV bulk caching

`pulse-ftc` discovers `hardwareMap.getAll(LynxModule.class)`, sets `BulkCachingMode.MANUAL` once, and clears each Hub once at the start of `capture()`. Libraries must not clear PULSE-owned caches.

PULSE deduplicates **logical getter calls**. It cannot merge unrelated I²C transactions into the motor bulk packet.

`ReadBus` only sequences those getters:

1. Required `HUB_BULK` (encoders, hub voltage, analog) — first getter after the clear fills one Lynx bulk packet; the rest of that bus are cache hits
2. Required `OTHER`
3. Required `I2C` (Hub IMU now; Pinpoint later)
4. Optional readers in the same bus order

TeamCode tags drive ticks and AMPER voltage `HUB_BULK` and puts them in coherent group `hub-bulk` so they share one due decision. Heading is `I2C` and is not in that group.

`PulseFtc.create` also runs `HubBulkFill.bind`: every `DcMotorEx` velocity / `isBusy` / `isOverCurrent`, every analog port, and every digital channel on the hardware map. Those fields are already in `cmdGetBulkInputData`. Encoder **position** keys stay TeamCode-owned. `MotorCurrentFill.bind` registers per-motor `getCurrent` as `onDemand` `OTHER` so AMPER can `requestOnce` after an over-current flag. A device added to the RC config later is sampled without a new TeamCode bind. Default `maxOnDemandOtherPerCapture` is 1 so two libraries cannot stack `getCurrent` in one loop; `PulseFtc.create` also sets a 15 ms on-demand OTHER deadline so a late capture skips the amp read and AMPER retries while the flag stays true.

## Metrics / TRACE

PULSE does not require TRACE. After `capture()`, read `PulseMetrics`:

- total / max / rolling capture duration
- per-reader duration (copy after capture; allocates)
- per-group duration and group ids (copy after capture; allocates)
- scheduled / executed / deferred / failed / stale counts
- one-shot scheduled / executed / deferred counts and last executed one-shot key
- current cycle and phase
- read-plan description (`ReadPlan.describe()`)

Optional `ReadTimingListener` on `PulseSettings` receives start/reader/group/finish primitives during capture. Implementations must not format telemetry, write files, or perform I/O. TRACE should copy from `PulseMetrics` after `capture()`.

`capture()` does not format telemetry, serialize JSON, write files, or use the network.

## Pedro without a PULSE dependency

Pedro should take `IntSupplier` encoder sources. TeamCode may pass `motor::getCurrentPosition` or a PULSE cached supplier. See `pulse-examples` `PedroCachedEncoderPattern` and [documentation/examples](../documentation/examples/pedro-cached-encoders.md).

## Output arbitration

Not in v1. Existing contracts do not require it.

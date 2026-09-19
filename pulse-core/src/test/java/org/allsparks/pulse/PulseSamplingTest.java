package org.allsparks.pulse;

import static org.allsparks.pulse.testkit.PulseAssertions.assertNotFresh;
import static org.allsparks.pulse.testkit.PulseAssertions.assertPlansEqual;
import static org.allsparks.pulse.testkit.PulseAssertions.assertReadCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.Sample;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.contracts.observation.Validity;
import org.allsparks.contracts.status.Reason;
import org.allsparks.pulse.testkit.CountingHubModule;
import org.allsparks.pulse.testkit.CountingIntSource;
import org.allsparks.pulse.testkit.DelayedIntSource;
import org.allsparks.pulse.testkit.FailingIntSource;
import org.allsparks.pulse.testkit.FakeClock;
import org.allsparks.pulse.testkit.PulseCycleRunner;
import org.junit.jupiter.api.Test;

class PulseSamplingTest {

    private static final SignalKey<Integer> ELEVATOR = SignalKey.intKey("elevator", "position");
    private static final SignalKey<Integer> LEFT = SignalKey.intKey("drive", "leftEncoder");
    private static final SignalKey<Integer> RIGHT = SignalKey.intKey("drive", "rightEncoder");
    private static final SignalKey<Integer> STRAFE = SignalKey.intKey("drive", "strafeEncoder");
    private static final SignalKey<Integer> HEADING = SignalKey.intKey("imu", "heading");
    private static final SignalKey<Integer> OPTIONAL = SignalKey.intKey("sensor", "optional");
    private static final SignalKey<Integer> SAFETY = SignalKey.intKey("limit", "switch");
    private static final SignalKey<Double> VOLTAGE = SignalKey.doubleKey("amper", "controlHubVoltage");
    private static final SignalKey<Double> YAW = SignalKey.doubleKey("imu", "yaw");
    private static final SignalKey<Double> CURRENT = SignalKey.doubleKey("motor", "front_left_drive/currentAmps");
    private static final SignalKey<Double> CURRENT_RIGHT =
            SignalKey.doubleKey("motor", "front_right_drive/currentAmps");
    private static final SignalKey<Integer> UNKNOWN = SignalKey.intKey("missing", "signal");

    @Test
    void twoConsumersOnePhysicalRead() {
        CountingIntSource hardware = new CountingIntSource(42);
        Pulse pulse = new Pulse();
        IntSupplier mimic = pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        IntSupplier amper = pulse.cachedInt(ELEVATOR);
        pulse.freeze();
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals(42, mimic.getAsInt());
        assertEquals(42, amper.getAsInt());
        assertEquals(42, pulse.getInt(ELEVATOR));
        assertReadCount(hardware, 1);
        pulse.capture(2L, 1L);
        assertReadCount(hardware, 2);
    }

    @Test
    void repeatedCachedReadsDoNotHitHardware() {
        CountingIntSource hardware = new CountingIntSource(7);
        Pulse pulse = new Pulse();
        IntSupplier cached = pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 0L);
        for (int i = 0; i < 50; i++) {
            assertEquals(7, cached.getAsInt());
        }
        assertReadCount(hardware, 1);
    }

    @Test
    void everyCycleScheduling() {
        CountingIntSource hardware = new CountingIntSource(1);
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        PulseCycleRunner runner = runner(pulse);
        runner.capture();
        runner.capture();
        runner.capture();
        assertReadCount(hardware, 3);
    }

    @Test
    void oddEvenScheduling() {
        CountingIntSource even = new CountingIntSource(1);
        CountingIntSource odd = new CountingIntSource(2);
        Pulse pulse = new Pulse();
        pulse.registerInt(LEFT, even, SamplingPolicy.everyEven());
        pulse.registerInt(RIGHT, odd, SamplingPolicy.everyOdd());
        PulseCycleRunner runner = runner(pulse);
        runner.capture(); // cycle 1 odd
        assertReadCount(even, 0);
        assertReadCount(odd, 1);
        runner.capture(); // cycle 2 even
        assertReadCount(even, 1);
        assertReadCount(odd, 1);
    }

    @Test
    void thirdABCScheduling() {
        CountingIntSource a = new CountingIntSource(1);
        CountingIntSource b = new CountingIntSource(2);
        CountingIntSource c = new CountingIntSource(3);
        Pulse pulse = new Pulse();
        pulse.registerInt(LEFT, a, SamplingPolicy.everyThirdA());
        pulse.registerInt(RIGHT, b, SamplingPolicy.everyThirdB());
        pulse.registerInt(STRAFE, c, SamplingPolicy.everyThirdC());
        PulseCycleRunner runner = runner(pulse);
        runner.capture(); // 1 phase 1
        assertEquals(0, a.reads);
        assertEquals(1, b.reads);
        assertEquals(0, c.reads);
        runner.capture(); // 2 phase 2
        assertEquals(0, a.reads);
        assertEquals(1, b.reads);
        assertEquals(1, c.reads);
        runner.capture(); // 3 phase 0
        assertEquals(1, a.reads);
        assertEquals(1, b.reads);
        assertEquals(1, c.reads);
    }

    @Test
    void scheduleUnionsSimplify() {
        CountingIntSource hardware = new CountingIntSource(9);
        Pulse pulse = new Pulse();
        pulse.require(ELEVATOR, SamplingPolicy.everyEven(), InputPriority.NORMAL);
        pulse.require(ELEVATOR, SamplingPolicy.everyOdd(), InputPriority.NORMAL);
        pulse.bindInt(ELEVATOR, hardware);
        PulseCycleRunner runner = runner(pulse);
        runner.capture();
        runner.capture();
        assertReadCount(hardware, 2);
        assertEquals(SamplingPolicy.everyCycle(), pulse.readPlan().entryAt(0).schedule());
    }

    @Test
    void flexiblePhasesAreDeterministic() {
        CountingIntSource first = new CountingIntSource(1);
        CountingIntSource second = new CountingIntSource(2);
        CountingIntSource third = new CountingIntSource(3);
        Pulse pulse = spreadPulse(first, second, third);
        Pulse again = spreadPulse(third, first, second);
        assertPlansEqual(pulse.readPlan(), again.readPlan());
        PulseCycleRunner runner = runner(pulse);
        runner.capture();
        runner.capture();
        runner.capture();
        assertEquals(1, first.reads);
        assertEquals(1, second.reads);
        assertEquals(1, third.reads);
    }

    @Test
    void coherentGroupsAreSampledTogether() {
        CountingIntSource left = new CountingIntSource(1);
        CountingIntSource right = new CountingIntSource(2);
        CountingIntSource strafe = new CountingIntSource(3);
        CountingIntSource heading = new CountingIntSource(4);
        Pulse pulse = new Pulse();
        pulse.requireGroup(
                "drive-localization", SamplingPolicy.everyOdd(), InputPriority.CRITICAL, LEFT, RIGHT, STRAFE, HEADING);
        pulse.bindInt(LEFT, left);
        pulse.bindInt(RIGHT, right);
        pulse.bindInt(STRAFE, strafe);
        pulse.bindInt(HEADING, heading);
        PulseCycleRunner runner = runner(pulse);
        runner.capture(); // odd
        assertEquals(1, left.reads);
        assertEquals(1, right.reads);
        assertEquals(1, strafe.reads);
        assertEquals(1, heading.reads);
        runner.capture(); // even, all skipped together
        assertEquals(1, left.reads);
        assertEquals(1, right.reads);
        assertEquals(1, strafe.reads);
        assertEquals(1, heading.reads);
    }

    @Test
    void skippedSignalsRetainAge() {
        CountingIntSource hardware = new CountingIntSource(11);
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyOdd());
        PulseCycleRunner runner = runner(pulse);
        long odd = runner.capture();
        Sample<Integer> fresh = pulse.get(ELEVATOR);
        assertTrue(fresh.isFresh());
        assertEquals(0L, fresh.ageCycles(odd));
        long even = runner.capture();
        Sample<Integer> skipped = pulse.get(ELEVATOR);
        assertNotFresh(skipped);
        assertEquals(Integer.valueOf(11), skipped.orNull());
        assertEquals(1L, skipped.ageCycles(even));
        assertReadCount(hardware, 1);
    }

    @Test
    void failedReadsAreNotFresh() {
        FailingIntSource hardware = new FailingIntSource();
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 0L);
        Sample<Integer> sample = pulse.get(ELEVATOR);
        assertNotFresh(sample);
        assertEquals(Validity.INVALID, sample.validity());
        assertEquals(Validity.INVALID, pulse.validity(ELEVATOR));
        assertFalse(pulse.isFresh(ELEVATOR));
        assertTrue(sample.fault().isPresent());
        assertEquals(1, hardware.reads);
        Reason first = sample.fault().get();
        pulse.capture(2L, 1L);
        assertSame(first, pulse.get(ELEVATOR).fault().get());
        assertEquals(2, hardware.reads);
    }

    @Test
    void disabledReaderReusesSharedFault() {
        FailingIntSource hardware = new FailingIntSource();
        Pulse pulse = new Pulse(PulseSettings.builder()
                .failurePolicy(FailurePolicy.builder().maxConsecutiveFailures(1).build())
                .build());
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals("read-failed", pulse.get(ELEVATOR).fault().get().code());
        assertEquals(1, hardware.reads);
        pulse.capture(2L, 1L);
        Reason disabled = pulse.get(ELEVATOR).fault().get();
        assertEquals("reader-disabled", disabled.code());
        assertEquals(Validity.INVALID, pulse.validity(ELEVATOR));
        assertFalse(pulse.isFresh(ELEVATOR));
        pulse.capture(3L, 2L);
        assertSame(disabled, pulse.get(ELEVATOR).fault().get());
        assertEquals(1, hardware.reads);
    }

    @Test
    void primitiveMetadataMatchesSample() {
        CountingIntSource hardware = new CountingIntSource(11);
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 40L);
        Sample<Integer> sample = pulse.get(ELEVATOR);
        assertEquals(sample.validity(), pulse.validity(ELEVATOR));
        assertEquals(sample.isFresh(), pulse.isFresh(ELEVATOR));
        assertEquals(sample.captureTimestampNanos(), pulse.captureTimestampNanos(ELEVATOR));
        assertEquals(sample.captureCycleId(), pulse.captureCycleId(ELEVATOR));
        assertTrue(pulse.isFresh(ELEVATOR));
    }

    @Test
    void criticalInputsAreNotSkippedUnderBudget() {
        FakeClock clock = new FakeClock();
        DelayedIntSource critical = new DelayedIntSource(clock, 1_000_000L, 5);
        DelayedIntSource optional = new DelayedIntSource(clock, 1_000_000L, 6);
        Pulse pulse = new Pulse(
                PulseSettings.builder().clock(clock).cycleBudgetNanos(1000L).build());
        pulse.registerInt(SAFETY, critical, SamplingPolicy.everyCycle(), InputPriority.CRITICAL);
        pulse.registerInt(OPTIONAL, optional, SamplingPolicy.everyCycle(), InputPriority.OPTIONAL);
        pulse.start();
        pulse.capture(1L, clock.nowNanos());
        assertEquals(1, critical.reads);
        assertEquals(0, optional.reads);
        assertTrue(pulse.get(SAFETY).isFresh());
        assertNotFresh(pulse.get(OPTIONAL));
        assertEquals(1, pulse.metrics().deferredReads());
    }

    @Test
    void optionalInputsCanBeDeferred() {
        FakeClock clock = new FakeClock(100L);
        DelayedIntSource optional = new DelayedIntSource(clock, 50L, 3);
        Pulse pulse = new Pulse(
                PulseSettings.builder().clock(clock).cycleBudgetNanos(1L).build());
        pulse.registerInt(OPTIONAL, optional, SamplingPolicy.everyCycle(), InputPriority.OPTIONAL);
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals(0, optional.reads);
        assertEquals(1, pulse.metrics().deferredReads());
        Sample<Integer> sample = pulse.get(OPTIONAL);
        assertNotFresh(sample);
        assertEquals(Validity.MISSING, sample.validity());
    }

    @Test
    void registrationAfterFreezeFails() {
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, new CountingIntSource(), SamplingPolicy.everyCycle());
        pulse.freeze();
        assertThrows(
                PulseException.class,
                () -> pulse.registerInt(LEFT, new CountingIntSource(), SamplingPolicy.everyCycle()));
    }

    @Test
    void conflictingPhysicalBindingsFail() {
        Pulse pulse = new Pulse();
        pulse.bindInt(ELEVATOR, new CountingIntSource(1));
        assertThrows(PulseException.class, () -> pulse.bindInt(ELEVATOR, new CountingIntSource(2)));
    }

    @Test
    void missingBindingsFailBeforeStart() {
        Pulse pulse = new Pulse();
        pulse.require(ELEVATOR, SamplingPolicy.everyCycle(), InputPriority.NORMAL);
        assertThrows(PulseException.class, pulse::freeze);
    }

    @Test
    void snapshotIsNotPartiallyUpdated() {
        CountingIntSource left = new CountingIntSource(1);
        class ObservingSource implements java.util.function.IntSupplier {
            final Pulse pulse;
            int observedLeft;

            ObservingSource(Pulse pulse) {
                this.pulse = pulse;
            }

            @Override
            public int getAsInt() {
                observedLeft = pulse.getInt(LEFT);
                return 99;
            }
        }
        Pulse pulse = new Pulse();
        ObservingSource right = new ObservingSource(pulse);
        pulse.registerInt(LEFT, left, SamplingPolicy.everyCycle());
        pulse.registerInt(RIGHT, right, SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 0L);
        left.value = 5;
        pulse.capture(2L, 1L);
        assertEquals(1, right.observedLeft);
        assertEquals(5, pulse.getInt(LEFT));
        assertEquals(99, pulse.getInt(RIGHT));
    }

    @Test
    void stopPreventsFurtherHardwareReads() {
        CountingIntSource hardware = new CountingIntSource(4);
        Pulse pulse = new Pulse();
        IntSupplier cached = pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 0L);
        pulse.stop();
        assertThrows(PulseException.class, () -> pulse.capture(2L, 1L));
        assertEquals(4, cached.getAsInt());
        assertReadCount(hardware, 1);
        assertEquals(PulseLifecycle.STOPPED, pulse.lifecycle());
    }

    @Test
    void hubCacheClearsOncePerCapture() {
        CountingHubModule hubA = new CountingHubModule("A");
        CountingHubModule hubB = new CountingHubModule("B");
        CountingIntSource hardware = new CountingIntSource(1);
        Pulse pulse = new Pulse(PulseSettings.builder()
                .hubCacheControl(new ListHubCacheControl(hubA, hubB))
                .build());
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyCycle());
        pulse.start();
        assertEquals(1, hubA.configures);
        pulse.capture(1L, 0L);
        pulse.capture(2L, 1L);
        assertEquals(2, hubA.clears);
        assertEquals(2, hubB.clears);
        assertEquals(1, hubA.configures);
        assertEquals(1, hubB.configures);
    }

    @Test
    void compilationIsDeterministic() {
        Pulse first = new Pulse();
        first.registerInt(RIGHT, new CountingIntSource(2), SamplingPolicy.spreadAcrossCycles(3));
        first.registerInt(LEFT, new CountingIntSource(1), SamplingPolicy.spreadAcrossCycles(3));
        first.freeze();
        Pulse second = new Pulse();
        second.registerInt(LEFT, new CountingIntSource(1), SamplingPolicy.spreadAcrossCycles(3));
        second.registerInt(RIGHT, new CountingIntSource(2), SamplingPolicy.spreadAcrossCycles(3));
        second.freeze();
        assertPlansEqual(first.readPlan(), second.readPlan());
    }

    @Test
    void cachedCallbackCannotBeReboundAsPhysical() {
        Pulse pulse = new Pulse();
        IntSupplier cached = pulse.registerInt(ELEVATOR, new CountingIntSource(), SamplingPolicy.everyCycle());
        assertThrows(PulseException.class, () -> pulse.bindInt(LEFT, cached));
    }

    @Test
    void derivedSignalUsesWriteSnapshot() {
        CountingIntSource left = new CountingIntSource(10);
        CountingIntSource right = new CountingIntSource(30);
        Pulse pulse = new Pulse();
        pulse.registerInt(LEFT, left, SamplingPolicy.everyCycle());
        pulse.registerInt(RIGHT, right, SamplingPolicy.everyCycle());
        SignalKey<Integer> sum = SignalKey.intKey("drive", "sum");
        pulse.deriveInt(sum, SamplingPolicy.everyCycle(), values -> values.getInt(LEFT) + values.getInt(RIGHT));
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals(40, pulse.getInt(sum));
        assertTrue(pulse.get(sum).isFresh());
    }

    @Test
    void timeBasedPolicyCapturesWhenElapsed() {
        FakeClock clock = new FakeClock();
        CountingIntSource hardware = new CountingIntSource(1);
        Pulse pulse = new Pulse(PulseSettings.builder().clock(clock).build());
        pulse.registerInt(ELEVATOR, hardware, SamplingPolicy.everyNanos(3_000_000L));
        pulse.start();
        PulseCycleRunner runner = new PulseCycleRunner(pulse, clock, 1_000_000L);
        runner.capture();
        assertEquals(1, hardware.reads);
        runner.capture();
        runner.capture();
        assertEquals(1, hardware.reads);
        runner.capture();
        assertEquals(2, hardware.reads);
    }

    @Test
    void timeBasedUnionsWithCyclePredicates() {
        FakeClock clock = new FakeClock();
        CountingIntSource hardware = new CountingIntSource(1);
        Pulse pulse = new Pulse(PulseSettings.builder().clock(clock).build());
        pulse.require(ELEVATOR, SamplingPolicy.everyEven(), InputPriority.NORMAL);
        pulse.require(ELEVATOR, SamplingPolicy.everyNanos(10_000_000_000L), InputPriority.NORMAL);
        pulse.bindInt(ELEVATOR, hardware);
        pulse.start();
        PulseCycleRunner runner = new PulseCycleRunner(pulse, clock, 1_000_000L);
        runner.capture();
        assertEquals(1, hardware.reads);
        runner.capture();
        assertEquals(2, hardware.reads);
        runner.capture();
        assertEquals(2, hardware.reads);
    }

    @Test
    void groupMemberSchedulesUnifyToSharedDue() {
        CountingIntSource left = new CountingIntSource(1);
        CountingIntSource right = new CountingIntSource(2);
        Pulse pulse = new Pulse();
        pulse.require(LEFT, SamplingPolicy.everyEven(), InputPriority.NORMAL);
        pulse.require(RIGHT, SamplingPolicy.everyOdd(), InputPriority.NORMAL);
        pulse.requireGroup("encoders", SamplingPolicy.everyNth(4, 0), InputPriority.NORMAL, LEFT, RIGHT);
        pulse.bindInt(LEFT, left);
        pulse.bindInt(RIGHT, right);
        PulseCycleRunner runner = runner(pulse);
        runner.capture();
        runner.capture();
        assertEquals(2, left.reads);
        assertEquals(2, right.reads);
        assertEquals(SamplingPolicy.everyCycle(), pulse.readPlan().entryAt(0).schedule());
        assertEquals(SamplingPolicy.everyCycle(), pulse.readPlan().entryAt(1).schedule());
    }

    @Test
    void readTimingListenerAndGroupDurations() {
        FakeClock clock = new FakeClock();
        DelayedIntSource left = new DelayedIntSource(clock, 100L, 1);
        DelayedIntSource right = new DelayedIntSource(clock, 100L, 2);
        RecordingTiming listener = new RecordingTiming();
        Pulse pulse = new Pulse(PulseSettings.builder()
                .clock(clock)
                .readTimingListener(listener)
                .build());
        pulse.requireGroup("drive-localization", SamplingPolicy.everyCycle(), InputPriority.CRITICAL, LEFT, RIGHT);
        pulse.bindInt(LEFT, left);
        pulse.bindInt(RIGHT, right);
        pulse.start();
        pulse.capture(1L, clock.nowNanos());
        assertEquals(1, listener.started);
        assertEquals(1, listener.finished);
        assertEquals(2, listener.readers);
        assertEquals(1, listener.groups);
        assertEquals(1, pulse.metrics().groupIds().length);
        assertEquals("drive-localization", pulse.metrics().groupIds()[0]);
        assertTrue(pulse.metrics().groupDurationsNanos()[0] >= 200L);
        assertEquals(listener.lastCaptureDuration, pulse.metrics().captureDurationNanos());
    }

    @Test
    void cachedAndDerivedPrimitiveApis() {
        SignalKey<Long> ticks = SignalKey.longKey("drive", "ticks");
        SignalKey<Double> heading = SignalKey.doubleKey("imu", "heading");
        SignalKey<Boolean> limit = SignalKey.booleanKey("lift", "limit");
        SignalKey<String> label = SignalKey.of("vision", "tag", String.class);
        SignalKey<Long> doubled = SignalKey.longKey("drive", "doubled");
        SignalKey<Double> plusOne = SignalKey.doubleKey("imu", "plusOne");
        SignalKey<Boolean> notLimit = SignalKey.booleanKey("lift", "clear");
        Pulse pulse = new Pulse();
        pulse.registerLong(ticks, () -> 8L, SamplingPolicy.everyCycle());
        pulse.registerDouble(heading, () -> 1.5d, SamplingPolicy.everyCycle());
        pulse.registerBoolean(limit, () -> true, SamplingPolicy.everyCycle());
        pulse.registerObject(label, () -> "alpha", SamplingPolicy.everyCycle());
        pulse.deriveLong(doubled, SamplingPolicy.everyCycle(), values -> values.getLong(ticks) * 2L);
        pulse.deriveDouble(plusOne, SamplingPolicy.everyCycle(), values -> values.getDouble(heading) + 1.0d);
        pulse.deriveBoolean(notLimit, SamplingPolicy.everyCycle(), values -> !values.getBoolean(limit));
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals(8L, pulse.cachedLong(ticks).getAsLong());
        assertEquals(1.5d, pulse.cachedDouble(heading).getAsDouble(), 0.0d);
        assertTrue(pulse.cachedBoolean(limit).getAsBoolean());
        assertEquals("alpha", pulse.cachedObject(label).get());
        assertEquals(16L, pulse.getLong(doubled));
        assertEquals(2.5d, pulse.getDouble(plusOne), 0.0d);
        assertFalse(pulse.getBoolean(notLimit));
    }

    @Test
    void readsBeforeFirstCaptureAreMissing() {
        Pulse pulse = new Pulse();
        IntSupplier cached = pulse.registerInt(ELEVATOR, new CountingIntSource(3), SamplingPolicy.everyCycle());
        pulse.freeze();
        assertEquals(0, cached.getAsInt());
        Sample<Integer> sample = pulse.get(ELEVATOR);
        assertEquals(Validity.MISSING, sample.validity());
        assertNotFresh(sample);
    }

    @Test
    void hubBulkRequiredReadsRunBeforeI2cEvenWhenI2cIsCritical() {
        List<String> order = new ArrayList<>();
        Pulse pulse = new Pulse();
        pulse.require(LEFT, SamplingPolicy.everyCycle(), InputPriority.CRITICAL);
        pulse.require(YAW, SamplingPolicy.everyCycle(), InputPriority.CRITICAL);
        pulse.require(VOLTAGE, SamplingPolicy.everyCycle(), InputPriority.NORMAL);
        pulse.bindInt(
                LEFT,
                () -> {
                    order.add("ticks");
                    return 1;
                },
                ReadBus.HUB_BULK);
        pulse.bindDouble(
                VOLTAGE,
                () -> {
                    order.add("voltage");
                    return 12.0d;
                },
                ReadBus.HUB_BULK);
        pulse.bindDouble(
                YAW,
                () -> {
                    order.add("imu");
                    return 0.1d;
                },
                ReadBus.I2C);
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals(Arrays.asList("ticks", "voltage", "imu"), order);
        assertEquals(ReadBus.HUB_BULK, pulse.readPlan().entryAt(0).bus());
        assertEquals(ReadBus.HUB_BULK, pulse.readPlan().entryAt(1).bus());
        assertEquals(ReadBus.I2C, pulse.readPlan().entryAt(2).bus());
    }

    @Test
    void hubBulkGroupCapturesVoltageWithEncoders() {
        CountingIntSource left = new CountingIntSource(1);
        CountingIntSource right = new CountingIntSource(2);
        int[] voltageReads = new int[1];
        Pulse pulse = new Pulse();
        pulse.requireGroup("hub-bulk", SamplingPolicy.everyOdd(), InputPriority.NORMAL, LEFT, RIGHT);
        pulse.bindInt(LEFT, left, ReadBus.HUB_BULK);
        pulse.bindInt(RIGHT, right, ReadBus.HUB_BULK);
        pulse.requireGroup("hub-bulk", SamplingPolicy.everyOdd(), InputPriority.NORMAL, LEFT, VOLTAGE);
        pulse.bindDouble(
                VOLTAGE,
                () -> {
                    voltageReads[0]++;
                    return 12.4d;
                },
                ReadBus.HUB_BULK);
        PulseCycleRunner runner = runner(pulse);
        runner.capture();
        assertEquals(1, left.reads);
        assertEquals(1, right.reads);
        assertEquals(1, voltageReads[0]);
        runner.capture();
        assertEquals(1, left.reads);
        assertEquals(1, right.reads);
        assertEquals(1, voltageReads[0]);
        assertEquals("hub-bulk", pulse.readPlan().entryAt(0).groupId());
        assertEquals(12.4d, pulse.getDouble(VOLTAGE), 0.0d);
    }

    @Test
    void onDemandDoesNotReadUntilRequestOnce() {
        int[] reads = new int[1];
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, new CountingIntSource(1), SamplingPolicy.everyCycle());
        pulse.bindDouble(
                CURRENT,
                () -> {
                    reads[0]++;
                    return 2.5d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.start();
        pulse.capture(1L, 0L);
        assertEquals(0, reads[0]);
        assertFalse(pulse.isFresh(CURRENT));
        Sample<Double> peek = pulse.tryGet(CURRENT);
        assertFalse(peek.isFresh());
        pulse.requestOnce(CURRENT);
        assertTrue(pulse.isRequested(CURRENT));
        assertEquals(0, reads[0]);
        pulse.capture(2L, 1L);
        assertEquals(1, reads[0]);
        assertFalse(pulse.isRequested(CURRENT));
        assertEquals(1, pulse.metrics().oneShotScheduled());
        assertEquals(1, pulse.metrics().oneShotExecuted());
        assertEquals(0, pulse.metrics().oneShotDeferred());
        assertEquals(CURRENT.qualifiedName(), pulse.metrics().lastOneShotKey());
        assertTrue(pulse.isFresh(CURRENT));
        assertEquals(2.5d, pulse.getDouble(CURRENT), 0.0d);
        assertEquals(2.5d, pulse.tryGetDouble(CURRENT), 0.0d);
        pulse.capture(3L, 2L);
        assertEquals(1, reads[0]);
        assertFalse(pulse.isFresh(CURRENT));
        assertEquals(0, pulse.metrics().oneShotExecuted());
        assertEquals("", pulse.metrics().lastOneShotKey());
    }

    @Test
    void requestOnceBeforeFirstCaptureRunsOnFirstCapture() {
        int[] reads = new int[1];
        Pulse pulse = new Pulse();
        pulse.bindDouble(
                CURRENT,
                () -> {
                    reads[0]++;
                    return 1.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.start();
        pulse.requestOnce(CURRENT);
        pulse.requestOnce(CURRENT);
        pulse.capture(1L, 0L);
        assertEquals(1, reads[0]);
    }

    @Test
    void tryGetUnknownDoesNotThrow() {
        Pulse pulse = new Pulse();
        pulse.registerInt(ELEVATOR, new CountingIntSource(1), SamplingPolicy.everyCycle());
        pulse.start();
        pulse.capture(1L, 0L);
        assertFalse(pulse.contains(UNKNOWN));
        Sample<Integer> sample = pulse.tryGet(UNKNOWN);
        assertEquals(Validity.MISSING, sample.validity());
        assertTrue(Double.isNaN(pulse.tryGetDouble(SignalKey.doubleKey("missing", "current"))));
        assertFalse(pulse.tryGetBoolean(SignalKey.booleanKey("missing", "flag")));
        assertThrows(PulseException.class, () -> pulse.getInt(UNKNOWN));
        assertThrows(PulseException.class, () -> pulse.requestOnce(UNKNOWN));
        assertThrows(PulseException.class, () -> pulse.isRequested(UNKNOWN));
    }

    @Test
    void requestOnceBeforeFreezeFails() {
        Pulse pulse = new Pulse();
        pulse.bindDouble(CURRENT, () -> 1.0d, ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        assertThrows(PulseException.class, () -> pulse.requestOnce(CURRENT));
        assertThrows(PulseException.class, () -> pulse.isRequested(CURRENT));
    }

    @Test
    void oneDemandOtherCapHoldsSecondRequestQueued() {
        int[] left = new int[1];
        int[] right = new int[1];
        Pulse pulse = new Pulse();
        pulse.bindDouble(
                CURRENT,
                () -> {
                    left[0]++;
                    return 1.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.bindDouble(
                CURRENT_RIGHT,
                () -> {
                    right[0]++;
                    return 2.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT_RIGHT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.start();
        pulse.requestOnce(CURRENT);
        pulse.requestOnce(CURRENT_RIGHT);
        pulse.capture(1L, 0L);
        assertEquals(1, left[0] + right[0]);
        assertEquals(1, pulse.metrics().oneShotExecuted());
        assertEquals(1, pulse.metrics().oneShotDeferred());
        assertEquals(2, pulse.metrics().oneShotScheduled());
        assertTrue(pulse.isRequested(CURRENT) ^ pulse.isRequested(CURRENT_RIGHT));
        pulse.capture(2L, 1L);
        assertEquals(1, left[0]);
        assertEquals(1, right[0]);
        assertFalse(pulse.isRequested(CURRENT));
        assertFalse(pulse.isRequested(CURRENT_RIGHT));
    }

    @Test
    void i2cOneShotDoesNotConsumeOtherCap() {
        int[] otherReads = new int[1];
        int[] i2cReads = new int[1];
        Pulse pulse = new Pulse();
        pulse.bindDouble(
                CURRENT,
                () -> {
                    otherReads[0]++;
                    return 1.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.bindDouble(
                YAW,
                () -> {
                    i2cReads[0]++;
                    return 0.5d;
                },
                ReadBus.I2C);
        pulse.require(YAW, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.start();
        pulse.requestOnce(CURRENT);
        pulse.requestOnce(YAW);
        pulse.capture(1L, 0L);
        assertEquals(1, otherReads[0]);
        assertEquals(1, i2cReads[0]);
        assertEquals(2, pulse.metrics().oneShotExecuted());
        assertEquals(0, pulse.metrics().oneShotDeferred());
    }

    @Test
    void onDemandOtherBudgetLeavesRequestQueued() {
        FakeClock clock = new FakeClock();
        int[] bulkReads = new int[1];
        int[] currentReads = new int[1];
        Pulse pulse = new Pulse(PulseSettings.builder()
                .clock(clock)
                .onDemandOtherBudgetNanos(1_000L)
                .build());
        pulse.bindInt(
                LEFT,
                () -> {
                    bulkReads[0]++;
                    if (bulkReads[0] == 2) {
                        clock.advanceNanos(5_000L);
                    }
                    return 1;
                },
                ReadBus.HUB_BULK);
        pulse.require(LEFT, SamplingPolicy.everyCycle(), InputPriority.NORMAL);
        pulse.bindDouble(
                CURRENT,
                () -> {
                    currentReads[0]++;
                    return 3.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.start();
        pulse.capture(1L, clock.nowNanos());
        pulse.requestOnce(CURRENT);
        pulse.capture(2L, clock.nowNanos());
        assertEquals(0, currentReads[0]);
        assertTrue(pulse.isRequested(CURRENT));
        assertEquals(1, pulse.metrics().oneShotScheduled());
        assertEquals(0, pulse.metrics().oneShotExecuted());
        assertEquals(1, pulse.metrics().oneShotDeferred());
        pulse.capture(3L, clock.nowNanos());
        assertEquals(1, currentReads[0]);
        assertFalse(pulse.isRequested(CURRENT));
        assertEquals(1, pulse.metrics().oneShotExecuted());
    }

    @Test
    void unlimitedOtherCapRunsBothOneShots() {
        int[] left = new int[1];
        int[] right = new int[1];
        Pulse pulse =
                new Pulse(PulseSettings.builder().maxOnDemandOtherPerCapture(0).build());
        pulse.bindDouble(
                CURRENT,
                () -> {
                    left[0]++;
                    return 1.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.bindDouble(
                CURRENT_RIGHT,
                () -> {
                    right[0]++;
                    return 2.0d;
                },
                ReadBus.OTHER);
        pulse.require(CURRENT_RIGHT, SamplingPolicy.onDemand(), InputPriority.NORMAL);
        pulse.start();
        pulse.requestOnce(CURRENT);
        pulse.requestOnce(CURRENT_RIGHT);
        pulse.capture(1L, 0L);
        assertEquals(1, left[0]);
        assertEquals(1, right[0]);
        assertEquals(2, pulse.metrics().oneShotExecuted());
    }

    @Test
    void conflictingReadBusFails() {
        Pulse pulse = new Pulse();
        pulse.bindInt(LEFT, new CountingIntSource(1), ReadBus.HUB_BULK);
        assertThrows(PulseException.class, () -> pulse.assignBus(LEFT, ReadBus.I2C));
    }

    private static Pulse spreadPulse(CountingIntSource a, CountingIntSource b, CountingIntSource c) {
        Pulse pulse = new Pulse();
        pulse.registerInt(SignalKey.intKey("n", "a"), a, SamplingPolicy.spreadAcrossCycles(3));
        pulse.registerInt(SignalKey.intKey("n", "b"), b, SamplingPolicy.spreadAcrossCycles(3));
        pulse.registerInt(SignalKey.intKey("n", "c"), c, SamplingPolicy.spreadAcrossCycles(3));
        pulse.freeze();
        return pulse;
    }

    private static PulseCycleRunner runner(Pulse pulse) {
        FakeClock clock = new FakeClock();
        pulse.start();
        return new PulseCycleRunner(pulse, clock);
    }

    private static final class RecordingTiming implements ReadTimingListener {
        int started;
        int finished;
        int readers;
        int groups;
        long lastCaptureDuration;

        @Override
        public void onCaptureStarted(long cycleId, long nowNanos) {
            started++;
        }

        @Override
        public void onReaderFinished(int slotIndex, long durationNanos, boolean failed) {
            readers++;
        }

        @Override
        public void onGroupFinished(int groupIndex, long durationNanos) {
            groups++;
        }

        @Override
        public void onCaptureFinished(long cycleId, long durationNanos) {
            finished++;
            lastCaptureDuration = durationNanos;
        }
    }
}

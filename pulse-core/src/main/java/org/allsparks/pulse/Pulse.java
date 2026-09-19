package org.allsparks.pulse;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.allsparks.contracts.input.InputDemand;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.InputRegistrar;
import org.allsparks.contracts.input.InputValues;
import org.allsparks.contracts.input.Sample;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.contracts.observation.Validity;
import org.allsparks.pulse.internal.PulseRuntime;

/**
 * Prioritized Unified Loop Sampling Engine.
 *
 * <p>Libraries declare {@link SignalKey} requirements or accept {@link IntSupplier}
 * callbacks. TeamCode binds physical getters once. {@link #capture(long, long)}
 * reads each due physical signal at most once and publishes a complete snapshot.
 *
 * <p>This class does not depend on the FTC SDK. Use {@code pulse-ftc} for REV
 * Hub bulk-cache adapters. Assign {@link ReadBus#HUB_BULK} vs {@link ReadBus#I2C}
 * so capture fills one MANUAL packet (encoders, voltage) before I²C (IMU).
 */
public final class Pulse implements InputRegistrar, InputDemand, InputValues {

    private final PulseRuntime runtime;

    public Pulse() {
        this(PulseSettings.defaults());
    }

    public Pulse(PulseSettings settings) {
        this.runtime = new PulseRuntime(settings);
    }

    public PulseLifecycle lifecycle() {
        return runtime.lifecycle();
    }

    public PulseMetrics metrics() {
        return runtime.metrics();
    }

    public ReadPlan readPlan() {
        return runtime.readPlan();
    }

    public InputValues snapshot() {
        return runtime.snapshot();
    }

    public IntSupplier registerInt(SignalKey<Integer> key, IntSupplier physical, SamplingPolicy policy) {
        return registerInt(key, physical, policy, InputPriority.NORMAL);
    }

    public IntSupplier registerInt(
            SignalKey<Integer> key, IntSupplier physical, SamplingPolicy policy, InputPriority priority) {
        return runtime.registerInt(key, physical, policy, priority);
    }

    public LongSupplier registerLong(SignalKey<Long> key, LongSupplier physical, SamplingPolicy policy) {
        return runtime.registerLong(key, physical, policy, InputPriority.NORMAL);
    }

    public LongSupplier registerLong(
            SignalKey<Long> key, LongSupplier physical, SamplingPolicy policy, InputPriority priority) {
        return runtime.registerLong(key, physical, policy, priority);
    }

    public DoubleSupplier registerDouble(SignalKey<Double> key, DoubleSupplier physical, SamplingPolicy policy) {
        return runtime.registerDouble(key, physical, policy, InputPriority.NORMAL);
    }

    public DoubleSupplier registerDouble(
            SignalKey<Double> key, DoubleSupplier physical, SamplingPolicy policy, InputPriority priority) {
        return runtime.registerDouble(key, physical, policy, priority);
    }

    public BooleanSupplier registerBoolean(SignalKey<Boolean> key, BooleanSupplier physical, SamplingPolicy policy) {
        return runtime.registerBoolean(key, physical, policy, InputPriority.NORMAL);
    }

    public BooleanSupplier registerBoolean(
            SignalKey<Boolean> key, BooleanSupplier physical, SamplingPolicy policy, InputPriority priority) {
        return runtime.registerBoolean(key, physical, policy, priority);
    }

    public <T> Supplier<T> registerObject(SignalKey<T> key, Supplier<T> physical, SamplingPolicy policy) {
        return registerObject(key, physical, policy, InputPriority.NORMAL);
    }

    public <T> Supplier<T> registerObject(
            SignalKey<T> key, Supplier<T> physical, SamplingPolicy policy, InputPriority priority) {
        return runtime.registerObject(key, physical, policy, priority);
    }

    public IntSupplier deriveInt(SignalKey<Integer> key, SamplingPolicy policy, ToIntFunction<InputValues> compute) {
        return runtime.deriveInt(key, policy, InputPriority.NORMAL, compute);
    }

    public LongSupplier deriveLong(SignalKey<Long> key, SamplingPolicy policy, ToLongFunction<InputValues> compute) {
        return runtime.deriveLong(key, policy, InputPriority.NORMAL, compute);
    }

    public DoubleSupplier deriveDouble(
            SignalKey<Double> key, SamplingPolicy policy, ToDoubleFunction<InputValues> compute) {
        return runtime.deriveDouble(key, policy, InputPriority.NORMAL, compute);
    }

    public BooleanSupplier deriveBoolean(
            SignalKey<Boolean> key, SamplingPolicy policy, Function<InputValues, Boolean> compute) {
        return runtime.deriveBoolean(key, policy, InputPriority.NORMAL, compute);
    }

    public void bindInt(SignalKey<Integer> key, IntSupplier physical) {
        runtime.bindInt(key, physical);
    }

    public void bindInt(SignalKey<Integer> key, IntSupplier physical, ReadBus bus) {
        runtime.bindInt(key, physical);
        runtime.assignBus(key, bus);
    }

    public void bindLong(SignalKey<Long> key, LongSupplier physical) {
        runtime.bindLong(key, physical);
    }

    public void bindLong(SignalKey<Long> key, LongSupplier physical, ReadBus bus) {
        runtime.bindLong(key, physical);
        runtime.assignBus(key, bus);
    }

    public void bindDouble(SignalKey<Double> key, DoubleSupplier physical) {
        runtime.bindDouble(key, physical);
    }

    public void bindDouble(SignalKey<Double> key, DoubleSupplier physical, ReadBus bus) {
        runtime.bindDouble(key, physical);
        runtime.assignBus(key, bus);
    }

    public void bindBoolean(SignalKey<Boolean> key, BooleanSupplier physical) {
        runtime.bindBoolean(key, physical);
    }

    public void bindBoolean(SignalKey<Boolean> key, BooleanSupplier physical, ReadBus bus) {
        runtime.bindBoolean(key, physical);
        runtime.assignBus(key, bus);
    }

    public <T> void bindObject(SignalKey<T> key, Supplier<T> physical) {
        runtime.bindObject(key, physical);
    }

    public <T> void bindObject(SignalKey<T> key, Supplier<T> physical, ReadBus bus) {
        runtime.bindObject(key, physical);
        runtime.assignBus(key, bus);
    }

    /**
     * Tag a signal's physical getter with a bus. Must run during CONFIGURING.
     * Hub-bulk getters capture before I²C so they share one REV MANUAL packet.
     */
    public void assignBus(SignalKey<?> key, ReadBus bus) {
        runtime.assignBus(key, bus);
    }

    public IntSupplier cachedInt(SignalKey<Integer> key) {
        return runtime.cachedInt(key);
    }

    public LongSupplier cachedLong(SignalKey<Long> key) {
        return runtime.cachedLong(key);
    }

    public DoubleSupplier cachedDouble(SignalKey<Double> key) {
        return runtime.cachedDouble(key);
    }

    public BooleanSupplier cachedBoolean(SignalKey<Boolean> key) {
        return runtime.cachedBoolean(key);
    }

    public <T> Supplier<T> cachedObject(SignalKey<T> key) {
        return runtime.cachedObject(key);
    }

    @Override
    public void require(SignalKey<?> key, SamplingPolicy policy, InputPriority priority) {
        runtime.require(key, policy, priority);
    }

    @Override
    public void requireGroup(String groupId, SamplingPolicy policy, InputPriority priority, SignalKey<?>... members) {
        runtime.requireGroup(groupId, policy, priority, members);
    }

    /**
     * Mark {@code key} due for the next {@link #capture(long, long)}. Does not
     * read hardware. Duplicate calls collapse to one physical read.
     */
    @Override
    public void requestOnce(SignalKey<?> key) {
        runtime.requestOnce(key);
    }

    /**
     * True when {@code key} is already marked for the next capture. Does not
     * read hardware.
     */
    @Override
    public boolean isRequested(SignalKey<?> key) {
        return runtime.isRequested(key);
    }

    public void freeze() {
        runtime.freeze();
    }

    public void start() {
        runtime.start();
    }

    /**
     * Capture one control cycle. Publishes a complete snapshot before return.
     * Callers must not invoke this from a background thread that touches FTC
     * hardware; the OpMode thread owns Hub I/O.
     *
     * @param cycleId strictly increasing cycle identifier
     * @param nowNanos monotonic capture timestamp from the configured clock
     */
    public void capture(long cycleId, long nowNanos) {
        runtime.capture(cycleId, nowNanos);
    }

    public void stop() {
        runtime.stop();
    }

    @Override
    public long currentCycleId() {
        return runtime.currentCycleId();
    }

    @Override
    public <T> Sample<T> get(SignalKey<T> key) {
        return runtime.get(key);
    }

    @Override
    public int getInt(SignalKey<Integer> key) {
        return runtime.getInt(key);
    }

    @Override
    public long getLong(SignalKey<Long> key) {
        return runtime.getLong(key);
    }

    @Override
    public double getDouble(SignalKey<Double> key) {
        return runtime.getDouble(key);
    }

    @Override
    public boolean getBoolean(SignalKey<Boolean> key) {
        return runtime.getBoolean(key);
    }

    @Override
    public Validity validity(SignalKey<?> key) {
        return runtime.validity(key);
    }

    @Override
    public boolean isFresh(SignalKey<?> key) {
        return runtime.isFresh(key);
    }

    @Override
    public long captureTimestampNanos(SignalKey<?> key) {
        return runtime.captureTimestampNanos(key);
    }

    @Override
    public long captureCycleId(SignalKey<?> key) {
        return runtime.captureCycleId(key);
    }

    @Override
    public boolean contains(SignalKey<?> key) {
        return runtime.contains(key);
    }

    @Override
    public <T> Sample<T> tryGet(SignalKey<T> key) {
        return runtime.tryGet(key);
    }

    @Override
    public double tryGetDouble(SignalKey<Double> key) {
        return runtime.tryGetDouble(key);
    }

    @Override
    public boolean tryGetBoolean(SignalKey<Boolean> key) {
        return runtime.tryGetBoolean(key);
    }
}

package org.allsparks.pulse.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.allsparks.contracts.input.CoherentGroup;
import org.allsparks.contracts.input.InputDemand;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.InputRegistrar;
import org.allsparks.contracts.input.InputValues;
import org.allsparks.contracts.input.Sample;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.contracts.input.SignalValueType;
import org.allsparks.contracts.observation.Validity;
import org.allsparks.contracts.status.Reason;
import org.allsparks.contracts.time.MonotonicClock;
import org.allsparks.pulse.FailurePolicy;
import org.allsparks.pulse.HubCacheControl;
import org.allsparks.pulse.PulseCachedReader;
import org.allsparks.pulse.PulseException;
import org.allsparks.pulse.PulseLifecycle;
import org.allsparks.pulse.PulseMetrics;
import org.allsparks.pulse.PulseSettings;
import org.allsparks.pulse.ReadBus;
import org.allsparks.pulse.ReadPlan;
import org.allsparks.pulse.ReadPlanEntry;
import org.allsparks.pulse.ReadTimingListener;

/**
 * Compiled sampling engine. Public {@code Pulse} delegates here.
 */
public final class PulseRuntime implements InputRegistrar, InputDemand, InputValues {

    private static final Reason READER_DISABLED =
            Reason.of("reader-disabled", "Reader disabled after repeated failures");
    private static final ReadBus[] BUS_ORDER = {ReadBus.HUB_BULK, ReadBus.OTHER, ReadBus.I2C};

    private final PulseSettings settings;
    private final MonotonicClock clock;
    private final HubCacheControl hubs;
    private final FailurePolicy failures;
    private final ReadTimingListener timing;
    private final PulseMetrics metrics = new PulseMetrics();

    private PulseLifecycle lifecycle = PulseLifecycle.CONFIGURING;
    private final Map<SignalKey<?>, Pending> pending = new LinkedHashMap<>();
    private final List<CoherentGroup> groups = new ArrayList<>();

    private SignalSlot[] slots = new SignalSlot[0];
    private boolean[] due = new boolean[0];
    private boolean[] oneShotQueued = new boolean[0];
    private boolean[] oneShotThisCapture = new boolean[0];
    private boolean[] groupDue = new boolean[0];
    private long[] groupDurations = new long[0];
    private String[] groupIds = new String[0];
    private final Map<SignalKey<?>, Integer> indexByKey = new HashMap<>();
    private ReadPlan readPlan = new ReadPlan(new ReadPlanEntry[0]);
    private int publishedFrame;
    private int writeFrame = 1;
    private long lastCycleId = -1L;
    private long publishedCycleId = -1L;
    private int oneShotScheduled;
    private int oneShotExecuted;
    private int oneShotDeferred;
    private SignalKey<?> lastOneShotKey;
    private final FrameView publishedView = new FrameView(true);
    private final FrameView writeView = new FrameView(false);

    public PulseRuntime(PulseSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = settings.clock();
        this.hubs = settings.hubCacheControl();
        this.failures = settings.failurePolicy();
        this.timing = settings.readTimingListener();
    }

    public PulseLifecycle lifecycle() {
        return lifecycle;
    }

    public PulseMetrics metrics() {
        return metrics;
    }

    public ReadPlan readPlan() {
        if (lifecycle == PulseLifecycle.CONFIGURING) {
            throw new PulseException("Read plan is not available until freeze()");
        }
        return readPlan;
    }

    public InputValues snapshot() {
        return publishedView;
    }

    public void require(SignalKey<?> key, SamplingPolicy policy, InputPriority priority) {
        requireConfiguring();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(priority, "priority");
        Pending existing = pending.get(key);
        if (existing == null) {
            Pending created = new Pending(key);
            created.policy = policy;
            created.priority = priority;
            pending.put(key, created);
            return;
        }
        if (existing.key.valueType() != key.valueType()
                || !Objects.equals(existing.key.objectType(), key.objectType())) {
            throw new PulseException("Type mismatch for " + key.qualifiedName());
        }
        existing.policy = existing.policy == null ? policy : existing.policy.union(policy);
        existing.priority = existing.priority == null ? priority : stronger(existing.priority, priority);
    }

    @Override
    public void requireGroup(String groupId, SamplingPolicy policy, InputPriority priority, SignalKey<?>... members) {
        requireConfiguring();
        CoherentGroup group = CoherentGroup.of(groupId, policy, priority, java.util.Arrays.asList(members));
        groups.add(group);
        for (SignalKey<?> member : group.members()) {
            require(member, policy, priority);
        }
    }

    @Override
    public void requestOnce(SignalKey<?> key) {
        Objects.requireNonNull(key, "key");
        if (lifecycle == PulseLifecycle.CONFIGURING) {
            throw new PulseException("requestOnce requires freeze() first");
        }
        if (lifecycle == PulseLifecycle.STOPPED) {
            throw new PulseException("requestOnce is not allowed after stop()");
        }
        Integer index = indexByKey.get(key);
        if (index == null) {
            throw new PulseException("Unknown signal " + key.qualifiedName());
        }
        oneShotQueued[index] = true;
    }

    @Override
    public boolean isRequested(SignalKey<?> key) {
        Objects.requireNonNull(key, "key");
        if (lifecycle == PulseLifecycle.CONFIGURING) {
            throw new PulseException("isRequested requires freeze() first");
        }
        if (lifecycle == PulseLifecycle.STOPPED) {
            throw new PulseException("isRequested is not allowed after stop()");
        }
        Integer index = indexByKey.get(key);
        if (index == null) {
            throw new PulseException("Unknown signal " + key.qualifiedName());
        }
        return oneShotQueued[index];
    }

    public IntSupplier registerInt(
            SignalKey<Integer> key, IntSupplier physical, SamplingPolicy policy, InputPriority priority) {
        bindInt(key, physical);
        require(key, policy, priority);
        return cachedInt(key);
    }

    public LongSupplier registerLong(
            SignalKey<Long> key, LongSupplier physical, SamplingPolicy policy, InputPriority priority) {
        bindLong(key, physical);
        require(key, policy, priority);
        return cachedLong(key);
    }

    public DoubleSupplier registerDouble(
            SignalKey<Double> key, DoubleSupplier physical, SamplingPolicy policy, InputPriority priority) {
        bindDouble(key, physical);
        require(key, policy, priority);
        return cachedDouble(key);
    }

    public BooleanSupplier registerBoolean(
            SignalKey<Boolean> key, BooleanSupplier physical, SamplingPolicy policy, InputPriority priority) {
        bindBoolean(key, physical);
        require(key, policy, priority);
        return cachedBoolean(key);
    }

    public <T> Supplier<T> registerObject(
            SignalKey<T> key, Supplier<T> physical, SamplingPolicy policy, InputPriority priority) {
        bindObject(key, physical);
        require(key, policy, priority);
        return cachedObject(key);
    }

    public IntSupplier deriveInt(
            SignalKey<Integer> key, SamplingPolicy policy, InputPriority priority, ToIntFunction<InputValues> compute) {
        requireConfiguring();
        Objects.requireNonNull(compute, "compute");
        require(key, policy, priority);
        Pending pendingSignal = pending.get(key);
        pendingSignal.derived = true;
        pendingSignal.intDerived = compute;
        return cachedInt(key);
    }

    public LongSupplier deriveLong(
            SignalKey<Long> key, SamplingPolicy policy, InputPriority priority, ToLongFunction<InputValues> compute) {
        requireConfiguring();
        Objects.requireNonNull(compute, "compute");
        require(key, policy, priority);
        Pending pendingSignal = pending.get(key);
        pendingSignal.derived = true;
        pendingSignal.longDerived = compute;
        return cachedLong(key);
    }

    public DoubleSupplier deriveDouble(
            SignalKey<Double> key,
            SamplingPolicy policy,
            InputPriority priority,
            ToDoubleFunction<InputValues> compute) {
        requireConfiguring();
        Objects.requireNonNull(compute, "compute");
        require(key, policy, priority);
        Pending pendingSignal = pending.get(key);
        pendingSignal.derived = true;
        pendingSignal.doubleDerived = compute;
        return cachedDouble(key);
    }

    public BooleanSupplier deriveBoolean(
            SignalKey<Boolean> key,
            SamplingPolicy policy,
            InputPriority priority,
            Function<InputValues, Boolean> compute) {
        requireConfiguring();
        Objects.requireNonNull(compute, "compute");
        require(key, policy, priority);
        Pending pendingSignal = pending.get(key);
        pendingSignal.derived = true;
        pendingSignal.booleanDerived = compute;
        return cachedBoolean(key);
    }

    public void bindInt(SignalKey<Integer> key, IntSupplier physical) {
        requireConfiguring();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(physical, "physical");
        rejectCachedPhysical(physical);
        requireType(key, SignalValueType.INT);
        Pending row = row(key);
        if (row.intPhysical != null && row.intPhysical != physical) {
            throw new PulseException("Conflicting physical binding for " + key.qualifiedName());
        }
        row.intPhysical = physical;
    }

    public void bindLong(SignalKey<Long> key, LongSupplier physical) {
        requireConfiguring();
        Objects.requireNonNull(physical, "physical");
        rejectCachedPhysical(physical);
        requireType(key, SignalValueType.LONG);
        Pending row = row(key);
        if (row.longPhysical != null && row.longPhysical != physical) {
            throw new PulseException("Conflicting physical binding for " + key.qualifiedName());
        }
        row.longPhysical = physical;
    }

    public void bindDouble(SignalKey<Double> key, DoubleSupplier physical) {
        requireConfiguring();
        Objects.requireNonNull(physical, "physical");
        rejectCachedPhysical(physical);
        requireType(key, SignalValueType.DOUBLE);
        Pending row = row(key);
        if (row.doublePhysical != null && row.doublePhysical != physical) {
            throw new PulseException("Conflicting physical binding for " + key.qualifiedName());
        }
        row.doublePhysical = physical;
    }

    public void bindBoolean(SignalKey<Boolean> key, BooleanSupplier physical) {
        requireConfiguring();
        Objects.requireNonNull(physical, "physical");
        rejectCachedPhysical(physical);
        requireType(key, SignalValueType.BOOLEAN);
        Pending row = row(key);
        if (row.booleanPhysical != null && row.booleanPhysical != physical) {
            throw new PulseException("Conflicting physical binding for " + key.qualifiedName());
        }
        row.booleanPhysical = physical;
    }

    public <T> void bindObject(SignalKey<T> key, Supplier<T> physical) {
        requireConfiguring();
        Objects.requireNonNull(physical, "physical");
        rejectCachedPhysical(physical);
        requireType(key, SignalValueType.OBJECT);
        Pending row = row(key);
        if (row.objectPhysical != null && row.objectPhysical != physical) {
            throw new PulseException("Conflicting physical binding for " + key.qualifiedName());
        }
        row.objectPhysical = physical;
    }

    public void assignBus(SignalKey<?> key, ReadBus bus) {
        requireConfiguring();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(bus, "bus");
        Pending row = row(key);
        if (row.bus != null && row.bus != bus) {
            throw new PulseException("Conflicting read bus for " + key.qualifiedName() + ": " + row.bus + " vs " + bus);
        }
        row.bus = bus;
    }

    public IntSupplier cachedInt(SignalKey<Integer> key) {
        CachedIntReader reader = new CachedIntReader(key);
        attachCached(reader::attach, key, row -> row.intCached.add(reader));
        return reader;
    }

    public LongSupplier cachedLong(SignalKey<Long> key) {
        CachedLongReader reader = new CachedLongReader(key);
        attachCached(reader::attach, key, row -> row.longCached.add(reader));
        return reader;
    }

    public DoubleSupplier cachedDouble(SignalKey<Double> key) {
        CachedDoubleReader reader = new CachedDoubleReader(key);
        attachCached(reader::attach, key, row -> row.doubleCached.add(reader));
        return reader;
    }

    public BooleanSupplier cachedBoolean(SignalKey<Boolean> key) {
        CachedBooleanReader reader = new CachedBooleanReader(key);
        attachCached(reader::attach, key, row -> row.booleanCached.add(reader));
        return reader;
    }

    @SuppressWarnings("unchecked")
    public <T> Supplier<T> cachedObject(SignalKey<T> key) {
        CachedObjectReader reader = new CachedObjectReader(key);
        attachCached(reader::attach, key, row -> row.objectCached.add(reader));
        return (Supplier<T>) reader;
    }

    public void freeze() {
        requireConfiguring();
        compile();
        hubs.configure();
        lifecycle = PulseLifecycle.FROZEN;
    }

    public void start() {
        if (lifecycle == PulseLifecycle.CONFIGURING) {
            freeze();
        }
        if (lifecycle != PulseLifecycle.FROZEN && lifecycle != PulseLifecycle.RUNNING) {
            throw new PulseException("Cannot start from " + lifecycle);
        }
        lifecycle = PulseLifecycle.RUNNING;
    }

    public void stop() {
        lifecycle = PulseLifecycle.STOPPED;
    }

    public void capture(long cycleId, long nowNanos) {
        if (lifecycle == PulseLifecycle.FROZEN) {
            start();
        }
        if (lifecycle != PulseLifecycle.RUNNING) {
            throw new PulseException("capture() requires RUNNING, not " + lifecycle);
        }
        if (cycleId <= lastCycleId) {
            throw new PulseException("cycleId must increase: last=" + lastCycleId + " next=" + cycleId);
        }
        runCapture(cycleId, nowNanos);
    }

    private void runCapture(long cycleId, long nowNanos) {
        lastCycleId = cycleId;
        writeFrame = 1 - publishedFrame;
        timing.onCaptureStarted(cycleId, nowNanos);
        hubs.clearAll();
        for (int i = 0; i < slots.length; i++) {
            slots[i].copyFrame(publishedFrame, writeFrame);
            slots[i].lastDurationNanos = 0L;
        }
        for (int g = 0; g < groupDue.length; g++) {
            groupDue[g] = false;
            groupDurations[g] = 0L;
        }

        int scheduled = 0;
        int executed = 0;
        int deferred = 0;
        int failed = 0;
        oneShotScheduled = 0;
        oneShotExecuted = 0;
        oneShotDeferred = 0;
        lastOneShotKey = null;
        for (int i = 0; i < slots.length; i++) {
            due[i] = isDue(slots[i], cycleId, nowNanos);
            if (slots[i].groupIndex >= 0 && due[i]) {
                groupDue[slots[i].groupIndex] = true;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].groupIndex >= 0) {
                due[i] = groupDue[slots[i].groupIndex];
            }
        }
        applyOneShots(cycleId, nowNanos);
        for (int i = 0; i < slots.length; i++) {
            if (due[i]) {
                scheduled++;
            }
        }

        long deadline = settings.cycleBudgetNanos() > 0L ? nowNanos + settings.cycleBudgetNanos() : Long.MAX_VALUE;

        // Hub-bulk first (encoders + voltage share one MANUAL packet), then other Lynx, then I2C.
        // Required (CRITICAL/NORMAL) of a bus finish before OPTIONAL, so optional analog cannot delay IMU.
        for (int b = 0; b < BUS_ORDER.length; b++) {
            executed += runPhysical(due, cycleId, nowNanos, InputPriority.CRITICAL, Long.MAX_VALUE, BUS_ORDER[b]);
            executed += runPhysical(due, cycleId, nowNanos, InputPriority.NORMAL, Long.MAX_VALUE, BUS_ORDER[b]);
        }
        for (int b = 0; b < BUS_ORDER.length; b++) {
            executed += runPhysical(due, cycleId, nowNanos, InputPriority.OPTIONAL, deadline, BUS_ORDER[b]);
        }
        executed += runDerived(due, cycleId, nowNanos);

        for (int i = 0; i < slots.length; i++) {
            if (!due[i]) {
                markSkipped(slots[i], cycleId, nowNanos, false);
            }
            if (slots[i].deferredThisCycle) {
                deferred++;
            }
            if (slots[i].validity[writeFrame] == Validity.INVALID && due[i] && !slots[i].deferredThisCycle) {
                failed++;
            }
        }

        int stale = 0;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].validity[writeFrame] == Validity.STALE
                    || (slots[i].validity[writeFrame] == Validity.VALID
                            && age(slots[i], cycleId) >= failures.maxAgeCycles()
                            && !slots[i].updated[writeFrame])) {
                if (slots[i].validity[writeFrame] == Validity.VALID && !slots[i].updated[writeFrame]) {
                    slots[i].validity[writeFrame] = Validity.STALE;
                }
            }
            if (slots[i].validity[writeFrame] == Validity.STALE) {
                stale++;
            }
            metrics.setReaderDuration(i, slots[i].lastDurationNanos);
            if (slots[i].groupIndex >= 0) {
                groupDurations[slots[i].groupIndex] += slots[i].lastDurationNanos;
            }
            timing.onReaderFinished(
                    i, slots[i].lastDurationNanos, slots[i].validity[writeFrame] == Validity.INVALID && due[i]);
        }
        for (int g = 0; g < groupDurations.length; g++) {
            metrics.setGroupDuration(g, groupDurations[g]);
            timing.onGroupFinished(g, groupDurations[g]);
        }

        publishedFrame = writeFrame;
        publishedCycleId = cycleId;
        long end = clock.nowNanos();
        long duration = end - nowNanos;
        if (duration < 0L) {
            duration = 0L;
        }
        int phase = slots.length == 0 ? 0 : (int) Math.floorMod(cycleId, 64L);
        metrics.record(
                cycleId,
                phase,
                duration,
                scheduled,
                executed,
                deferred,
                failed,
                stale,
                oneShotScheduled,
                oneShotExecuted,
                oneShotDeferred,
                lastOneShotKey == null ? "" : lastOneShotKey.qualifiedName());
        timing.onCaptureFinished(cycleId, duration);
    }

    private void applyOneShots(long cycleId, long nowNanos) {
        int admittedOther = 0;
        int maxOther = settings.maxOnDemandOtherPerCapture();
        for (int i = 0; i < slots.length; i++) {
            oneShotThisCapture[i] = false;
            if (!oneShotQueued[i]) {
                continue;
            }
            oneShotScheduled++;
            boolean otherPhysical = !slots[i].derived && slots[i].bus == ReadBus.OTHER;
            if (otherPhysical && maxOther > 0 && admittedOther >= maxOther) {
                oneShotDeferred++;
                markSkipped(slots[i], cycleId, nowNanos, true);
                continue;
            }
            if (otherPhysical) {
                admittedOther++;
            }
            due[i] = true;
            oneShotQueued[i] = false;
            oneShotThisCapture[i] = true;
        }
    }

    private boolean onDemandOtherOverBudget(long captureStartNanos) {
        long budget = settings.onDemandOtherBudgetNanos();
        if (budget <= 0L) {
            return false;
        }
        return clock.nowNanos() - captureStartNanos >= budget;
    }

    private boolean isDue(SignalSlot slot, long cycleId, long nowNanos) {
        long lastCapture = -1L;
        Validity published = slot.validity[publishedFrame];
        if (slot.cycleId[publishedFrame] >= 0L && (published == Validity.VALID || published == Validity.STALE)) {
            lastCapture = slot.timestampNanos[publishedFrame];
        }
        return slot.schedule.isDue(cycleId, nowNanos, lastCapture);
    }

    private int runPhysical(
            boolean[] due, long cycleId, long nowNanos, InputPriority priority, long deadline, ReadBus bus) {
        int executed = 0;
        for (int i = 0; i < slots.length; i++) {
            if (!due[i] || slots[i].priority != priority || slots[i].derived || slots[i].bus != bus) {
                continue;
            }
            if (priority == InputPriority.OPTIONAL && clock.nowNanos() >= deadline) {
                markSkipped(slots[i], cycleId, nowNanos, true);
                continue;
            }
            if (oneShotThisCapture[i]
                    && slots[i].bus == ReadBus.OTHER
                    && slots[i].priority != InputPriority.CRITICAL
                    && onDemandOtherOverBudget(nowNanos)) {
                due[i] = false;
                oneShotQueued[i] = true;
                oneShotThisCapture[i] = false;
                oneShotDeferred++;
                markSkipped(slots[i], cycleId, nowNanos, true);
                continue;
            }
            execute(slots[i], cycleId, nowNanos);
            executed++;
            if (oneShotThisCapture[i]) {
                oneShotExecuted++;
                lastOneShotKey = slots[i].key;
            }
        }
        return executed;
    }

    private int runDerived(boolean[] due, long cycleId, long nowNanos) {
        int executed = 0;
        for (int i = 0; i < slots.length; i++) {
            if (!due[i] || !slots[i].derived) {
                continue;
            }
            execute(slots[i], cycleId, nowNanos);
            executed++;
        }
        return executed;
    }

    private void execute(SignalSlot slot, long cycleId, long nowNanos) {
        if (slot.disabled) {
            slot.validity[writeFrame] = Validity.INVALID;
            slot.updated[writeFrame] = false;
            slot.lastFault = READER_DISABLED;
            return;
        }
        long start = clock.nowNanos();
        try {
            if (slot.derived) {
                evaluateDerived(slot);
            } else {
                evaluatePhysical(slot);
            }
            slot.validity[writeFrame] = Validity.VALID;
            slot.timestampNanos[writeFrame] = start;
            slot.cycleId[writeFrame] = cycleId;
            slot.updated[writeFrame] = true;
            slot.consecutiveFailures = 0;
            slot.lastFault = null;
        } catch (RuntimeException ex) {
            handleFailure(slot, cycleId, start, ex);
        }
        slot.lastDurationNanos = clock.nowNanos() - start;
    }

    private void evaluatePhysical(SignalSlot slot) {
        switch (slot.type) {
            case INT:
                slot.intValue[writeFrame] = slot.intPhysical.getAsInt();
                break;
            case LONG:
                slot.longValue[writeFrame] = slot.longPhysical.getAsLong();
                break;
            case DOUBLE:
                slot.doubleValue[writeFrame] = slot.doublePhysical.getAsDouble();
                break;
            case BOOLEAN:
                slot.booleanValue[writeFrame] = slot.booleanPhysical.getAsBoolean();
                break;
            case OBJECT:
                slot.objectValue[writeFrame] = slot.objectPhysical.get();
                break;
            default:
                throw new PulseException("Unsupported type " + slot.type);
        }
    }

    private void evaluateDerived(SignalSlot slot) {
        switch (slot.type) {
            case INT:
                slot.intValue[writeFrame] = slot.intDerived.applyAsInt(writeView);
                break;
            case LONG:
                slot.longValue[writeFrame] = slot.longDerived.applyAsLong(writeView);
                break;
            case DOUBLE:
                slot.doubleValue[writeFrame] = slot.doubleDerived.applyAsDouble(writeView);
                break;
            case BOOLEAN:
                slot.booleanValue[writeFrame] = Boolean.TRUE.equals(slot.booleanDerived.apply(writeView));
                break;
            case OBJECT:
                slot.objectValue[writeFrame] = slot.objectDerived.apply(writeView);
                break;
            default:
                throw new PulseException("Unsupported type " + slot.type);
        }
    }

    private void handleFailure(SignalSlot slot, long cycleId, long timestampNanos, RuntimeException ex) {
        slot.consecutiveFailures++;
        slot.totalFailures++;
        slot.updated[writeFrame] = false;
        slot.validity[writeFrame] = Validity.INVALID;
        slot.timestampNanos[writeFrame] = timestampNanos;
        if (slot.lastFault == null) {
            slot.lastFault = Reason.of("read-failed", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        if (failures.maxConsecutiveFailures() > 0 && slot.consecutiveFailures >= failures.maxConsecutiveFailures()) {
            slot.disabled = true;
        }
        boolean fatal = slot.priority == InputPriority.CRITICAL
                ? failures.criticalFailuresFatal()
                : slot.priority == InputPriority.OPTIONAL
                        ? failures.optionalFailuresFatal()
                        : failures.criticalFailuresFatal();
        if (fatal) {
            throw new PulseException("Fatal sample failure for " + slot.key.qualifiedName(), ex);
        }
    }

    private void markSkipped(SignalSlot slot, long cycleId, long nowNanos, boolean deferred) {
        slot.updated[writeFrame] = false;
        if (deferred) {
            slot.deferrals++;
            slot.deferredThisCycle = true;
        }
        if (slot.validity[writeFrame] == Validity.VALID || slot.validity[writeFrame] == Validity.STALE) {
            long age = age(slot, cycleId);
            if (age >= failures.maxAgeCycles()) {
                slot.validity[writeFrame] = Validity.STALE;
            }
        }
    }

    private long age(SignalSlot slot, long cycleId) {
        long captured = slot.cycleId[writeFrame];
        if (captured < 0L) {
            return Long.MAX_VALUE;
        }
        long delta = cycleId - captured;
        return delta < 0L ? 0L : delta;
    }

    private void compile() {
        for (Pending row : pending.values()) {
            if (row.policy == null) {
                row.policy = SamplingPolicy.everyCycle();
            }
            if (row.priority == null) {
                row.priority = InputPriority.NORMAL;
            }
        }
        mergeGroups();
        List<Pending> rows = new ArrayList<>(pending.values());
        Collections.sort(rows, Comparator.comparing(p -> p.key));
        for (int i = 0; i < rows.size(); i++) {
            Pending row = rows.get(i);
            if (!row.derived && !hasBinding(row)) {
                throw new PulseException("Missing physical binding for " + row.key.qualifiedName());
            }
            if (row.derived && hasBinding(row)) {
                throw new PulseException("Derived signal also has a physical binding: " + row.key.qualifiedName());
            }
        }
        assignPhases(rows);
        Collections.sort(rows, (left, right) -> {
            if (left.derived != right.derived) {
                return left.derived ? 1 : -1;
            }
            int byBus = Integer.compare(busOf(left).rank(), busOf(right).rank());
            if (byBus != 0) {
                return byBus;
            }
            int byPriority = Integer.compare(rank(left.priority), rank(right.priority));
            if (byPriority != 0) {
                return byPriority;
            }
            return left.key.compareTo(right.key);
        });
        slots = new SignalSlot[rows.size()];
        due = new boolean[rows.size()];
        oneShotQueued = new boolean[rows.size()];
        oneShotThisCapture = new boolean[rows.size()];
        indexByKey.clear();
        Map<String, Integer> groupIndex = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String groupId = rows.get(i).groupId;
            if (groupId != null && !groupIndex.containsKey(groupId)) {
                groupIndex.put(groupId, groupIndex.size());
            }
        }
        groupIds = groupIndex.keySet().toArray(new String[0]);
        groupDue = new boolean[groupIds.length];
        groupDurations = new long[groupIds.length];
        ReadPlanEntry[] entries = new ReadPlanEntry[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Pending row = rows.get(i);
            SignalSlot slot = toSlot(row);
            if (row.groupId != null) {
                slot.groupIndex = groupIndex.get(row.groupId);
            }
            slots[i] = slot;
            indexByKey.put(row.key, i);
            entries[i] = new ReadPlanEntry(
                    row.key, row.policy, row.priority, row.groupId, row.policy.cost(), row.derived, busOf(row));
            attachCached(row, slot);
        }
        readPlan = new ReadPlan(entries);
        metrics.prepare(slots.length, groupIds, readPlan.describe(), hubs.hubCount());
    }

    private void mergeGroups() {
        Map<String, SamplingPolicy> policyByGroup = new LinkedHashMap<>();
        Map<String, InputPriority> priorityByGroup = new LinkedHashMap<>();
        Map<SignalKey<?>, String> groupByKey = new HashMap<>();
        for (int g = 0; g < groups.size(); g++) {
            CoherentGroup group = groups.get(g);
            SamplingPolicy policy = policyByGroup.get(group.id());
            if (policy == null) {
                policyByGroup.put(group.id(), group.policy());
                priorityByGroup.put(group.id(), group.priority());
            } else {
                policyByGroup.put(group.id(), policy.union(group.policy()));
                priorityByGroup.put(group.id(), stronger(priorityByGroup.get(group.id()), group.priority()));
            }
            for (SignalKey<?> member : group.members()) {
                String existing = groupByKey.get(member);
                if (existing != null && !existing.equals(group.id())) {
                    SamplingPolicy merged = policyByGroup.get(existing).union(policyByGroup.get(group.id()));
                    policyByGroup.put(existing, merged);
                    policyByGroup.put(group.id(), merged);
                    InputPriority mergedPriority =
                            stronger(priorityByGroup.get(existing), priorityByGroup.get(group.id()));
                    priorityByGroup.put(existing, mergedPriority);
                    priorityByGroup.put(group.id(), mergedPriority);
                }
                groupByKey.put(member, existing == null ? group.id() : existing);
            }
        }
        for (Map.Entry<SignalKey<?>, String> entry : groupByKey.entrySet()) {
            Pending row = pending.get(entry.getKey());
            if (row == null) {
                continue;
            }
            String groupId = entry.getValue();
            row.groupId = groupId;
            row.policy = row.policy.union(policyByGroup.get(groupId));
            row.priority = stronger(row.priority, priorityByGroup.get(groupId));
        }
        Map<String, SamplingPolicy> memberUnion = new LinkedHashMap<>();
        for (Pending row : pending.values()) {
            if (row.groupId == null) {
                continue;
            }
            SamplingPolicy current = memberUnion.get(row.groupId);
            memberUnion.put(row.groupId, current == null ? row.policy : current.union(row.policy));
        }
        for (Pending row : pending.values()) {
            if (row.groupId != null) {
                row.policy = memberUnion.get(row.groupId);
            }
        }
    }

    private void assignPhases(List<Pending> rows) {
        Map<Integer, int[]> load = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Pending row = rows.get(i);
            if (row.policy.isAutoPhase()) {
                continue;
            }
            addLoad(load, row.policy, row.policy.cost());
        }
        List<Pending> flexible = new ArrayList<>();
        Map<String, Pending> groupRepresentative = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Pending row = rows.get(i);
            if (!row.policy.isAutoPhase()) {
                continue;
            }
            if (row.groupId != null) {
                if (!groupRepresentative.containsKey(row.groupId)) {
                    groupRepresentative.put(row.groupId, row);
                    flexible.add(row);
                }
            } else {
                flexible.add(row);
            }
        }
        Collections.sort(flexible, Comparator.comparing(p -> p.groupId != null ? p.groupId : p.key.qualifiedName()));
        Map<String, SamplingPolicy> assignedGroup = new HashMap<>();
        for (int i = 0; i < flexible.size(); i++) {
            Pending row = flexible.get(i);
            int period = row.policy.spreadPeriod();
            int cost = row.policy.cost();
            if (row.groupId != null) {
                cost = 0;
                for (int r = 0; r < rows.size(); r++) {
                    if (row.groupId.equals(rows.get(r).groupId)) {
                        cost += rows.get(r).policy.cost();
                    }
                }
            }
            int phase = choosePhase(load, period, cost);
            SamplingPolicy assigned = row.policy.assignPhase(phase);
            if (row.groupId != null) {
                assignedGroup.put(row.groupId, assigned);
            } else {
                row.policy = assigned;
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            Pending row = rows.get(i);
            if (row.groupId != null && assignedGroup.containsKey(row.groupId)) {
                row.policy = assignedGroup.get(row.groupId);
            }
        }
    }

    private static void addLoad(Map<Integer, int[]> load, SamplingPolicy policy, int cost) {
        for (int i = 0; i < policy.predicateCount(); i++) {
            int period = policy.periodAt(i);
            int phase = policy.phaseAt(i);
            int[] bucket = load.get(period);
            if (bucket == null) {
                bucket = new int[period];
                load.put(period, bucket);
            }
            bucket[phase] += cost;
        }
    }

    private static int choosePhase(Map<Integer, int[]> load, int period, int cost) {
        int[] bucket = load.get(period);
        if (bucket == null) {
            bucket = new int[period];
            load.put(period, bucket);
        }
        int best = 0;
        for (int phase = 1; phase < period; phase++) {
            if (bucket[phase] < bucket[best]) {
                best = phase;
            }
        }
        bucket[best] += cost;
        return best;
    }

    private SignalSlot toSlot(Pending row) {
        SignalSlot slot = new SignalSlot();
        slot.key = row.key;
        slot.type = row.key.valueType();
        slot.priority = row.priority;
        slot.schedule = row.policy;
        slot.groupId = row.groupId;
        slot.cost = row.policy.cost();
        slot.derived = row.derived;
        slot.bus = busOf(row);
        slot.intPhysical = row.intPhysical;
        slot.longPhysical = row.longPhysical;
        slot.doublePhysical = row.doublePhysical;
        slot.booleanPhysical = row.booleanPhysical;
        slot.objectPhysical = row.objectPhysical;
        slot.intDerived = row.intDerived;
        slot.longDerived = row.longDerived;
        slot.doubleDerived = row.doubleDerived;
        slot.booleanDerived = row.booleanDerived;
        slot.objectDerived = row.objectDerived;
        return slot;
    }

    private void attachCached(Pending row, SignalSlot slot) {
        int index = indexOf(row.key);
        for (int i = 0; i < row.intCached.size(); i++) {
            row.intCached.get(i).attach(index);
            slot.cachedReader = row.intCached.get(i);
        }
        for (int i = 0; i < row.longCached.size(); i++) {
            row.longCached.get(i).attach(index);
            slot.cachedReader = row.longCached.get(i);
        }
        for (int i = 0; i < row.doubleCached.size(); i++) {
            row.doubleCached.get(i).attach(index);
            slot.cachedReader = row.doubleCached.get(i);
        }
        for (int i = 0; i < row.booleanCached.size(); i++) {
            row.booleanCached.get(i).attach(index);
            slot.cachedReader = row.booleanCached.get(i);
        }
        for (int i = 0; i < row.objectCached.size(); i++) {
            row.objectCached.get(i).attach(index);
            slot.cachedReader = row.objectCached.get(i);
        }
    }

    private int indexOf(SignalKey<?> key) {
        Integer index = indexByKey.get(key);
        if (index == null) {
            throw new PulseException("Unknown signal " + key.qualifiedName());
        }
        return index;
    }

    private void attachCached(IntConsumer attach, SignalKey<?> key, Consumer<Pending> queue) {
        if (lifecycle == PulseLifecycle.CONFIGURING) {
            queue.accept(row(key));
            return;
        }
        attach.accept(indexOf(key));
    }

    private static boolean hasBinding(Pending row) {
        return row.intPhysical != null
                || row.longPhysical != null
                || row.doublePhysical != null
                || row.booleanPhysical != null
                || row.objectPhysical != null;
    }

    private Pending row(SignalKey<?> key) {
        Pending existing = pending.get(key);
        if (existing == null) {
            existing = new Pending(key);
            pending.put(key, existing);
        }
        return existing;
    }

    private void requireType(SignalKey<?> key, SignalValueType type) {
        if (key.valueType() != type) {
            throw new PulseException("Type mismatch for " + key.qualifiedName() + ": expected " + type);
        }
    }

    private void rejectCachedPhysical(Object physical) {
        if (physical instanceof PulseCachedReader) {
            throw new PulseException("Cannot bind a PULSE cached reader as a physical source");
        }
    }

    private void requireConfiguring() {
        if (lifecycle != PulseLifecycle.CONFIGURING) {
            throw new PulseException("Registration is only allowed during CONFIGURING, not " + lifecycle);
        }
    }

    private static InputPriority stronger(InputPriority left, InputPriority right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return rank(left) <= rank(right) ? left : right;
    }

    private static ReadBus busOf(Pending row) {
        return row.bus == null ? ReadBus.OTHER : row.bus;
    }

    private static int rank(InputPriority priority) {
        if (priority == null || priority == InputPriority.OPTIONAL) {
            return 2;
        }
        if (priority == InputPriority.CRITICAL) {
            return 0;
        }
        return 1;
    }

    @Override
    public long currentCycleId() {
        return publishedCycleId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Sample<T> get(SignalKey<T> key) {
        SignalSlot slot = slot(key);
        int frame = publishedFrame;
        T value;
        switch (slot.type) {
            case INT:
                value = (T) Integer.valueOf(slot.intValue[frame]);
                break;
            case LONG:
                value = (T) Long.valueOf(slot.longValue[frame]);
                break;
            case DOUBLE:
                value = (T) Double.valueOf(slot.doubleValue[frame]);
                break;
            case BOOLEAN:
                value = (T) Boolean.valueOf(slot.booleanValue[frame]);
                break;
            default:
                value = (T) slot.objectValue[frame];
                break;
        }
        return Sample.of(
                value,
                slot.validity[frame],
                slot.timestampNanos[frame],
                slot.cycleId[frame],
                slot.updated[frame] && publishedCycleId == slot.cycleId[frame],
                slot.lastFault);
    }

    @Override
    public int getInt(SignalKey<Integer> key) {
        SignalSlot slot = slot(key);
        return slot.intValue[publishedFrame];
    }

    @Override
    public long getLong(SignalKey<Long> key) {
        return slot(key).longValue[publishedFrame];
    }

    @Override
    public double getDouble(SignalKey<Double> key) {
        return slot(key).doubleValue[publishedFrame];
    }

    @Override
    public boolean getBoolean(SignalKey<Boolean> key) {
        return slot(key).booleanValue[publishedFrame];
    }

    @Override
    public Validity validity(SignalKey<?> key) {
        return slot(key).validity[publishedFrame];
    }

    @Override
    public boolean isFresh(SignalKey<?> key) {
        return isFresh(slot(key), publishedFrame, true);
    }

    @Override
    public long captureTimestampNanos(SignalKey<?> key) {
        return slot(key).timestampNanos[publishedFrame];
    }

    @Override
    public long captureCycleId(SignalKey<?> key) {
        return slot(key).cycleId[publishedFrame];
    }

    @Override
    public boolean contains(SignalKey<?> key) {
        Objects.requireNonNull(key, "key");
        return indexByKey.containsKey(key);
    }

    @Override
    public <T> Sample<T> tryGet(SignalKey<T> key) {
        Objects.requireNonNull(key, "key");
        if (!contains(key)) {
            return Sample.missing();
        }
        return get(key);
    }

    @Override
    public double tryGetDouble(SignalKey<Double> key) {
        Objects.requireNonNull(key, "key");
        Integer index = indexByKey.get(key);
        if (index == null) {
            return Double.NaN;
        }
        return slots[index].doubleValue[publishedFrame];
    }

    @Override
    public boolean tryGetBoolean(SignalKey<Boolean> key) {
        Objects.requireNonNull(key, "key");
        Integer index = indexByKey.get(key);
        if (index == null) {
            return false;
        }
        return slots[index].booleanValue[publishedFrame];
    }

    private boolean isFresh(SignalSlot slot, int frame, boolean published) {
        if (slot.validity[frame] != Validity.VALID || !slot.updated[frame]) {
            return false;
        }
        if (!published) {
            return true;
        }
        return publishedCycleId == slot.cycleId[frame];
    }

    private SignalSlot slot(SignalKey<?> key) {
        Integer index = indexByKey.get(key);
        if (index == null) {
            Pending row = pending.get(key);
            if (row == null) {
                throw new PulseException("Unknown signal " + key.qualifiedName());
            }
            if (lifecycle == PulseLifecycle.CONFIGURING) {
                throw new PulseException("Signal " + key.qualifiedName() + " has not been captured yet");
            }
            throw new PulseException("Unknown signal " + key.qualifiedName());
        }
        return slots[index];
    }

    int publishedInt(int index) {
        return slots[index].intValue[publishedFrame];
    }

    long publishedLong(int index) {
        return slots[index].longValue[publishedFrame];
    }

    double publishedDouble(int index) {
        return slots[index].doubleValue[publishedFrame];
    }

    boolean publishedBoolean(int index) {
        return slots[index].booleanValue[publishedFrame];
    }

    Object publishedObject(int index) {
        return slots[index].objectValue[publishedFrame];
    }

    private static final class Pending {
        final SignalKey<?> key;
        SamplingPolicy policy;
        InputPriority priority;
        String groupId;
        boolean derived;
        ReadBus bus;
        IntSupplier intPhysical;
        LongSupplier longPhysical;
        DoubleSupplier doublePhysical;
        BooleanSupplier booleanPhysical;
        Supplier<?> objectPhysical;
        ToIntFunction<InputValues> intDerived;
        ToLongFunction<InputValues> longDerived;
        ToDoubleFunction<InputValues> doubleDerived;
        Function<InputValues, Boolean> booleanDerived;
        Function<InputValues, ?> objectDerived;
        final List<CachedIntReader> intCached = new ArrayList<>();
        final List<CachedLongReader> longCached = new ArrayList<>();
        final List<CachedDoubleReader> doubleCached = new ArrayList<>();
        final List<CachedBooleanReader> booleanCached = new ArrayList<>();
        final List<CachedObjectReader> objectCached = new ArrayList<>();

        Pending(SignalKey<?> key) {
            this.key = key;
        }
    }

    private final class FrameView implements InputValues {
        private final boolean published;

        FrameView(boolean published) {
            this.published = published;
        }

        private int frame() {
            return published ? publishedFrame : writeFrame;
        }

        @Override
        public long currentCycleId() {
            return published ? publishedCycleId : lastCycleId;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Sample<T> get(SignalKey<T> key) {
            SignalSlot slot = slot(key);
            int frame = frame();
            T value;
            switch (slot.type) {
                case INT:
                    value = (T) Integer.valueOf(slot.intValue[frame]);
                    break;
                case LONG:
                    value = (T) Long.valueOf(slot.longValue[frame]);
                    break;
                case DOUBLE:
                    value = (T) Double.valueOf(slot.doubleValue[frame]);
                    break;
                case BOOLEAN:
                    value = (T) Boolean.valueOf(slot.booleanValue[frame]);
                    break;
                default:
                    value = (T) slot.objectValue[frame];
                    break;
            }
            return Sample.of(
                    value,
                    slot.validity[frame],
                    slot.timestampNanos[frame],
                    slot.cycleId[frame],
                    slot.updated[frame],
                    slot.lastFault);
        }

        @Override
        public int getInt(SignalKey<Integer> key) {
            return slot(key).intValue[frame()];
        }

        @Override
        public long getLong(SignalKey<Long> key) {
            return slot(key).longValue[frame()];
        }

        @Override
        public double getDouble(SignalKey<Double> key) {
            return slot(key).doubleValue[frame()];
        }

        @Override
        public boolean getBoolean(SignalKey<Boolean> key) {
            return slot(key).booleanValue[frame()];
        }

        @Override
        public Validity validity(SignalKey<?> key) {
            return slot(key).validity[frame()];
        }

        @Override
        public boolean isFresh(SignalKey<?> key) {
            return PulseRuntime.this.isFresh(slot(key), frame(), published);
        }

        @Override
        public long captureTimestampNanos(SignalKey<?> key) {
            return slot(key).timestampNanos[frame()];
        }

        @Override
        public long captureCycleId(SignalKey<?> key) {
            return slot(key).cycleId[frame()];
        }

        @Override
        public boolean contains(SignalKey<?> key) {
            return PulseRuntime.this.contains(key);
        }

        @Override
        public <T> Sample<T> tryGet(SignalKey<T> key) {
            Objects.requireNonNull(key, "key");
            if (!contains(key)) {
                return Sample.missing();
            }
            return get(key);
        }

        @Override
        public double tryGetDouble(SignalKey<Double> key) {
            Objects.requireNonNull(key, "key");
            Integer index = indexByKey.get(key);
            if (index == null) {
                return Double.NaN;
            }
            return slots[index].doubleValue[frame()];
        }

        @Override
        public boolean tryGetBoolean(SignalKey<Boolean> key) {
            Objects.requireNonNull(key, "key");
            Integer index = indexByKey.get(key);
            if (index == null) {
                return false;
            }
            return slots[index].booleanValue[frame()];
        }
    }

    final class CachedIntReader implements IntSupplier, PulseCachedReader {
        private final SignalKey<Integer> key;
        private int index = -1;

        CachedIntReader(SignalKey<Integer> key) {
            this.key = key;
        }

        void attach(int index) {
            this.index = index;
        }

        @Override
        public int getAsInt() {
            if (index < 0) {
                return 0;
            }
            return slots[index].intValue[publishedFrame];
        }
    }

    final class CachedLongReader implements LongSupplier, PulseCachedReader {
        private int index = -1;

        CachedLongReader(SignalKey<Long> key) {}

        void attach(int index) {
            this.index = index;
        }

        @Override
        public long getAsLong() {
            if (index < 0) {
                return 0L;
            }
            return slots[index].longValue[publishedFrame];
        }
    }

    final class CachedDoubleReader implements DoubleSupplier, PulseCachedReader {
        private int index = -1;

        CachedDoubleReader(SignalKey<Double> key) {}

        void attach(int index) {
            this.index = index;
        }

        @Override
        public double getAsDouble() {
            if (index < 0) {
                return 0.0d;
            }
            return slots[index].doubleValue[publishedFrame];
        }
    }

    final class CachedBooleanReader implements BooleanSupplier, PulseCachedReader {
        private int index = -1;

        CachedBooleanReader(SignalKey<Boolean> key) {}

        void attach(int index) {
            this.index = index;
        }

        @Override
        public boolean getAsBoolean() {
            if (index < 0) {
                return false;
            }
            return slots[index].booleanValue[publishedFrame];
        }
    }

    final class CachedObjectReader implements Supplier<Object>, PulseCachedReader {
        private int index = -1;

        CachedObjectReader(SignalKey<?> key) {}

        void attach(int index) {
            this.index = index;
        }

        @Override
        public Object get() {
            if (index < 0) {
                return null;
            }
            return slots[index].objectValue[publishedFrame];
        }
    }
}

package org.allsparks.pulse.internal;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.InputValues;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;
import org.allsparks.contracts.input.SignalValueType;
import org.allsparks.contracts.observation.Validity;
import org.allsparks.contracts.status.Reason;
import org.allsparks.pulse.PulseCachedReader;
import org.allsparks.pulse.ReadBus;

/** Per-signal compiled slot with a double-buffered sample. */
public final class SignalSlot {
    public SignalKey<?> key;
    public SignalValueType type;
    public InputPriority priority;
    public SamplingPolicy schedule;
    public String groupId;
    public int cost = 1;
    public boolean derived;
    public ReadBus bus = ReadBus.OTHER;

    public IntSupplier intPhysical;
    public LongSupplier longPhysical;
    public DoubleSupplier doublePhysical;
    public BooleanSupplier booleanPhysical;
    public Supplier<?> objectPhysical;

    public ToIntFunction<InputValues> intDerived;
    public ToLongFunction<InputValues> longDerived;
    public ToDoubleFunction<InputValues> doubleDerived;
    public java.util.function.Function<InputValues, Boolean> booleanDerived;
    public java.util.function.Function<InputValues, ?> objectDerived;

    public final int[] intValue = new int[2];
    public final long[] longValue = new long[2];
    public final double[] doubleValue = new double[2];
    public final boolean[] booleanValue = new boolean[2];
    public final Object[] objectValue = new Object[2];
    public final Validity[] validity = new Validity[] {Validity.MISSING, Validity.MISSING};
    public final long[] timestampNanos = new long[2];
    public final long[] cycleId = new long[] {-1L, -1L};
    public final boolean[] updated = new boolean[2];
    public Reason lastFault;
    public int consecutiveFailures;
    public int totalFailures;
    public int deferrals;
    public boolean disabled;
    public boolean deferredThisCycle;
    public long lastDurationNanos;
    public int groupIndex = -1;

    public PulseCachedReader cachedReader;

    public void copyFrame(int from, int to) {
        intValue[to] = intValue[from];
        longValue[to] = longValue[from];
        doubleValue[to] = doubleValue[from];
        booleanValue[to] = booleanValue[from];
        objectValue[to] = objectValue[from];
        validity[to] = validity[from];
        timestampNanos[to] = timestampNanos[from];
        cycleId[to] = cycleId[from];
        updated[to] = false;
        deferredThisCycle = false;
    }
}

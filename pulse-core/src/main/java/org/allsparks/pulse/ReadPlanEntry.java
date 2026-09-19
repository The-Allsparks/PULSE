package org.allsparks.pulse;

import java.util.Objects;
import org.allsparks.contracts.input.InputPriority;
import org.allsparks.contracts.input.SamplingPolicy;
import org.allsparks.contracts.input.SignalKey;

/**
 * One compiled reader in a {@link ReadPlan}. TRACE and diagnostics may print
 * this without depending on capture internals.
 */
public final class ReadPlanEntry {

    private final SignalKey<?> key;
    private final SamplingPolicy schedule;
    private final InputPriority priority;
    private final String groupId;
    private final int cost;
    private final boolean derived;
    private final ReadBus bus;

    public ReadPlanEntry(
            SignalKey<?> key,
            SamplingPolicy schedule,
            InputPriority priority,
            String groupId,
            int cost,
            boolean derived) {
        this(key, schedule, priority, groupId, cost, derived, ReadBus.OTHER);
    }

    public ReadPlanEntry(
            SignalKey<?> key,
            SamplingPolicy schedule,
            InputPriority priority,
            String groupId,
            int cost,
            boolean derived,
            ReadBus bus) {
        this.key = Objects.requireNonNull(key, "key");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.groupId = groupId;
        this.cost = cost;
        this.derived = derived;
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    public SignalKey<?> key() {
        return key;
    }

    public SamplingPolicy schedule() {
        return schedule;
    }

    public InputPriority priority() {
        return priority;
    }

    public String groupId() {
        return groupId;
    }

    public int cost() {
        return cost;
    }

    public boolean derived() {
        return derived;
    }

    public ReadBus bus() {
        return bus;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        text.append(key.qualifiedName());
        text.append(' ').append(schedule);
        text.append(' ').append(priority);
        if (groupId != null) {
            text.append(" group=").append(groupId);
        }
        if (bus != ReadBus.OTHER) {
            text.append(" bus=").append(bus);
        }
        if (derived) {
            text.append(" derived");
        }
        text.append(" cost=").append(cost);
        return text.toString();
    }
}

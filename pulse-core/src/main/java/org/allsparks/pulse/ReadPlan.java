package org.allsparks.pulse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Compiled, inspectable capture plan. Built once at freeze. Deterministic for
 * the same set of keys, policies, and groups regardless of registration order.
 */
public final class ReadPlan {

    private final ReadPlanEntry[] entries;
    private final String description;

    public ReadPlan(ReadPlanEntry[] entries) {
        this.entries = Objects.requireNonNull(entries, "entries").clone();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < this.entries.length; i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(this.entries[i]);
        }
        this.description = text.toString();
    }

    public List<ReadPlanEntry> entries() {
        return Collections.unmodifiableList(Arrays.asList(entries));
    }

    public int size() {
        return entries.length;
    }

    public ReadPlanEntry entryAt(int index) {
        return entries[index];
    }

    /**
     * Human-readable plan. Safe to call after freeze. Do not call from inside
     * {@link Pulse#capture(long, long)}.
     */
    public String describe() {
        return description;
    }
}

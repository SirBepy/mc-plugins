package dev.sirbepy.mc.endfight.reset;

import java.util.List;

/** Walks a fixed {@link ChangedBlock} list a budgeted slice at a time - one call per tick from
 * {@link IslandRestorer}, but just a cursor over a list here so the budgeting itself is testable
 * with no scheduler or world at all. */
public final class RestoreRun {

    private final List<ChangedBlock> changes;
    private int cursor;

    public RestoreRun(List<ChangedBlock> changes) {
        this.changes = changes;
    }

    public int total() {
        return changes.size();
    }

    public int applied() {
        return cursor;
    }

    public int remaining() {
        return changes.size() - cursor;
    }

    public boolean isDone() {
        return cursor >= changes.size();
    }

    /** Returns up to {@code budget} not-yet-applied blocks and advances past them. */
    public List<ChangedBlock> takeNext(int budget) {
        if (budget <= 0 || isDone()) {
            return List.of();
        }
        int end = Math.min(changes.size(), cursor + budget);
        List<ChangedBlock> batch = changes.subList(cursor, end);
        cursor = end;
        return batch;
    }
}

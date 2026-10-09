package dev.sirbepy.mc.endfight.reset;

import java.util.ArrayList;
import java.util.List;

/** Pure diff: which blocks in {@code snapshot}'s box don't already match it. Never includes a
 * block that's already correct, so a restore right after a fresh build (nothing changed) touches
 * nothing. */
public final class RestorePlan {

    private RestorePlan() {
    }

    public static List<ChangedBlock> diff(IslandSnapshot snapshot, BlockDataLookup current) {
        List<ChangedBlock> changes = new ArrayList<>();
        snapshot.forEach((x, y, z, target) -> {
            String currentData = current.blockDataAt(x, y, z);
            if (!target.equals(currentData)) {
                changes.add(new ChangedBlock(x, y, z, target));
            }
        });
        return changes;
    }
}

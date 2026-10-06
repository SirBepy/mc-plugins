package dev.sirbepy.mc.dragonballs.config;

import java.util.List;

/** Pure lookup, no server needed: which {@link DifficultyTier} applies at a given wish count. */
public final class DifficultySchedule {

    private DifficultySchedule() {
    }

    /** Clamps {@code wishCount} into {@code [0, tiers.size() - 1]}, so a count past the configured
     * list just keeps using the hardest (last) tier instead of throwing. */
    public static DifficultyTier tierFor(List<DifficultyTier> tiers, long wishCount) {
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("difficulty schedule is empty");
        }
        int index = (int) Math.max(0, Math.min(wishCount, tiers.size() - 1));
        return tiers.get(index);
    }
}

package dev.sirbepy.mc.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One market period's Player of the Day: the target (null fields when nobody qualified, e.g. an
 * empty server with nobody ever having joined, or a cooldown period resting after a sale) and,
 * once sold, who claimed it. {@code postedAt} is when this target was picked: the fresh/old-head
 * cutoff a sale compares a head's kill stamp against (Joe, 2026-10-09).
 */
record WantedState(String period, boolean cooldown, UUID targetId, String targetName, BigDecimal price,
                    Instant postedAt, UUID claimedBy, String claimedByName) {

    static WantedState none(String period) {
        return new WantedState(period, false, null, null, null, null, null, null);
    }

    /** The period right after a sale: no bounty, resting (Joe, 2026-10-09), so a claim can't be
     * farmed for a guaranteed second payout the very next period. */
    static WantedState cooldown(String period) {
        return new WantedState(period, true, null, null, null, null, null, null);
    }

    boolean hasTarget() {
        return targetId != null;
    }

    boolean claimed() {
        return claimedBy != null;
    }

    WantedState claim(UUID by, String byName) {
        return new WantedState(period, cooldown, targetId, targetName, price, postedAt, by, byName);
    }
}

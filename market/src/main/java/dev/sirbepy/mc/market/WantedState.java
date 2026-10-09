package dev.sirbepy.mc.market;

import java.time.Instant;
import java.util.UUID;

/**
 * One market period's Player of the Day: the target (null fields when nobody qualified, e.g. an
 * empty server with nobody ever having joined, or a cooldown period resting after a sale) and,
 * once sold, who claimed it. {@code postedAt} is when this target was picked: the fresh/old-head
 * cutoff a sale compares a head's kill stamp against (Joe, 2026-10-09).
 *
 * <p>The bounty's dollar value isn't stored: it follows the target's time alive (WantedBoard), so
 * only the random {@code factor} rolled at posting is. {@code played} says the target was online at
 * some point since posting and {@code diedStreakTicks} is the time alive their latest death since
 * posting ended (null: no death yet); together they decide the survivor reward.
 */
record WantedState(String period, boolean cooldown, UUID targetId, String targetName, double factor,
                    Instant postedAt, boolean played, Long diedStreakTicks, UUID claimedBy, String claimedByName) {

    static WantedState none(String period) {
        return new WantedState(period, false, null, null, 0, null, false, null, null, null);
    }

    /** The period right after a sale: no bounty, resting (Joe, 2026-10-09), so a claim can't be
     * farmed for a guaranteed second payout the very next period. */
    static WantedState cooldown(String period) {
        return new WantedState(period, true, null, null, 0, null, false, null, null, null);
    }

    static WantedState posted(String period, UUID targetId, String targetName, double factor, Instant postedAt, boolean played) {
        return new WantedState(period, false, targetId, targetName, factor, postedAt, played, null, null, null);
    }

    boolean hasTarget() {
        return targetId != null;
    }

    boolean claimed() {
        return claimedBy != null;
    }

    boolean died() {
        return diedStreakTicks != null;
    }

    WantedState claim(UUID by, String byName) {
        return new WantedState(period, cooldown, targetId, targetName, factor, postedAt, played, diedStreakTicks, by, byName);
    }

    WantedState withPlayed() {
        return new WantedState(period, cooldown, targetId, targetName, factor, postedAt, true, diedStreakTicks, claimedBy, claimedByName);
    }

    WantedState withDeath(long streakTicks) {
        return new WantedState(period, cooldown, targetId, targetName, factor, postedAt, played, streakTicks, claimedBy, claimedByName);
    }
}

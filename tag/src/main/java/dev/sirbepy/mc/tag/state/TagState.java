package dev.sirbepy.mc.tag.state;

import dev.sirbepy.mc.tag.logic.PendingAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Tag needs to survive a restart. Never trust player PDC as the source of truth here:
 * offline PDC is read-only, so all of this lives in {@code state.yml} instead.
 */
public final class TagState {

    public UUID currentIt;
    public UUID lastTagger;
    public long tagbackExpiryMillis;
    public final Map<UUID, PlayerStats> stats = new HashMap<>();
    public final Set<UUID> optedOut = new HashSet<>();
    public final Map<UUID, PendingAction> pending = new HashMap<>();
    /** Null until an admin runs `/tag leaderboard place`. */
    public LeaderboardLocation leaderboardLocation;

    public PlayerStats statsFor(UUID player) {
        return stats.computeIfAbsent(player, ignored -> new PlayerStats());
    }

    /** Where the in-world leaderboard TextDisplay respawns after a restart. */
    public static final class LeaderboardLocation {
        public final String world;
        public final double x;
        public final double y;
        public final double z;

        public LeaderboardLocation(String world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LeaderboardLocation other)) return false;
            return world.equals(other.world)
                    && Double.compare(x, other.x) == 0
                    && Double.compare(y, other.y) == 0
                    && Double.compare(z, other.z) == 0;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(world, x, y, z);
        }
    }
}

package dev.sirbepy.mc.endfight.fight;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fight honours (10ca's spec, Joe decided 2026-10-09), decided once when the dragon dies:
 * "Dragon's Bane" for the most damage to the dragon, and one honour per {@link HonourGroup} for
 * the most kills of it. Only in a fight with 2+ fighters; a group honour needs 3+ kills; ties go to
 * whoever got there first. An honour raises its holder's armor-roll chance for this kill's hoard:
 * Bane +50%, each group honour +25%.
 */
public record FightHonours(UUID bane, double baneDamage, Map<HonourGroup, UUID> groups,
                           Map<HonourGroup, Integer> groupCounts) {

    public static final int MIN_FIGHTERS = 2;
    public static final int MIN_GROUP_KILLS = 3;
    public static final double BANE_BONUS = 0.50;
    public static final double GROUP_BONUS = 0.25;

    public static final FightHonours NONE = new FightHonours(null, 0, Map.of(), Map.of());

    /**
     * @param fighters     this kill's participants
     * @param topDamagers  players by damage dealt, most first (ties in first-hit order)
     * @param damage       damage dealt per player
     * @param groupKills   kills per group per player
     * @param groupLeaders each group's leader, ties already resolved to whoever got there first
     */
    public static FightHonours decide(Collection<UUID> fighters, List<UUID> topDamagers, Map<UUID, Double> damage,
                                      Map<HonourGroup, Map<UUID, Integer>> groupKills,
                                      Map<HonourGroup, UUID> groupLeaders) {
        if (fighters.size() < MIN_FIGHTERS) {
            return NONE;
        }
        UUID bane = topDamagers.stream().filter(fighters::contains).findFirst().orElse(null);
        Map<HonourGroup, UUID> groups = new LinkedHashMap<>();
        Map<HonourGroup, Integer> counts = new LinkedHashMap<>();
        for (HonourGroup group : HonourGroup.values()) {
            UUID leader = groupLeaders.get(group);
            int count = leader == null ? 0 : groupKills.getOrDefault(group, Map.of()).getOrDefault(leader, 0);
            if (leader != null && count >= MIN_GROUP_KILLS) {
                groups.put(group, leader);
                counts.put(group, count);
            }
        }
        return new FightHonours(bane, bane == null ? 0 : damage.getOrDefault(bane, 0.0), groups, counts);
    }

    /** How much this player's armor-roll chance goes up for this kill. */
    public double rollBonus(UUID player) {
        double bonus = player.equals(bane) ? BANE_BONUS : 0;
        for (UUID holder : groups.values()) {
            if (holder.equals(player)) {
                bonus += GROUP_BONUS;
            }
        }
        return bonus;
    }

    public boolean isEmpty() {
        return bane == null && groups.isEmpty();
    }
}

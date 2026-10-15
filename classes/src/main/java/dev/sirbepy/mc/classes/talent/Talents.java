package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Talent points and ranks. A class has one point per level after the first (24 at level 25), spent
 * in its own tree; ranks are kept per class like class XP. Tier gates count the points a branch has
 * in lower tiers, so a refund that would strand a higher node is refused. Respec is free.
 */
public final class Talents {

    public enum Result { OK, NO_POINTS, MAXED, TIER_LOCKED, LEVEL_LOCKED, OTHER_KEYSTONE, NOT_LEARNED, STRANDS_HIGHER }

    private final Classes classes;
    private final Map<RpgClass, Map<String, Talent>> byClass = new EnumMap<>(RpgClass.class);

    public Talents(Classes classes) {
        this.classes = classes;
    }

    /** Called once per kit at enable; ids must be unique within the class. */
    public void register(RpgClass c, List<Talent> talents) {
        Map<String, Talent> map = new LinkedHashMap<>();
        for (Talent t : talents) {
            if (map.put(t.id(), t) != null) throw new IllegalArgumentException(c + " has two talents called " + t.id());
        }
        byClass.put(c, map);
    }

    public List<Talent> all(RpgClass c) {
        return List.copyOf(byClass.getOrDefault(c, Map.of()).values());
    }

    public Talent get(RpgClass c, String id) {
        return byClass.getOrDefault(c, Map.of()).get(id);
    }

    public int level(UUID player, RpgClass c) {
        return classes.profile(player).level(c);
    }

    public int points(UUID player, RpgClass c) {
        return Math.max(0, level(player, c) - 1);
    }

    public int spent(UUID player, RpgClass c) {
        return ranks(player, c).values().stream().mapToInt(Integer::intValue).sum();
    }

    public int available(UUID player, RpgClass c) {
        return Math.max(0, points(player, c) - spent(player, c));
    }

    public int rank(UUID player, RpgClass c, String id) {
        return ranks(player, c).getOrDefault(id, 0);
    }

    /** Ranks spent in {@code branch} below {@code tier}: what tier {@code tier}'s gate counts. */
    public int spentBelow(UUID player, RpgClass c, int branch, int tier) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks(player, c).entrySet()) {
            Talent t = get(c, e.getKey());
            if (t != null && t.branch() == branch && t.tier() < tier) sum += e.getValue();
        }
        return sum;
    }

    /** The class's learned keystone, or null. */
    public Talent keystone(UUID player, RpgClass c) {
        for (String id : ranks(player, c).keySet()) {
            Talent t = get(c, id);
            if (t != null && t.keystone()) return t;
        }
        return null;
    }

    /** Why {@code t} can't take another rank right now, or OK. */
    public Result check(UUID player, RpgClass c, Talent t) {
        if (rank(player, c, t.id()) >= t.maxRank()) return Result.MAXED;
        if (spentBelow(player, c, t.branch(), t.tier()) < t.gate()) return Result.TIER_LOCKED;
        if (t.keystone()) {
            if (level(player, c) < Talent.KEYSTONE_LEVEL) return Result.LEVEL_LOCKED;
            Talent other = keystone(player, c);
            if (other != null && !other.id().equals(t.id())) return Result.OTHER_KEYSTONE;
        }
        if (available(player, c) <= 0) return Result.NO_POINTS;
        return Result.OK;
    }

    public Result learn(UUID player, RpgClass c, Talent t) {
        Result r = check(player, c, t);
        if (r != Result.OK) return r;
        ranks(player, c).merge(t.id(), 1, Integer::sum);
        classes.markDirty();
        return Result.OK;
    }

    /** Takes one rank back, unless a higher node in the branch would lose its gate. */
    public Result unlearn(UUID player, RpgClass c, Talent t) {
        int rank = rank(player, c, t.id());
        if (rank == 0) return Result.NOT_LEARNED;
        for (Map.Entry<String, Integer> e : ranks(player, c).entrySet()) {
            Talent higher = get(c, e.getKey());
            if (higher == null || higher.branch() != t.branch() || higher.tier() <= t.tier()) continue;
            if (spentBelow(player, c, t.branch(), higher.tier()) - 1 < higher.gate()) return Result.STRANDS_HIGHER;
        }
        if (rank == 1) ranks(player, c).remove(t.id());
        else ranks(player, c).put(t.id(), rank - 1);
        classes.markDirty();
        return Result.OK;
    }

    /** Refunds every point in {@code c}'s tree. Always free. */
    public void respec(UUID player, RpgClass c) {
        ranks(player, c).clear();
        classes.markDirty();
    }

    /** Respecs any class with more points spent than it has (an admin lowered its XP); true if one did. */
    public boolean repair(UUID player) {
        boolean any = false;
        for (RpgClass c : RpgClass.values()) {
            if (spent(player, c) > points(player, c)) {
                respec(player, c);
                any = true;
            }
        }
        return any;
    }

    /** Sets a rank directly (admin and tests), ignoring points and gates. */
    public void force(UUID player, RpgClass c, String id, int rank) {
        if (rank <= 0) ranks(player, c).remove(id);
        else ranks(player, c).put(id, rank);
        classes.markDirty();
    }

    private Map<String, Integer> ranks(UUID player, RpgClass c) {
        return classes.profile(player).tree(c);
    }
}

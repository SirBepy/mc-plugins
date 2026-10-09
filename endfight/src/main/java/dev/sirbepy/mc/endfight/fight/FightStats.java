package dev.sirbepy.mc.endfight.fight;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Per-player tallies for one dragon fight: how much damage each player dealt the dragon, and how
 * many cult mobs of each type each player killed. For the 50% wave's phantoms (sent after the top
 * damage dealers) and the extra rewards for the top dealer and the top killer of each mob type.
 *
 * <p>Kept on the dragon's own PDC, the same restart-safe pattern as the fighter list and the wave
 * thresholds: a restart mid-fight keeps the tallies, and a new dragon starts with none. Stored as
 * {@code uuid=amount,...} and {@code type|uuid=count,...} strings.
 */
public final class FightStats {

    static final NamespacedKey DAMAGE = new NamespacedKey("endfight", "fight-damage");
    static final NamespacedKey KILLS = new NamespacedKey("endfight", "fight-kills");

    private FightStats() {
    }

    public static void addDamage(PersistentDataContainer pdc, UUID player, double amount) {
        if (amount <= 0) {
            return;
        }
        Map<UUID, Double> damage = damage(pdc);
        damage.merge(player, amount, Double::sum);
        List<String> parts = new ArrayList<>(damage.size());
        damage.forEach((id, total) -> parts.add(id + "=" + total));
        pdc.set(DAMAGE, PersistentDataType.STRING, String.join(",", parts));
    }

    /** Damage dealt per player, in the order they first hit. */
    public static Map<UUID, Double> damage(PersistentDataContainer pdc) {
        Map<UUID, Double> out = new LinkedHashMap<>();
        for (String[] pair : pairs(pdc.get(DAMAGE, PersistentDataType.STRING))) {
            try {
                out.put(UUID.fromString(pair[0]), Double.parseDouble(pair[1]));
            } catch (IllegalArgumentException ignored) {
                // a hand-edited value; skip it
            }
        }
        return out;
    }

    /** Players by damage dealt, most first; ties keep first-hit order. At most {@code limit}. */
    public static List<UUID> topDamagers(PersistentDataContainer pdc, int limit) {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(damage(pdc).entrySet());
        entries.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        return entries.stream().limit(Math.max(0, limit)).map(Map.Entry::getKey).toList();
    }

    public static void addKill(PersistentDataContainer pdc, UUID player, String type) {
        Map<String, Map<UUID, Integer>> kills = kills(pdc);
        kills.computeIfAbsent(type, t -> new LinkedHashMap<>()).merge(player, 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        kills.forEach((t, byPlayer) -> byPlayer.forEach((id, count) -> parts.add(t + "|" + id + "=" + count)));
        pdc.set(KILLS, PersistentDataType.STRING, String.join(",", parts));
    }

    /** Kills per mob type, then per player in the order they first killed one of that type. */
    public static Map<String, Map<UUID, Integer>> kills(PersistentDataContainer pdc) {
        Map<String, Map<UUID, Integer>> out = new TreeMap<>();
        for (String[] pair : pairs(pdc.get(KILLS, PersistentDataType.STRING))) {
            int bar = pair[0].indexOf('|');
            if (bar <= 0) {
                continue;
            }
            try {
                UUID id = UUID.fromString(pair[0].substring(bar + 1));
                out.computeIfAbsent(pair[0].substring(0, bar), t -> new LinkedHashMap<>())
                        .put(id, Integer.parseInt(pair[1]));
            } catch (IllegalArgumentException ignored) {
                // a hand-edited value; skip it
            }
        }
        return out;
    }

    /** The player with the most kills of each type; a tie goes to whoever got there first. */
    public static Map<String, UUID> topKillers(PersistentDataContainer pdc) {
        Map<String, UUID> out = new TreeMap<>();
        kills(pdc).forEach((type, byPlayer) -> {
            UUID best = null;
            int bestCount = 0;
            for (Map.Entry<UUID, Integer> entry : byPlayer.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            if (best != null) {
                out.put(type, best);
            }
        });
        return out;
    }

    private static List<String[]> pairs(String raw) {
        List<String[]> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            int eq = part.lastIndexOf('=');
            if (eq > 0) {
                out.add(new String[] {part.substring(0, eq), part.substring(eq + 1)});
            }
        }
        return out;
    }
}

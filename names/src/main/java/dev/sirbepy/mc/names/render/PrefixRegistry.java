package dev.sirbepy.mc.names.render;

import net.kyori.adventure.text.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prefixes added by other plugins (e.g. Tag's IT marker) through {@code NamesApi}. Not persisted:
 * the owning plugin re-applies its prefixes on its own enable and on player join.
 */
public final class PrefixRegistry {

    private final Map<UUID, Map<String, PrefixEntry>> byPlayer = new ConcurrentHashMap<>();

    public void set(UUID player, String key, Component prefix, int priority) {
        byPlayer.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>())
                .put(key, new PrefixEntry(key, prefix, priority));
    }

    public void remove(UUID player, String key) {
        Map<String, PrefixEntry> prefixes = byPlayer.get(player);
        if (prefixes != null) {
            prefixes.remove(key);
        }
    }

    public void clear(UUID player) {
        byPlayer.remove(player);
    }

    /** Prefix components for a player, lowest priority first. */
    public List<Component> prefixesFor(UUID player) {
        Map<String, PrefixEntry> prefixes = byPlayer.get(player);
        if (prefixes == null || prefixes.isEmpty()) {
            return List.of();
        }
        return prefixes.values().stream()
                .sorted(Comparator.comparingInt(PrefixEntry::priority))
                .map(PrefixEntry::prefix)
                .toList();
    }
}

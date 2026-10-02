package dev.sirbepy.mc.names.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Registered in Bukkit's ServicesManager by the Names plugin. Prefixes are not persisted: the
 * plugin that sets one owns it and re-applies it on its own enable and on player join.
 */
public interface NamesApi {

    /** Adds or replaces the prefix stored under {@code key}. Lower priority renders further left. */
    void setPrefix(UUID player, String key, Component prefix, int priority);

    void removePrefix(UUID player, String key);

    /** Prefixes plus the colored nickname (or real name), exactly as shown in chat and tab. */
    Component nameComponent(Player player);

    /** Colored nickname (or real name) without prefixes. */
    Component plainName(Player player);

    /**
     * Mirrors the hidden-nametag team onto a scoreboard a plugin assigns to a player, so the
     * vanilla nametag stays hidden while that player is viewing it.
     */
    void registerScoreboard(Scoreboard scoreboard);

    void unregisterScoreboard(Scoreboard scoreboard);

    /** Re-renders chat name, tab entry and floating nametag for the player. */
    void refresh(Player player);
}

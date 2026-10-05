package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.names.api.NamesApi;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/** Bare no-op stand-in for tests that exercise Tag's own bookkeeping, not Names' rendering. */
public final class NoopNamesApi implements NamesApi {
    @Override
    public void setPrefix(UUID player, String key, Component prefix, int priority) {
    }

    @Override
    public void removePrefix(UUID player, String key) {
    }

    @Override
    public Component nameComponent(Player player) {
        return Component.text(player.getName());
    }

    @Override
    public Component plainName(Player player) {
        return Component.text(player.getName());
    }

    @Override
    public void registerScoreboard(Scoreboard scoreboard) {
    }

    @Override
    public void unregisterScoreboard(Scoreboard scoreboard) {
    }

    @Override
    public void refresh(Player player) {
    }
}

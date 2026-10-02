package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.names.api.NamesApi;
import dev.sirbepy.mc.names.label.NametagTeamManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

public final class NamesApiImpl implements NamesApi {

    private final PrefixRegistry prefixRegistry;
    private final NameService nameService;
    private final NameChangeService changeService;
    private final NametagTeamManager teamManager;

    public NamesApiImpl(PrefixRegistry prefixRegistry, NameService nameService,
                         NameChangeService changeService, NametagTeamManager teamManager) {
        this.prefixRegistry = prefixRegistry;
        this.nameService = nameService;
        this.changeService = changeService;
        this.teamManager = teamManager;
    }

    @Override
    public void setPrefix(UUID player, String key, Component prefix, int priority) {
        prefixRegistry.set(player, key, prefix, priority);
        applyIfOnline(player);
    }

    @Override
    public void removePrefix(UUID player, String key) {
        prefixRegistry.remove(player, key);
        applyIfOnline(player);
    }

    @Override
    public Component nameComponent(Player player) {
        return nameService.nameComponent(player);
    }

    @Override
    public Component plainName(Player player) {
        return nameService.plainName(player);
    }

    @Override
    public void registerScoreboard(Scoreboard scoreboard) {
        teamManager.registerScoreboard(scoreboard);
    }

    @Override
    public void unregisterScoreboard(Scoreboard scoreboard) {
        teamManager.unregisterScoreboard(scoreboard);
    }

    @Override
    public void refresh(Player player) {
        changeService.apply(player);
    }

    private void applyIfOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            changeService.apply(player);
        }
    }
}

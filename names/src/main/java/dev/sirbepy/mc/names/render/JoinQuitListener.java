package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.names.label.FloatingLabelManager;
import dev.sirbepy.mc.names.label.NametagTeamManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps team membership, the floating label's fake entity id, and rendered name in sync. */
public final class JoinQuitListener implements Listener {

    private final NametagTeamManager teamManager;
    private final FloatingLabelManager labels;
    private final NameChangeService changeService;
    private final BedrockTag bedrockTag;

    public JoinQuitListener(NametagTeamManager teamManager, FloatingLabelManager labels,
                            NameChangeService changeService, BedrockTag bedrockTag) {
        this.teamManager = teamManager;
        this.labels = labels;
        this.changeService = changeService;
        this.bedrockTag = bedrockTag;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        teamManager.addPlayer(event.getPlayer());
        labels.initPlayer(event.getPlayer());
        bedrockTag.update(event.getPlayer());
        changeService.apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teamManager.removePlayer(event.getPlayer());
        labels.removePlayer(event.getPlayer());
    }
}

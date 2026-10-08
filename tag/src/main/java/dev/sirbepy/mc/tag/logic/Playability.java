package dev.sirbepy.mc.tag.logic;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Survival/adventure is "can actually play tag": shared by AFK auto-pass candidates, tracker
 * candidates, the teleport-block decision and the tag-hit check, so creative/spectator players
 * never get pinned, tracked, tagged or passed IT.
 */
public final class Playability {

    private Playability() {
    }

    public static boolean playable(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
}

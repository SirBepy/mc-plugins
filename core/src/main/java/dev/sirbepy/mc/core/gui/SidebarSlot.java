package dev.sirbepy.mc.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Shared predicate for whether a player's sidebar slot is free for a plugin to take (see
 * {@code dev.sirbepy.mc.tag.board.ScoreboardService} and {@code dev.sirbepy.mc.haru.quest.QuestSidebar},
 * which each used to paste a private copy of this same method). A slot is free if the player is still
 * on the main scoreboard, or if they're already on a scoreboard carrying this caller's own objective.
 */
public final class SidebarSlot {

    private SidebarSlot() {
    }

    public static boolean freeToUse(Player player, String objectiveName) {
        Scoreboard current = player.getScoreboard();
        if (current == Bukkit.getScoreboardManager().getMainScoreboard()) {
            return true;
        }
        return current.getObjective(objectiveName) != null;
    }
}

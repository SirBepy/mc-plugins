package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Speech;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The plain scoreboard sidebar for owners without the tree mod's {@code haru:quest} channel: one line
 * per active Spirit Request. Lines use {@code Objective#getScore("line-N").customName(...)} the same
 * way {@code tag}'s {@code ScoreboardService} does, so a line isn't limited to a 16-character entry id.
 *
 * <p>{@code tag}'s {@code /tag board} already owns {@code DisplaySlot.SIDEBAR} for any player who turns
 * it on, by replacing their whole {@link Scoreboard} with its own. This class never takes the slot away
 * from a scoreboard it didn't hand out itself, so it loses that fight silently rather than flicker the
 * two against each other; see the per-session report for why that's a real, still-open conflict instead
 * of something this guard fully resolves (Tag has no idea Haru's board exists, so {@code /tag board}
 * can still clobber Haru's sidebar outright).
 */
final class QuestSidebar {

    private static final String OBJECTIVE = "haru-quest";

    private final Map<UUID, Scoreboard> active = new HashMap<>();

    /** Applies (or refreshes) the sidebar, unless some other plugin already owns the player's slot. */
    void show(Player player, OwnerState state) {
        if (!freeToUse(player)) {
            return;
        }
        Scoreboard board = active.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.getScoreboardManager().getNewScoreboard());
        render(board, state);
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    /** Back to the main scoreboard, only if the player is still looking at the board we gave them. */
    void hide(Player player) {
        Scoreboard mine = active.remove(player.getUniqueId());
        if (mine != null && player.getScoreboard() == mine) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    void forget(Player player) {
        active.remove(player.getUniqueId());
    }

    /** True if the player's sidebar slot is either untouched or already ours. */
    private boolean freeToUse(Player player) {
        Scoreboard current = player.getScoreboard();
        if (current == Bukkit.getScoreboardManager().getMainScoreboard()) {
            return true;
        }
        return current.getObjective(OBJECTIVE) != null;
    }

    private void render(Scoreboard board, OwnerState state) {
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.text("Spirit Requests", Speech.HARU));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        for (String entry : List.copyOf(board.getEntries())) {
            board.resetScores(entry);
        }
        List<String> lines = lineTexts(state);
        int line = lines.size();
        for (String text : lines) {
            writeLine(objective, line--, Component.text(text));
        }
    }

    /** One formatted line per active request; pulled out so the text itself is testable without a live scoreboard. */
    static List<String> lineTexts(OwnerState state) {
        List<String> lines = new ArrayList<>();
        for (ActiveQuest quest : state.board()) {
            QuestDef def = QuestPool.byId(quest.questId());
            if (def != null) {
                lines.add(def.text() + " (" + statusText(quest, def) + ")");
            }
        }
        return lines;
    }

    static String statusText(ActiveQuest quest, QuestDef def) {
        if (quest.claimed()) {
            return "Done";
        }
        return quest.progress() >= def.amount() ? "Ready!" : quest.progress() + "/" + def.amount();
    }

    private void writeLine(Objective objective, int score, Component text) {
        Score line = objective.getScore("line-" + score);
        line.setScore(score);
        line.customName(text);
        line.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
    }
}

package dev.sirbepy.mc.tag.board;

import dev.sirbepy.mc.names.api.NamesApi;
import dev.sirbepy.mc.tag.logic.LeaderboardRanking;
import dev.sirbepy.mc.tag.state.PlayerStats;
import dev.sirbepy.mc.tag.state.TagState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player sidebar toggled by `/tag board`. Mirrored into Names so the hidden nametag team follows. */
public final class ScoreboardService {

    private static final String OBJECTIVE = "tag-board";

    private final NamesApi names;
    private final Map<UUID, Scoreboard> active = new HashMap<>();

    public ScoreboardService(NamesApi names) {
        this.names = names;
    }

    public boolean isActive(UUID player) {
        return active.containsKey(player);
    }

    public boolean toggle(Player player) {
        if (active.containsKey(player.getUniqueId())) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public void enable(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.text("Tag", NamedTextColor.GREEN));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        names.registerScoreboard(board);
        active.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    public void disable(Player player) {
        Scoreboard board = active.remove(player.getUniqueId());
        if (board != null) {
            names.unregisterScoreboard(board);
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Called every points tick and on every tag so boards never show a stale IT or rank. */
    public void refreshAll(TagState state) {
        for (Map.Entry<UUID, Scoreboard> entry : active.entrySet()) {
            render(entry.getValue(), state, entry.getKey());
        }
    }

    private void render(Scoreboard board, TagState state, UUID viewer) {
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        for (String entry : List.copyOf(board.getEntries())) {
            board.resetScores(entry);
        }

        List<LeaderboardRanking.Entry> entries = state.stats.entrySet().stream()
                .map(e -> new LeaderboardRanking.Entry(e.getKey(), nameOf(e.getKey()), e.getValue().points))
                .toList();
        List<LeaderboardRanking.Row> ranked = LeaderboardRanking.rank(entries, 5);

        int line = state.stats.size() + 10;
        line = writeLine(objective, line, Component.text("IT: " + nameOf(state.currentIt), NamedTextColor.RED));

        for (LeaderboardRanking.Row row : ranked) {
            Component text = Component.text(row.rank() + ". " + row.name() + " " + format(row.points()));
            line = writeLine(objective, line, text);
        }

        PlayerStats mine = state.stats.get(viewer);
        double myPoints = mine == null ? 0.0 : mine.points;
        writeLine(objective, line, Component.text("You: " + format(myPoints), NamedTextColor.YELLOW));
    }

    private int writeLine(Objective objective, int score, Component text) {
        Score line = objective.getScore("line-" + score);
        line.setScore(score);
        line.customName(text);
        line.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        return score - 1;
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "nobody";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

    private String format(double points) {
        return String.format(java.util.Locale.ROOT, "%.1f", points);
    }
}

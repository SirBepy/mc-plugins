package dev.sirbepy.mc.names.label;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla nametags only support 16 colors and always show the real username, so this team hides
 * them everywhere; the floating packet-only TextDisplay (see {@link FloatingLabelManager}) is the
 * real nametag replacement. Mirrored onto any scoreboard a plugin assigns to a player (Tag's
 * sidebar) so the hidden nametag stays hidden there too.
 */
public final class NametagTeamManager {

    private static final String TEAM_NAME = "names_hidden_tag";

    private final Set<Scoreboard> registered = ConcurrentHashMap.newKeySet();

    public void setup() {
        ensureTeam(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void registerScoreboard(Scoreboard scoreboard) {
        registered.add(scoreboard);
        ensureTeam(scoreboard);
        for (Player player : Bukkit.getOnlinePlayers()) {
            addMember(scoreboard, player);
        }
    }

    public void unregisterScoreboard(Scoreboard scoreboard) {
        registered.remove(scoreboard);
    }

    public void addPlayer(Player player) {
        addMember(Bukkit.getScoreboardManager().getMainScoreboard(), player);
        for (Scoreboard scoreboard : registered) {
            addMember(scoreboard, player);
        }
    }

    public void removePlayer(Player player) {
        removeMember(Bukkit.getScoreboardManager().getMainScoreboard(), player);
        for (Scoreboard scoreboard : registered) {
            removeMember(scoreboard, player);
        }
    }

    private void ensureTeam(Scoreboard scoreboard) {
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
        }
        team.setNameTagVisibility(NameTagVisibility.NEVER);
    }

    private void addMember(Scoreboard scoreboard, Player player) {
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            ensureTeam(scoreboard);
            team = scoreboard.getTeam(TEAM_NAME);
        }
        if (team != null && !team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private void removeMember(Scoreboard scoreboard, Player player) {
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team != null) {
            team.removeEntry(player.getName());
        }
    }
}

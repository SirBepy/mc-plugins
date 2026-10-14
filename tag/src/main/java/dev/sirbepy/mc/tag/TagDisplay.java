package dev.sirbepy.mc.tag;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.UUID;

/** Player-facing formatting shared by the leaderboard, commands and messages - no TagState
 * involved, so it lives outside TagService (the single place allowed to mutate that). */
public final class TagDisplay {

    private TagDisplay() {
    }

    public static String nameOf(UUID uuid) {
        if (uuid == null) {
            return "nobody";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

    public static String formatPoints(double points) {
        return String.format(Locale.ROOT, "%.1f", points);
    }
}

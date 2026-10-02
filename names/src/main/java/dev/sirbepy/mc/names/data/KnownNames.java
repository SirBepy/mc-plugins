package dev.sirbepy.mc.names.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real usernames of every player the server has ever seen, for the anti-impersonation check. */
public final class KnownNames {

    private KnownNames() {
    }

    public static List<String> excluding(UUID exceptPlayer) {
        List<String> names = new ArrayList<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getUniqueId().equals(exceptPlayer)) {
                continue;
            }
            String name = offlinePlayer.getName();
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }
}

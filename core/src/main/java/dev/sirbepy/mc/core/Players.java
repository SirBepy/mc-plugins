package dev.sirbepy.mc.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Turns a typed player argument into a player, for admin commands that may name someone offline. */
public final class Players {

    private Players() {
    }

    /**
     * Online by exact name, else a player this server has seen. Empty for a name that never joined:
     * {@code getOfflinePlayer(name)} would invent a profile for a typo instead.
     */
    public static Optional<OfflinePlayer> known(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online);
        }
        return Optional.ofNullable(Bukkit.getOfflinePlayerIfCached(name));
    }

    /**
     * A raw UUID as typed (so a player who never joined can still be named, e.g. a test bot), else
     * {@link #known}'s id. Null when it is neither. A UUID string is 36 characters and a name at most
     * 16, so the order never hides a real name.
     */
    public static UUID idOf(String nameOrId) {
        try {
            return UUID.fromString(nameOrId);
        } catch (IllegalArgumentException notUuid) {
            return known(nameOrId).map(OfflinePlayer::getUniqueId).orElse(null);
        }
    }
}

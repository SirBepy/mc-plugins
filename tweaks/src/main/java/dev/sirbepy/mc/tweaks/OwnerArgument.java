package dev.sirbepy.mc.tweaks;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** The {@code <player|uuid>} argument of the wish {@code owner} subcommands, which hand a wish to the e2e bot. */
public final class OwnerArgument {

    private OwnerArgument() {
    }

    /** The UUID {@code arg} names, or null after telling {@code sender} that name never joined. */
    public static UUID resolve(CommandSender sender, String arg) {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException notUuid) {
            Player online = Bukkit.getPlayerExact(arg);
            OfflinePlayer cached = online != null ? online : Bukkit.getOfflinePlayerIfCached(arg);
            if (cached == null) {
                sender.sendMessage(Component.text(arg + " has never joined this server."));
                return null;
            }
            return cached.getUniqueId();
        }
    }
}

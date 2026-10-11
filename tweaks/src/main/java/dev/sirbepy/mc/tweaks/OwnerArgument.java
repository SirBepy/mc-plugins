package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.core.Players;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/** The {@code <player|uuid>} argument of the wish {@code owner} subcommands, which hand a wish to the e2e bot. */
public final class OwnerArgument {

    private OwnerArgument() {
    }

    /** Whether {@code entity} is the player who owns a single-owner wish; false while no owner is set. */
    public static boolean isOwner(Entity entity, UUID owner) {
        return entity instanceof Player && entity.getUniqueId().equals(owner);
    }

    /** The UUID {@code arg} names, or null after telling {@code sender} that name never joined. */
    public static UUID resolve(CommandSender sender, String arg, Messages messages) {
        UUID id = Players.idOf(arg);
        if (id == null) {
            messages.send(sender, "owner-not-joined", Placeholder.unparsed("player", arg));
        }
        return id;
    }
}

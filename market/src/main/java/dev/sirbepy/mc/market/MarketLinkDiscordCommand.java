package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * {@code /market linkdiscord <id>}: a player pastes their own Discord numeric user id (Discord's
 * User Settings > Advanced > Developer Mode, then right-click yourself > Copy User ID) so
 * {@link DealAnnouncer} can @-mention them for real. Registration only, same split as
 * {@link MarketWatchCommand}/{@link MarketWatches}.
 */
final class MarketLinkDiscordCommand {

    private final MarketDiscordLinks links;
    private final Supplier<MarketConfig> config;

    MarketLinkDiscordCommand(MarketDiscordLinks links, Supplier<MarketConfig> config) {
        this.links = links;
        this.config = config;
    }

    ArgumentBuilder<CommandSourceStack, ?> node() {
        return Commands.literal("linkdiscord")
                .requires(src -> src.getExecutor() instanceof Player)
                .then(Commands.argument("discord-id", StringArgumentType.word())
                        .executes(ctx -> {
                            link((Player) ctx.getSource().getExecutor(), StringArgumentType.getString(ctx, "discord-id"));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void link(Player player, String discordId) {
        var messages = config.get().messages();
        if (!MarketDiscordLinks.isValidSnowflake(discordId)) {
            messages.send(player, "linkdiscord-invalid");
            return;
        }
        links.link(player.getUniqueId(), discordId);
        messages.send(player, "linkdiscord-success");
    }
}

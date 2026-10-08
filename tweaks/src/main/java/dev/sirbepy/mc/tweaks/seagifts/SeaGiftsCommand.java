package dev.sirbepy.mc.tweaks.seagifts;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import dev.sirbepy.mc.tweaks.OwnerArgument;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /seagifts [dolphin|conduit]}: only the owner sees it (status, or flip one gift). Console and ops
 * get {@code /seagifts owner <player|uuid>} to move the wish to someone else (the e2e suite's bot).
 */
public final class SeaGiftsCommand {

    static final String ADMIN = "tweaks.seagifts.admin";

    private final SeaGifts gifts;
    private final Consumer<UUID> setOwner;

    public SeaGiftsCommand(SeaGifts gifts, Consumer<UUID> setOwner) {
        this.gifts = gifts;
        this.setOwner = setOwner;
    }

    public void registerInto(Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("seagifts")
                .requires(src -> isOwner(src) || src.getSender().hasPermission(ADMIN))
                .executes(c -> {
                    if (c.getSource().getSender() instanceof Player player) gifts.status(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("owner").requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::owner)));
        for (SeaGift gift : SeaGift.values()) {
            root.then(Commands.literal(gift.key()).requires(this::isOwner).executes(c -> {
                gifts.flip((Player) c.getSource().getSender(), gift);
                return Command.SINGLE_SUCCESS;
            }));
        }
        registrar.register(root.build(), "Switch the sea's gifts on or off");
    }

    private boolean isOwner(CommandSourceStack src) {
        return src.getSender() instanceof Player player && gifts.isOwner(player);
    }

    private int owner(CommandContext<CommandSourceStack> c) {
        String arg = StringArgumentType.getString(c, "player");
        UUID uuid = OwnerArgument.resolve(c.getSource().getSender(), arg);
        if (uuid == null) return Command.SINGLE_SUCCESS;
        setOwner.accept(uuid);
        // The command tree was sent at join with the old owner's view of it.
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        c.getSource().getSender().sendMessage(Component.text("The sea's gifts now answer to " + arg + " (" + uuid + ")."));
        return Command.SINGLE_SUCCESS;
    }
}

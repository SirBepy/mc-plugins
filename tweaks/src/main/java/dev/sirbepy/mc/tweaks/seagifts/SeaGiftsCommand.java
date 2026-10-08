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
import java.util.function.BiPredicate;

/**
 * {@code /seagifts [dolphin|conduit]}: only owners see it (status, or flip one of their own gifts). Console
 * and ops get {@code /seagifts add|remove <player|uuid>} to edit the owner list (the e2e suite's bots).
 * Removing an owner leaves whatever effects are on him; clear them by hand if they should go.
 */
public final class SeaGiftsCommand {

    static final String ADMIN = "tweaks.seagifts.admin";

    private final SeaGifts gifts;
    /** {@code (player, owner)}: makes the player an owner or not; false when he already was / wasn't. */
    private final BiPredicate<UUID, Boolean> setOwner;

    public SeaGiftsCommand(SeaGifts gifts, BiPredicate<UUID, Boolean> setOwner) {
        this.gifts = gifts;
        this.setOwner = setOwner;
    }

    public void registerInto(Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("seagifts")
                .requires(src -> isOwner(src) || src.getSender().hasPermission(ADMIN))
                .executes(c -> {
                    if (c.getSource().getSender() instanceof Player player) gifts.status(player);
                    return Command.SINGLE_SUCCESS;
                });
        for (boolean add : new boolean[] {true, false}) {
            root.then(Commands.literal(add ? "add" : "remove").requires(src -> src.getSender().hasPermission(ADMIN))
                    .then(Commands.argument("player", StringArgumentType.word()).executes(c -> setOwner(c, add))));
        }
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

    private int setOwner(CommandContext<CommandSourceStack> c, boolean add) {
        String arg = StringArgumentType.getString(c, "player");
        UUID uuid = OwnerArgument.resolve(c.getSource().getSender(), arg);
        if (uuid == null) return Command.SINGLE_SUCCESS;
        String who = arg + " (" + uuid + ")";
        if (!setOwner.test(uuid, add)) {
            c.getSource().getSender().sendMessage(Component.text(
                    "The sea's gifts " + (add ? "already answer" : "don't answer") + " to " + who + "."));
            return Command.SINGLE_SUCCESS;
        }
        // The command tree was sent at join with the old owner list's view of it.
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        c.getSource().getSender().sendMessage(Component.text(
                "The sea's gifts " + (add ? "now answer" : "no longer answer") + " to " + who + "."));
        return Command.SINGLE_SUCCESS;
    }
}

package dev.sirbepy.mc.tweaks.seagifts;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.core.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import dev.sirbepy.mc.tweaks.OwnerArgument;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
    private final Messages messages;

    public SeaGiftsCommand(SeaGifts gifts, BiPredicate<UUID, Boolean> setOwner, Messages messages) {
        this.gifts = gifts;
        this.setOwner = setOwner;
        this.messages = messages;
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
        UUID uuid = OwnerArgument.resolve(c.getSource().getSender(), arg, messages);
        if (uuid == null) return Command.SINGLE_SUCCESS;
        TagResolver playerArg = Placeholder.unparsed("player", arg);
        TagResolver uuidArg = Placeholder.unparsed("uuid", uuid.toString());
        if (!setOwner.test(uuid, add)) {
            messages.send(c.getSource().getSender(), add ? "seagifts-owner-already-owner" : "seagifts-owner-already-not-owner",
                    playerArg, uuidArg);
            return Command.SINGLE_SUCCESS;
        }
        // The command tree was sent at join with the old owner list's view of it.
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        messages.send(c.getSource().getSender(), add ? "seagifts-owner-now-owner" : "seagifts-owner-no-longer-owner",
                playerArg, uuidArg);
        return Command.SINGLE_SUCCESS;
    }
}

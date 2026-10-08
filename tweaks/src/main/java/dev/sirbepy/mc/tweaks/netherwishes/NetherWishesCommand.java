package dev.sirbepy.mc.tweaks.netherwishes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.tweaks.OwnerArgument;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /netherwishes owner <player|uuid>} (console and ops): moves CoffiebagSupreme's three Nether wishes to
 * someone else, which the e2e suite does to borrow them for a bot. The wishes themselves have no command.
 */
public final class NetherWishesCommand {

    static final String ADMIN = "tweaks.netherwishes.admin";

    private final Consumer<UUID> setOwner;

    public NetherWishesCommand(Consumer<UUID> setOwner) {
        this.setOwner = setOwner;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("netherwishes")
                .requires(src -> src.getSender().hasPermission(ADMIN))
                .then(Commands.literal("owner")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::owner)))
                .build(), "Move CoffiebagSupreme's Nether wishes to another player");
    }

    private int owner(CommandContext<CommandSourceStack> c) {
        String arg = StringArgumentType.getString(c, "player");
        UUID uuid = OwnerArgument.resolve(c.getSource().getSender(), arg);
        if (uuid == null) return Command.SINGLE_SUCCESS;
        setOwner.accept(uuid);
        c.getSource().getSender().sendMessage(Component.text("The Nether wishes now answer to " + arg + " (" + uuid + ")."));
        return Command.SINGLE_SUCCESS;
    }
}

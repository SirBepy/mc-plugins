package dev.sirbepy.mc.minibosses.chronicle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.core.Players;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * {@code /chronicle} opens a player's journal. {@code /chronicle admin ...} (ops, console and RCON)
 * places or removes the Chronicler and inspects or sets a player's stage for testing and repairs.
 * {@code chronicle} and {@code listener} are null while the story can't be decrypted (no key on
 * this server), in which case only placing the NPC works.
 */
public final class ChronicleCommand {

    private final ChroniclerNpc npc;
    private final Chronicle chronicle;
    private final ChronicleListener listener;

    public ChronicleCommand(ChroniclerNpc npc, Chronicle chronicle, ChronicleListener listener) {
        this.npc = npc;
        this.chronicle = chronicle;
        this.listener = listener;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("chronicle")
                .executes(this::journal)
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission("minibosses.admin"))
                        .then(Commands.literal("place").executes(this::placeHere)
                                .then(Commands.argument("world", StringArgumentType.word())
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                                                        .executes(this::placeAt)))))))
                        .then(Commands.literal("remove").executes(c -> {
                            npc.remove();
                            send(c, "The Chronicler is gone.");
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("status").then(player().executes(this::status)))
                        .then(Commands.literal("stage").then(player()
                                .then(Commands.argument("stage", IntegerArgumentType.integer(0)).executes(this::setStage))))
                        .then(Commands.literal("reset").then(player().executes(this::reset))))
                .build(), "Read your Guardian Chronicles journal");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word());
    }

    private int journal(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getSender() instanceof Player player)) {
            send(c, "Only players have a journal.");
            return 0;
        }
        if (listener == null) {
            send(c, "The Chronicles are not open yet.");
            return 0;
        }
        listener.openJournal(player);
        return Command.SINGLE_SUCCESS;
    }

    private int placeHere(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getSender() instanceof Player player)) {
            send(c, "From the console, give a spot: place <world> <x> <y> <z> <yaw>.");
            return 0;
        }
        Location at = player.getLocation().toBlockLocation().add(0.5, 0, 0.5);
        at.setYaw(player.getLocation().getYaw());
        at.setPitch(0);
        npc.place(at);
        send(c, "The Chronicler now stands here.");
        return Command.SINGLE_SUCCESS;
    }

    private int placeAt(CommandContext<CommandSourceStack> c) {
        World world = Bukkit.getWorld(StringArgumentType.getString(c, "world"));
        if (world == null) {
            send(c, "No such world.");
            return 0;
        }
        npc.place(new Location(world, DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"),
                DoubleArgumentType.getDouble(c, "z"), FloatArgumentType.getFloat(c, "yaw"), 0));
        send(c, "The Chronicler is placed.");
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> c) {
        Optional<OfflinePlayer> target = target(c);
        if (target.isEmpty()) {
            return 0;
        }
        send(c, target.get().getName() + " is at stage " + chronicle.stage(target.get().getUniqueId())
                + " of " + chronicle.doneStage() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int setStage(CommandContext<CommandSourceStack> c) {
        Optional<OfflinePlayer> target = target(c);
        if (target.isEmpty()) {
            return 0;
        }
        int stage = Math.min(IntegerArgumentType.getInteger(c, "stage"), chronicle.doneStage());
        chronicle.progress().setStage(target.get().getUniqueId(), stage);
        send(c, target.get().getName() + " is now at stage " + stage + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> c) {
        Optional<OfflinePlayer> target = target(c);
        if (target.isEmpty()) {
            return 0;
        }
        chronicle.progress().reset(target.get().getUniqueId());
        send(c, target.get().getName() + "'s Chronicles progress is cleared.");
        return Command.SINGLE_SUCCESS;
    }

    private Optional<OfflinePlayer> target(CommandContext<CommandSourceStack> c) {
        if (chronicle == null) {
            send(c, "The Chronicles are locked on this server (no key).");
            return Optional.empty();
        }
        Optional<OfflinePlayer> player = Players.known(StringArgumentType.getString(c, "player"));
        if (player.isEmpty()) {
            send(c, "No player by that name has been here.");
        }
        return player;
    }

    private static void send(CommandContext<CommandSourceStack> c, String message) {
        c.getSource().getSender().sendMessage(Component.text(message, NamedTextColor.GOLD));
    }
}

package dev.sirbepy.mc.minibosses.arena;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * {@code /guardianadmin arena ...}, ops, console and RCON only: set a Guardian's arena from two
 * opposite corner blocks, clear it, or list every arena with its size. {@code list} is also how
 * Shenron answers a player asking where a Guardian's arena is.
 */
public final class ArenaCommand {

    private final ArenaRegistry arenas;
    private final GuardianRegistry guardians;

    public ArenaCommand(ArenaRegistry arenas, GuardianRegistry guardians) {
        this.arenas = arenas;
        this.guardians = guardians;
    }

    public void registerInto(Commands registrar) {
        var corners = corner("x1").then(corner("y1").then(corner("z1")
                .then(corner("x2").then(corner("y2").then(corner("z2").executes(this::set))))));
        var set = Commands.literal("set").then(Commands.argument("guardian", StringArgumentType.word())
                .then(Commands.argument("world", StringArgumentType.word()).then(corners)));
        registrar.register(Commands.literal("guardianadmin")
                .requires(src -> src.getSender().hasPermission("minibosses.admin"))
                .then(Commands.literal("arena")
                        .then(Commands.literal("list").executes(this::list))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("guardian", StringArgumentType.word()).executes(this::clear)))
                        .then(set))
                .build(), "Guardian arenas (admin)");
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> corner(String name) {
        return Commands.argument(name, IntegerArgumentType.integer());
    }

    private int set(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "guardian");
        if (guardians.get(id).isEmpty()) {
            send(c, "No Guardian called " + id + ".");
            return 0;
        }
        World world = Bukkit.getWorld(StringArgumentType.getString(c, "world"));
        if (world == null) {
            send(c, "No such world.");
            return 0;
        }
        Arena arena = Arena.ofCorners(id, world, IntegerArgumentType.getInteger(c, "x1"),
                IntegerArgumentType.getInteger(c, "y1"), IntegerArgumentType.getInteger(c, "z1"),
                IntegerArgumentType.getInteger(c, "x2"), IntegerArgumentType.getInteger(c, "y2"),
                IntegerArgumentType.getInteger(c, "z2"));
        arenas.set(arena);
        send(c, "Arena set: " + arena.describe() + ". The Guardian rises at " + spot(arena) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "guardian");
        send(c, arenas.clear(id) ? "Arena for " + id + " cleared." : id + " had no arena.");
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> c) {
        if (arenas.all().isEmpty()) {
            send(c, "No Guardian arenas are set.");
        }
        arenas.all().values().forEach(arena -> send(c, arena.describe() + ", rises at " + spot(arena)));
        return Command.SINGLE_SUCCESS;
    }

    private static String spot(Arena arena) {
        var at = arena.center();
        return at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
    }

    private static void send(CommandContext<CommandSourceStack> c, String message) {
        c.getSource().getSender().sendMessage(Component.text(message, NamedTextColor.GOLD));
    }
}

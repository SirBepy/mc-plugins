package dev.sirbepy.mc.guild.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.Place;
import dev.sirbepy.mc.guild.staff.Staff;
import dev.sirbepy.mc.guild.staff.StaffNpcs;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Guild Hall staff NPCs and the hall-guard box, split out of {@link GuildAdminCommand} (which keeps
 * member/economy/event repairs) so each file holds one concern. {@link #attach} adds {@code staff}
 * and {@code hall} to whatever {@code /guildadmin} root it is given, so the syntax and replies of
 * {@code /guildadmin staff ...} and {@code /guildadmin hall guard ...} are unchanged.
 */
public final class StaffAdminCommand {

    private final Guild guild;
    private final StaffNpcs staff;

    public StaffAdminCommand(Guild guild, StaffNpcs staff) {
        this.guild = guild;
        this.staff = staff;
    }

    public LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("staff")
                        .then(Commands.literal("list").executes(this::staffList))
                        .then(Commands.literal("place")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (Staff s : Staff.values()) {
                                                b.suggest(s.id());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(this::placeHere)
                                        .then(Commands.argument("world", StringArgumentType.word())
                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                                                                .executes(this::placeAt))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("role", StringArgumentType.word()).executes(this::removeStaff))))
                .then(Commands.literal("hall")
                        .then(Commands.literal("guard")
                                .then(Commands.literal("off").executes(c -> {
                                    guild.store().setGuard(null);
                                    guild.store().save();
                                    send(c, "The Guild Hall is no longer guarded by the Guild plugin.");
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.argument("world", StringArgumentType.word())
                                        .then(Commands.argument("x0", IntegerArgumentType.integer())
                                                .then(Commands.argument("y0", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z0", IntegerArgumentType.integer())
                                                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("y1", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                                                        .executes(this::setGuard))))))))));
    }

    private int staffList(CommandContext<CommandSourceStack> c) {
        for (Staff s : Staff.values()) {
            Place spot = guild.store().staffSpots().get(s.id());
            String state = spot == null ? "not placed" : (staff.villager(s) == null ? "placed, not loaded" : "present")
                    + " at " + spot.describe();
            send(c, s.id() + " (" + s.displayName() + ", " + s.title() + "): " + state);
        }
        Place.Box box = guild.store().guard();
        send(c, "Hall guard: " + (box == null ? "off" : box.world() + " " + box.x0() + " " + box.y0() + " " + box.z0()
                + " to " + box.x1() + " " + box.y1() + " " + box.z1()));
        return Command.SINGLE_SUCCESS;
    }

    private Staff role(CommandContext<CommandSourceStack> c) {
        Staff s = Staff.byId(StringArgumentType.getString(c, "role"));
        if (s == null) {
            StringBuilder ids = new StringBuilder();
            for (Staff each : Staff.values()) {
                ids.append(each.id()).append(' ');
            }
            send(c, "Role must be one of: " + ids.toString().trim());
        }
        return s;
    }

    private int placeHere(CommandContext<CommandSourceStack> c) {
        Staff s = role(c);
        if (s == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!(c.getSource().getSender() instanceof Player player)) {
            send(c, "From the console: guildadmin staff place " + s.id() + " <world> <x> <y> <z> <yaw>");
            return Command.SINGLE_SUCCESS;
        }
        Location at = player.getLocation();
        staff.place(s, new Place(at.getWorld().getName(), at.getBlockX() + 0.5, at.getY(), at.getBlockZ() + 0.5, at.getYaw()));
        send(c, s.displayName() + " now stands at " + guild.store().staffSpots().get(s.id()).describe() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int placeAt(CommandContext<CommandSourceStack> c) {
        Staff s = role(c);
        String world = StringArgumentType.getString(c, "world");
        if (s == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (Bukkit.getWorld(world) == null) {
            send(c, "No loaded world by that name.");
            return Command.SINGLE_SUCCESS;
        }
        Place spot = new Place(world, DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"),
                DoubleArgumentType.getDouble(c, "z"), FloatArgumentType.getFloat(c, "yaw"));
        staff.place(s, spot);
        send(c, s.displayName() + " now stands at " + spot.describe() + " (appears once that chunk is loaded).");
        return Command.SINGLE_SUCCESS;
    }

    private int removeStaff(CommandContext<CommandSourceStack> c) {
        Staff s = role(c);
        if (s != null) {
            staff.remove(s);
            send(c, s.displayName() + " is gone until placed again.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int setGuard(CommandContext<CommandSourceStack> c) {
        String world = StringArgumentType.getString(c, "world");
        Place.Box box = Place.Box.of(world, IntegerArgumentType.getInteger(c, "x0"), IntegerArgumentType.getInteger(c, "y0"),
                IntegerArgumentType.getInteger(c, "z0"), IntegerArgumentType.getInteger(c, "x1"),
                IntegerArgumentType.getInteger(c, "y1"), IntegerArgumentType.getInteger(c, "z1"));
        guild.store().setGuard(box);
        guild.store().save();
        send(c, "The Guild Hall box is guarded: no breaking, placing, sign edits or tampering for non-ops inside it.");
        return Command.SINGLE_SUCCESS;
    }

    private static void send(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().getSender().sendMessage(Component.text(text));
    }
}

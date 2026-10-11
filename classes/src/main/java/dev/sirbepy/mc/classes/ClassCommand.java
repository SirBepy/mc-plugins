package dev.sirbepy.mc.classes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.core.Players;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /class}: the menu, {@code choose <class>}, {@code info [class]}, {@code levels},
 * {@code ability} and {@code leave}; {@code admin ...} for ops and the console (Player Claude).
 */
final class ClassCommand {

    static final String ADMIN = "classes.admin";

    private final Classes classes;
    private final ClassPicker picker;
    private final ClassMenu menu;
    private final Abilities abilities;
    /** Re-applies Frail Body and the footer after an admin change to an online player. */
    private final Consumer<Player> resync;

    ClassCommand(Classes classes, ClassPicker picker, ClassMenu menu, Abilities abilities, Consumer<Player> resync) {
        this.classes = classes;
        this.picker = picker;
        this.menu = menu;
        this.abilities = abilities;
        this.resync = resync;
    }

    void registerInto(Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("class")
                .executes(c -> player(c, menu::open))
                .then(Commands.literal("choose").then(classArg("class").executes(c -> player(c, p -> {
                    RpgClass rpg = classOf(c, "class");
                    if (rpg == null) unknown(p, c);
                    else picker.pick(p, rpg);
                }))))
                .then(Commands.literal("info")
                        .executes(c -> player(c, p -> {
                            RpgClass active = classes.active(p.getUniqueId());
                            if (active == null) p.sendMessage(Component.text("You have no class. /class to pick one.", NamedTextColor.GRAY));
                            else describe(p, active, classes.profile(p.getUniqueId()));
                        }))
                        .then(classArg("class").executes(c -> player(c, p -> {
                            RpgClass rpg = classOf(c, "class");
                            if (rpg == null) unknown(p, c);
                            else describe(p, rpg, classes.profile(p.getUniqueId()));
                        }))))
                .then(Commands.literal("levels").executes(c -> player(c, p -> levels(p, classes.profile(p.getUniqueId())))))
                .then(Commands.literal("secondary").then(classArg("class").executes(c -> player(c, p -> {
                    RpgClass rpg = classOf(c, "class");
                    if (rpg == null) unknown(p, c);
                    else picker.pickSecondary(p, rpg);
                }))))
                .then(Commands.literal("ability").executes(c -> player(c, abilities::use)))
                .then(Commands.literal("leave").executes(c -> player(c, picker::leave)))
                .then(admin());
        registrar.register(root.build(), "RPG classes: Warrior, Ranger, Arcanist, Artificer");
    }

    private LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("admin")
                .requires(src -> src.getSender().hasPermission(ADMIN))
                .then(Commands.literal("info").then(playerArg().executes(c -> target(c, (sender, id) -> {
                    ClassProfile p = classes.profile(id);
                    say(sender, "Active: " + (p.active() == null ? "none" : p.active().displayName())
                            + ", today " + p.earnedToday() + "/" + classes.dailyCap() + " XP");
                    if (classes.hasDualClassGrant(id)) {
                        RpgClass secondary = classes.secondary(id);
                        say(sender, "Dual-class grant: secondary " + (secondary == null ? "not yet picked" : secondary.displayName() + " (permanent)"));
                    } else if (p.secondary() != null) {
                        say(sender, "Dual-class grant revoked; stored secondary pick " + p.secondary().displayName() + " is inactive.");
                    }
                    for (RpgClass rpg : RpgClass.values()) {
                        long locked = classes.lockedUntil(id, rpg);
                        say(sender, rpg.displayName() + ": " + p.xp(rpg) + " XP, " + ClassDisplay.progress(p, rpg)
                                + (locked == 0 ? "" : ", locked until " + Instant.ofEpochMilli(locked)));
                    }
                }))))
                .then(Commands.literal("setxp").then(playerArg().then(classArg("class")
                        .then(Commands.argument("xp", IntegerArgumentType.integer(0, Levels.MAX_XP))
                                .executes(c -> target(c, (sender, id) -> {
                                    RpgClass rpg = classOf(c, "class");
                                    if (rpg == null) {
                                        say(sender, "No such class.");
                                        return;
                                    }
                                    classes.setXp(id, rpg, IntegerArgumentType.getInteger(c, "xp"));
                                    say(sender, rpg.displayName() + " XP set: " + ClassDisplay.progress(classes.profile(id), rpg));
                                }))))))
                .then(Commands.literal("set").then(playerArg().then(Commands.argument("class", StringArgumentType.word())
                        .suggests((c, b) -> {
                            b.suggest("none");
                            for (RpgClass rpg : RpgClass.values()) b.suggest(rpg.id());
                            return b.buildFuture();
                        })
                        .executes(c -> target(c, (sender, id) -> {
                            String arg = StringArgumentType.getString(c, "class");
                            if (arg.equalsIgnoreCase("none")) {
                                classes.leave(id);
                                say(sender, "Class cleared.");
                                return;
                            }
                            RpgClass rpg = RpgClass.parse(arg);
                            if (rpg == null) {
                                say(sender, "No such class.");
                                return;
                            }
                            classes.unlock(id);
                            classes.choose(id, rpg);
                            say(sender, "Class set to " + rpg.displayName() + " (7-day lock restarted).");
                        })))))
                .then(Commands.literal("unlock").then(playerArg().executes(c -> target(c, (sender, id) -> {
                    classes.unlock(id);
                    say(sender, "Change lock lifted: their next pick is free.");
                }))))
                .then(Commands.literal("today").then(playerArg().executes(c -> target(c, (sender, id) -> {
                    classes.resetToday(id);
                    say(sender, "Today's class XP reset to 0/" + classes.dailyCap() + ".");
                }))))
                .then(Commands.literal("ready").then(playerArg().executes(c -> target(c, (sender, id) -> {
                    classes.readyAbility(id);
                    say(sender, "Ability ready.");
                }))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> classArg(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests((c, b) -> {
            for (RpgClass rpg : RpgClass.values()) b.suggest(rpg.id());
            return b.buildFuture();
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArg() {
        return Commands.argument("player", StringArgumentType.word());
    }

    private static RpgClass classOf(CommandContext<CommandSourceStack> c, String arg) {
        return RpgClass.parse(StringArgumentType.getString(c, arg));
    }

    private static void unknown(Player p, CommandContext<CommandSourceStack> c) {
        p.sendMessage(Component.text("No class called " + StringArgumentType.getString(c, "class")
                + ". Classes: warrior, ranger, arcanist, artificer.", NamedTextColor.RED));
    }

    static void describe(Player p, RpgClass rpg, ClassProfile profile) {
        int level = profile.level(rpg);
        p.sendMessage(Component.text(rpg.displayName() + " · " + ClassDisplay.progress(profile, rpg), rpg.color()));
        p.sendMessage(Component.text(rpg.passiveLine(level), NamedTextColor.GRAY));
        p.sendMessage(Component.text(rpg.abilityLine(level), NamedTextColor.GRAY));
        p.sendMessage(Component.text(rpg.weaknessLine(), NamedTextColor.GRAY));
    }

    static void levels(Player p, ClassProfile profile) {
        for (RpgClass rpg : RpgClass.values()) {
            p.sendMessage(Component.text(rpg.displayName() + ": " + ClassDisplay.progress(profile, rpg)
                    + (profile.active() == rpg ? " (active)" : ""), rpg.color()));
        }
    }

    private static int player(CommandContext<CommandSourceStack> c, Consumer<Player> action) {
        if (c.getSource().getExecutor() instanceof Player p) {
            action.accept(p);
        } else {
            say(c.getSource().getSender(), "Players only. Ops: /class admin info|setxp|set|unlock|today|ready <player> ...");
        }
        return Command.SINGLE_SUCCESS;
    }

    private interface TargetAction {
        void run(CommandSender sender, UUID target);
    }

    private int target(CommandContext<CommandSourceStack> c, TargetAction action) {
        CommandSender sender = c.getSource().getSender();
        String arg = StringArgumentType.getString(c, "player");
        UUID id = resolve(arg);
        if (id == null) {
            say(sender, "No such player or UUID: " + arg);
            return Command.SINGLE_SUCCESS;
        }
        action.run(sender, id);
        Player online = Bukkit.getPlayer(id);
        if (online != null) resync.accept(online);
        return Command.SINGLE_SUCCESS;
    }

    private static UUID resolve(String arg) {
        return Players.idOf(arg);
    }

    private static void say(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text));
    }
}

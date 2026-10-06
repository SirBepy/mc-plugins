package dev.sirbepy.mc.dragonballs.summon;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseListener;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseMath;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import dev.sirbepy.mc.dragonballs.summon.curse.EffectCurse;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /shenron}: the judge's interface to the active wish session plus curse management.
 * Restricted to the console specifically (not merely permission-gated like {@code /dragonballs}):
 * this is Player Claude's own tool for judging wishes, never a player's, even an op's, since a
 * player running it in-game would bypass the entire "the dragon speaks through console commands"
 * design the rulebook describes.
 */
public final class ShenronCommand {

    private final SummonSessionManager sessions;
    private final CurseStore curses;
    private final CurseListener curseListener;

    public ShenronCommand(SummonSessionManager sessions, CurseStore curses, CurseListener curseListener) {
        this.sessions = sessions;
        this.curses = curses;
        this.curseListener = curseListener;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("shenron")
                .requires(this::isJudge)
                .then(Commands.literal("say")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> reply(ctx, sessions.say(StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("grant")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .then(Commands.argument("note", StringArgumentType.greedyString())
                                        .executes(ctx -> reply(ctx, sessions.grant(
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "note")))))))
                .then(Commands.literal("dismiss")
                        .executes(ctx -> reply(ctx, sessions.dismiss(null)))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> reply(ctx, sessions.dismiss(StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("status")
                        .executes(ctx -> reply(ctx, sessions.statusText())))
                .then(Commands.literal("curse")
                        .then(Commands.literal("effect")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("effect", StringArgumentType.word())
                                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("hours", DoubleArgumentType.doubleArg(0.0001))
                                                                .executes(this::curseEffect))))))
                        .then(Commands.literal("nowish")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(this::curseNowish)))
                        .then(Commands.literal("lift")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(this::curseLift))))
                .build(), "Judge interface for the active Dragon Balls wish session");
    }

    private boolean isJudge(CommandSourceStack source) {
        return source.getSender() instanceof ConsoleCommandSender && source.getSender().hasPermission("dragonballs.admin");
    }

    private int reply(CommandContext<CommandSourceStack> ctx, String message) {
        if (message != null) {
            ctx.getSource().getSender().sendMessage(Component.text(message));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int curseEffect(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        String effectName = StringArgumentType.getString(ctx, "effect");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");
        double hours = DoubleArgumentType.getDouble(ctx, "hours");

        Optional<UUID> target = resolvePlayer(playerName);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text("No such player: " + playerName));
            return Command.SINGLE_SUCCESS;
        }
        PotionEffectType type = CurseListener.resolveType(effectName.toLowerCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(Component.text("Unknown effect: " + effectName));
            return Command.SINGLE_SUCCESS;
        }
        long expiry = CurseMath.expiryFromHours(System.currentTimeMillis(), hours);
        curses.addEffect(target.get(), type.getKey().toString(), amplifier, expiry);
        Player online = Bukkit.getPlayer(target.get());
        if (online != null) {
            curseListener.applyNow(online);
        }
        sender.sendMessage(Component.text("Cursed " + playerName + " with " + effectName + " (amplifier " + amplifier
                + ") for " + hours + "h."));
        return Command.SINGLE_SUCCESS;
    }

    private int curseNowish(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = resolvePlayer(playerName);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text("No such player: " + playerName));
            return Command.SINGLE_SUCCESS;
        }
        curses.banFromWishing(target.get());
        sender.sendMessage(Component.text(playerName + " can never summon the dragon again."));
        return Command.SINGLE_SUCCESS;
    }

    private int curseLift(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        Optional<UUID> target = resolvePlayer(playerName);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text("No such player: " + playerName));
            return Command.SINGLE_SUCCESS;
        }
        UUID targetId = target.get();
        List<EffectCurse> previous = curses.activeEffects(targetId, System.currentTimeMillis());
        curses.liftAll(targetId);
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            for (EffectCurse curse : previous) {
                PotionEffectType type = CurseListener.resolveType(curse.effectKey());
                if (type != null) {
                    online.removePotionEffect(type);
                }
            }
        }
        sender.sendMessage(Component.text("Lifted all curses from " + playerName + "."));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<UUID> resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? Optional.empty() : Optional.of(cached.getUniqueId());
    }
}

package dev.sirbepy.mc.dragonballs.summon;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseListener;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseMath;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import dev.sirbepy.mc.dragonballs.summon.curse.EffectCurse;
import dev.sirbepy.mc.dragonballs.summon.wish.WishJsonWriter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /shenron}: the judge's interface to the active wish session plus curse management, plus
 * {@code summon}, a debug-only trigger for bots/scripts. The judge subcommands ({@code say},
 * {@code grant}, {@code dismiss}, {@code status}, {@code curse}) are restricted to the console
 * specifically (not merely permission-gated like {@code /dragonballs}): this is Player Claude's own
 * tool for judging wishes, never a player's, even an op's, since a player running it in-game would
 * bypass the entire "the dragon speaks through console commands" design the rulebook describes.
 * {@code summon} is deliberately NOT console-restricted the same way: Brigadier's {@code .requires}
 * is checked top-down (confirmed empirically - a console-only top-level node hides every child from
 * a non-console sender, including RCON, before the dispatcher even looks at which child was typed),
 * so the per-command {@code .requires} below sits on each judge subcommand individually rather than
 * on the shared {@code shenron} literal; {@code summon} only needs the plain admin permission
 * check, since unlike the judge commands it can never bypass a rule - it re-validates every trigger
 * condition through {@link SummonSessionManager#tryStart} exactly as a real dialog click would, and
 * is the only path an RCON-driven e2e bot has to start a session at all (it can't answer the dialog).
 */
public final class ShenronCommand {

    private final SummonSessionManager sessions;
    private final CurseStore curses;
    private final CurseListener curseListener;
    private final BallSets sets;

    public ShenronCommand(SummonSessionManager sessions, CurseStore curses, CurseListener curseListener,
                           BallSets sets) {
        this.sessions = sessions;
        this.curses = curses;
        this.curseListener = curseListener;
        this.sets = sets;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("shenron")
                .requires(this::isAdmin)
                .then(Commands.literal("say")
                        .requires(this::isJudge)
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> reply(ctx, sessions.say(StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("grant")
                        .requires(this::isJudge)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .then(Commands.argument("note", StringArgumentType.greedyString())
                                        .executes(ctx -> reply(ctx, sessions.grant(
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "note")))))))
                .then(Commands.literal("public")
                        .requires(this::isJudge)
                        .then(Commands.argument("session", StringArgumentType.word())
                                .then(Commands.argument("titleAndLine", StringArgumentType.greedyString())
                                        .executes(ctx -> publicize(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "session"),
                                                StringArgumentType.getString(ctx, "titleAndLine"))))))
                .then(Commands.literal("dismiss")
                        .requires(this::isJudge)
                        .executes(ctx -> reply(ctx, sessions.dismiss(null)))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> reply(ctx, sessions.dismiss(StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("status")
                        .requires(this::isJudge)
                        .executes(ctx -> reply(ctx, sessions.statusText())))
                .then(Commands.literal("summon")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("dragon", StringArgumentType.word())
                                        .executes(this::summon)
                                        .then(Commands.argument("set", StringArgumentType.word())
                                                .executes(this::summonWithSet)))))
                .then(Commands.literal("curse")
                        .requires(this::isJudge)
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

    /** Gate for the shared {@code shenron} literal itself: permission only, so an op can reach
     * {@code summon} - each judge subcommand re-checks {@link #isJudge} on top of this. */
    private boolean isAdmin(CommandSourceStack source) {
        return source.getSender().hasPermission("dragonballs.admin");
    }

    private boolean isJudge(CommandSourceStack source) {
        return isJudge(source.getSender());
    }

    /** The server console or RCON, never a player, op or not: speaking as the dragon is the judge's job. */
    static boolean isJudge(CommandSender sender) {
        return (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)
                && sender.hasPermission("dragonballs.admin");
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

    private static final int MAX_PUBLIC_TITLE_CHARS = 60;
    private static final int MAX_PUBLIC_LINE_CHARS = 200;

    /**
     * {@code /shenron public <session> <title> | <line>}: appends a player-safe {@code "public"}
     * record to {@code wishes.jsonl} for a session that already has a {@code "grant"} record, so the
     * public website can show the wish without ever reading a judge's internal grant note. The
     * session need not be the active one - backfilling an old, already-ended session is the main
     * first use. Running this again for the same session appends a newer {@code "public"} record;
     * see {@link WishJsonWriter} for the "latest wins" rule.
     */
    int publicize(CommandSender sender, String session, String titleAndLine) {
        int sep = titleAndLine.indexOf(" | ");
        if (sep < 0) {
            sender.sendMessage(Component.text("Usage: /shenron public <session> <title> | <line>"));
            return Command.SINGLE_SUCCESS;
        }
        String title = titleAndLine.substring(0, sep).trim();
        String line = titleAndLine.substring(sep + 3).trim();
        if (title.isEmpty() || line.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /shenron public <session> <title> | <line>"));
            return Command.SINGLE_SUCCESS;
        }
        if (title.length() > MAX_PUBLIC_TITLE_CHARS) {
            sender.sendMessage(Component.text("Title is too long (max " + MAX_PUBLIC_TITLE_CHARS + " chars)."));
            return Command.SINGLE_SUCCESS;
        }
        if (line.length() > MAX_PUBLIC_LINE_CHARS) {
            sender.sendMessage(Component.text("Line is too long (max " + MAX_PUBLIC_LINE_CHARS + " chars)."));
            return Command.SINGLE_SUCCESS;
        }
        if (!hasGrant(session)) {
            sender.sendMessage(Component.text("no grant logged for session " + session));
            return Command.SINGLE_SUCCESS;
        }
        String json = WishJsonWriter.publicLine(Instant.now(), session, title, line);
        try {
            WishJsonWriter.append(sessions.wishesFile(), json);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Failed to write public record: " + e.getMessage()));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("Published session " + session + ": \"" + title + "\" | \"" + line + "\""));
        return Command.SINGLE_SUCCESS;
    }

    /** Whether {@code wishes.jsonl} already holds a {@code "type":"grant"} record for {@code
     * session}; field order is fixed by {@link WishJsonWriter#grantedLine}, so a plain substring
     * check is enough without a JSON parser dependency. */
    private boolean hasGrant(String session) {
        File file = sessions.wishesFile();
        if (!file.isFile()) {
            return false;
        }
        try {
            for (String recordedLine : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (recordedLine.contains("\"type\":\"grant\"") && recordedLine.contains("\"session\":\"" + session + "\"")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private int summon(CommandContext<CommandSourceStack> ctx) {
        return summon(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"),
                StringArgumentType.getString(ctx, "dragon"));
    }

    private int summonWithSet(CommandContext<CommandSourceStack> ctx) {
        return summon(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"),
                StringArgumentType.getString(ctx, "dragon"), StringArgumentType.getString(ctx, "set"));
    }

    /** No set named: picks the first (overworld, nether, end order) whose 7 balls are ready for
     * {@code target}, same as {@link #summon(CommandSender, String, String, String)} would validate
     * anyway - this just chooses which one to try. */
    int summon(CommandSender sender, String playerName, String dragonArg) {
        return summon(sender, playerName, dragonArg, null);
    }

    /**
     * Debug-only path for bots/scripts that can't answer the real right-click dialog: resolves the
     * same inputs a dialog click would (an online player, a dragon choice, a set, and one placed
     * ball's location) and calls {@link SummonSessionManager#tryStart} with them, so every trigger
     * condition (complete set, not dormant, no active session, not wish-banned) is re-validated
     * exactly as it is for a real click - this never bypasses a rule, it only skips the UI.
     */
    int summon(CommandSender sender, String playerName, String dragonArg, String setArg) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Component.text(playerName + " is not online."));
            return Command.SINGLE_SUCCESS;
        }
        DragonType dragon = resolveDragon(dragonArg);
        if (dragon == null) {
            sender.sendMessage(Component.text("Unknown dragon: " + dragonArg + " (use shenron or porunga)."));
            return Command.SINGLE_SUCCESS;
        }
        BallSet set;
        if (setArg == null) {
            // Prefer a genuinely ready set (the spec case); short of that, fall back to whichever
            // set has anything placed at all, so the refusal below still names a real reason
            // (dormant/incomplete/session-active) instead of a blanket "nothing is ready" - same
            // wording this command gave before the 3-set split, when overworld was the only set.
            set = firstReadySet(target);
            if (set == null) {
                set = firstSetWithAnyPlacedBall();
            }
            if (set == null) {
                set = BallSet.OVERWORLD;
            }
        } else {
            set = BallSet.byId(setArg.toLowerCase(Locale.ROOT));
            if (set == null) {
                sender.sendMessage(Component.text("Unknown set: " + setArg + " (use overworld, nether or end)."));
                return Command.SINGLE_SUCCESS;
            }
        }
        Location ballLocation = anyPlacedBallLocation(set);
        if (ballLocation == null) {
            sender.sendMessage(Component.text("No placed Dragon Ball to summon from."));
            return Command.SINGLE_SUCCESS;
        }
        boolean started = sessions.tryStart(target, dragon, ballLocation, set);
        if (!started) {
            sender.sendMessage(Component.text("Cannot summon: the 7 balls are not all placed together "
                    + "within range, the balls are dormant, a session is already active, or " + playerName
                    + " is wish-banned."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static DragonType resolveDragon(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "shenron" -> DragonType.SHENRON;
            case "porunga" -> DragonType.PORUNGA;
            default -> null;
        };
    }

    /** The first set (declaration order: overworld, nether, end) whose 7 balls are placed together
     * and ready for {@code target} to summon from; null if none are. */
    private BallSet firstReadySet(Player target) {
        for (BallSet set : BallSet.values()) {
            if (!sets.isEnabled(set)) {
                continue;
            }
            Location loc = anyPlacedBallLocation(set);
            if (loc == null) {
                continue;
            }
            if (sessions.evaluate(target.getUniqueId(), loc, set) == SummonTrigger.Readiness.READY) {
                return set;
            }
        }
        return null;
    }

    /** The first set (declaration order) with any ball at all PLACED somewhere, ready or not; used
     * only as the fallback anchor once no set is actually ready, so the final refusal message can
     * still name a specific reason. */
    private BallSet firstSetWithAnyPlacedBall() {
        for (BallSet set : BallSet.values()) {
            if (sets.isEnabled(set) && anyPlacedBallLocation(set) != null) {
                return set;
            }
        }
        return null;
    }

    /** Any one PLACED ball's current block location for {@code set}, to stand in for the block a
     * real right-click would hand {@link SummonSessionManager#tryStart}; which of the 7 doesn't
     * matter, since readiness requires all 7 within radius of whichever one is clicked. */
    private Location anyPlacedBallLocation(BallSet set) {
        for (BallView ball : sets.registry(set).getBalls()) {
            if (ball.state() == BallState.PLACED) {
                Location loc = ball.lastKnownLocation();
                if (loc != null) {
                    return loc;
                }
            }
        }
        return null;
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

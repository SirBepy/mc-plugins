package dev.sirbepy.mc.guild.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.FirstDragon;
import dev.sirbepy.mc.guild.listener.ExploreTracker;
import dev.sirbepy.mc.guild.rank.Rank;
import dev.sirbepy.mc.guild.staff.StaffNpcs;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * {@code /guildadmin}, ops/console/RCON only: inspect and repair members, the board and the Treasury.
 * For testing and fixing mistakes; normal play never needs it, and the Guildmaster has no access.
 */
public final class GuildAdminCommand {

    private final Guild guild;
    private final Consumer<String> setGuildmaster;
    private final Runnable reload;
    private final StaffAdminCommand staffAdmin;

    public GuildAdminCommand(Guild guild, Consumer<String> setGuildmaster, Runnable reload, StaffNpcs staff) {
        this.guild = guild;
        this.setGuildmaster = setGuildmaster;
        this.reload = reload;
        this.staffAdmin = new StaffAdminCommand(guild, staff);
    }

    public void registerInto(Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("guildadmin")
                .requires(src -> src.getSender().hasPermission("guild.admin"))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("member").then(player().executes(this::member)))
                .then(Commands.literal("rep").then(player()
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(this::rep))))
                .then(Commands.literal("contracts").then(player()
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(this::contracts))))
                .then(Commands.literal("newday").then(player().executes(this::newDay)))
                .then(Commands.literal("newweek").then(player().executes(this::newWeek)))
                .then(Commands.literal("setrank").then(player()
                        .then(Commands.argument("rank", StringArgumentType.word()).executes(this::setRank))))
                .then(Commands.literal("resettrial").then(player().executes(this::resetTrial)))
                .then(Commands.literal("complete").then(player()
                        .then(Commands.argument("slot", StringArgumentType.word()).executes(this::complete))))
                .then(Commands.literal("resetannounce").executes(c -> {
                    guild.store().setLastAnnouncement(0);
                    guild.store().save();
                    send(c, "The Guildmaster may announce again now.");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("grandquest")
                        .then(Commands.literal("reroll").executes(this::rerollGrand)))
                .then(Commands.literal("firstdragon").executes(this::firstDragon)
                        .then(Commands.literal("reset").executes(this::resetFirstDragon)))
                .then(Commands.literal("treasury")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(this::treasury)))
                .then(Commands.literal("probe")
                        .then(Commands.argument("world", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(this::probe))))))
                .then(Commands.literal("guildmaster")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::guildmaster)))
                .then(Commands.literal("reload").executes(c -> {
                    reload.run();
                    send(c, "Guild config reloaded.");
                    return Command.SINGLE_SUCCESS;
                }));
        registrar.register(staffAdmin.attach(root).build(), "Inspect and repair the Adventurer's Guild (ops)");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word());
    }

    private int status(CommandContext<CommandSourceStack> c) {
        var treasury = guild.store().treasury();
        send(c, "Members " + guild.store().members().size() + ", treasury " + treasury.balance() + " (in "
                + treasury.totalIn() + ", out " + treasury.totalOut() + "), guildmaster may draw " + guild.office().withdrawable()
                + " today, commissions " + guild.store().commissions().size() + " (" + guild.office().commissionsThisWeek()
                + " this week), featured " + guild.store().featuredId() + ", bank " + (guild.bank().available() ? "up" : "DOWN"));
        return Command.SINGLE_SUCCESS;
    }

    private int member(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m == null) {
            return Command.SINGLE_SUCCESS;
        }
        guild.refresh(m);
        send(c, m.name() + " (" + m.id() + "): " + m.rank() + ", rep " + m.reputation() + ", contracts " + m.contractsDone()
                + ", today " + m.coinsToday() + " coins / " + m.levelsToday() + " levels, rerolls used " + m.rerollsUsed());
        StringBuilder board = new StringBuilder("Board " + m.boardDay() + ":");
        for (ActiveContract daily : m.dailies()) {
            board.append(' ').append(describe(daily));
        }
        board.append(" | weekly ").append(m.boardWeek()).append(' ').append(m.weekly() == null ? "none" : describe(m.weekly()));
        send(c, board.toString());
        send(c, "Trial: " + (m.trial() == null ? "none" : describe(m.trial())) + ", retry after " + m.trialRetryAfter()
                + ", qualifies " + Guild.qualifies(m) + ", stats " + m.stats());
        return Command.SINGLE_SUCCESS;
    }

    private static String describe(ActiveContract a) {
        StringBuilder out = new StringBuilder(a.defId()).append('[');
        int[] progress = a.progress();
        for (int i = 0; i < progress.length; i++) {
            out.append(i == 0 ? "" : ",").append(progress[i]);
        }
        return out.append(']').append(a.claimed() ? "(claimed)" : "").toString();
    }

    private int rep(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m != null) {
            guild.addReputation(Bukkit.getPlayer(m.id()), m, IntegerArgumentType.getInteger(c, "amount"));
            guild.store().save();
            send(c, m.name() + " reputation: " + m.reputation());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int contracts(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m != null) {
            m.setContractsDone(Math.max(0, m.contractsDone() + IntegerArgumentType.getInteger(c, "amount")));
            guild.addReputation(Bukkit.getPlayer(m.id()), m, 0);
            guild.store().save();
            send(c, m.name() + " contracts: " + m.contractsDone());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int newDay(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m != null) {
            m.setBoardDay(null);
            m.setEarnDay(null);
            guild.refresh(m);
            guild.store().save();
            send(c, m.name() + " has a fresh board and fresh daily limits.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int newWeek(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m != null) {
            m.setBoardWeek(null);
            guild.refresh(m);
            guild.store().save();
            send(c, m.name() + " has a fresh weekly: " + (m.weekly() == null ? "none" : m.weekly().defId()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int setRank(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        Rank rank = Rank.parse(StringArgumentType.getString(c, "rank"), null);
        if (m != null && rank == null) {
            send(c, "Rank must be one of E, D, C, B, A, S.");
        } else if (m != null) {
            m.setRank(rank);
            m.setTrial(null);
            guild.store().save();
            send(c, m.name() + " is now " + rank.title() + " (repair only; no announcement).");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int resetTrial(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m != null) {
            m.setTrial(null);
            m.setTrialRetryAfter(0);
            guild.store().save();
            send(c, m.name() + "'s trial is cleared and can be started again.");
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Marks a board contract's tracked objectives done (daily slot 0-4, "weekly" or "trial"), for testing claims. */
    private int complete(CommandContext<CommandSourceStack> c) {
        Member m = target(c);
        if (m == null) {
            return Command.SINGLE_SUCCESS;
        }
        guild.refresh(m);
        String slot = StringArgumentType.getString(c, "slot");
        ActiveContract active = switch (slot) {
            case "weekly" -> m.weekly();
            case "trial" -> m.trial();
            case "0", "1", "2", "3", "4" -> Integer.parseInt(slot) < m.dailies().size()
                    ? m.dailies().get(Integer.parseInt(slot)) : null;
            default -> null;
        };
        if (active == null) {
            send(c, "Slot must be a daily 0-4, weekly or trial (with one active).");
            return Command.SINGLE_SUCCESS;
        }
        ContractDef def = active.def();
        for (int i = 0; i < def.objectives().size(); i++) {
            active.setProgress(i, def.objectives().get(i).amount());
        }
        guild.store().save();
        send(c, m.name() + "'s " + active.defId() + " is complete apart from any hand-ins.");
        return Command.SINGLE_SUCCESS;
    }

    private int rerollGrand(CommandContext<CommandSourceStack> c) {
        var events = guild.events();
        var old = events.grandQuest();
        String before = old == null ? "none" : old.id() + " (" + events.grandTotal() + "/" + old.goal()
                + (events.grandDone() ? ", done" : "") + ")";
        var next = events.rerollGrandQuest();
        guild.store().save();
        send(c, "Voided Grand Quest " + before + "; this week's is now " + next.id() + ": " + next.title()
                + ", goal " + next.goal() + ". Shares already claimed stay paid.");
        return Command.SINGLE_SUCCESS;
    }

    private int firstDragon(CommandContext<CommandSourceStack> c) {
        send(c, FirstDragon.describe(guild.store(), guild.config().zone()));
        return Command.SINGLE_SUCCESS;
    }

    private int resetFirstDragon(CommandContext<CommandSourceStack> c) {
        FirstDragon.reset(guild.store());
        send(c, "First Dragon record cleared: the next dragon to die in the End pays its Guild fighters again."
                + " Coins, reputation and titles already given stay given.");
        return Command.SINGLE_SUCCESS;
    }

    private int treasury(CommandContext<CommandSourceStack> c) {
        int amount = IntegerArgumentType.getInteger(c, "amount");
        guild.store().treasury().record(guild.clock().nowMillis(), LedgerEntry.Kind.ADJUST, amount,
                c.getSource().getSender().getName());
        guild.store().save();
        send(c, "Treasury: " + guild.store().treasury().balance());
        return Command.SINGLE_SUCCESS;
    }

    /** What exploration contracts would see at a spot: its biome key and the structures it stands inside. */
    private int probe(CommandContext<CommandSourceStack> c) {
        World world = Bukkit.getWorld(StringArgumentType.getString(c, "world"));
        if (world == null) {
            send(c, "No loaded world by that name.");
            return Command.SINGLE_SUCCESS;
        }
        Location at = new Location(world, IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"),
                IntegerArgumentType.getInteger(c, "z"));
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            send(c, "That chunk isn't loaded; forceload it first.");
            return Command.SINGLE_SUCCESS;
        }
        send(c, "Biome " + ExploreTracker.biomeKey(at) + ", inside " + ExploreTracker.structuresAt(at)
                + ", in this chunk " + ExploreTracker.structuresInChunk(at));
        return Command.SINGLE_SUCCESS;
    }

    private int guildmaster(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            send(c, name + " has never joined this server.");
            return Command.SINGLE_SUCCESS;
        }
        setGuildmaster.accept(target.getUniqueId().toString());
        send(c, "The Guildmaster is now " + name + " (" + target.getUniqueId() + ").");
        return Command.SINGLE_SUCCESS;
    }

    private Member target(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        Member m = GuildCommand.find(guild, name);
        if (m == null) {
            send(c, name + " isn't a Guild member.");
        }
        return m;
    }

    private static void send(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().getSender().sendMessage(Component.text(text));
    }
}

package dev.sirbepy.mc.haru.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.core.Players;
import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.NpcAnchor;
import dev.sirbepy.mc.haru.data.OwnerState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * {@code /haru}, ops and console only: place and inspect Haru, and adjust the owner's petals and
 * reputation (for reverting or testing; normal play never needs it).
 */
public final class HaruCommand {

    private final HaruContext ctx;
    private final Consumer<String> setOwner;
    private final Runnable reload;

    public HaruCommand(HaruContext ctx, Consumer<String> setOwner, Runnable reload) {
        this.ctx = ctx;
        this.setOwner = setOwner;
        this.reload = reload;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("haru")
                .requires(src -> src.getSender().hasPermission("haru.admin"))
                .then(Commands.literal("place")
                        .executes(this::placeHere)
                        .then(Commands.argument("world", StringArgumentType.word())
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                                                .executes(this::placeAt)))))))
                .then(Commands.literal("remove").executes(this::remove))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("owner")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::owner)))
                .then(Commands.literal("petals")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(this::petals)))
                .then(Commands.literal("rep")
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(this::rep)))
                .then(Commands.literal("newday").executes(this::newDay))
                .then(Commands.literal("unbound")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(this::unbound)))
                .then(Commands.literal("reload").executes(ctx -> {
                    reload.run();
                    send(ctx, "Haru's config reloaded.");
                    return Command.SINGLE_SUCCESS;
                }))
                .build(), "Manage Haru, Keeper of the Sakura Sanctuary");
    }

    private int placeHere(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getSender() instanceof Player player)) {
            send(c, "From the console: haru place <world> <x> <y> <z> <yaw>");
            return Command.SINGLE_SUCCESS;
        }
        ctx.npc().place(player.getLocation());
        send(c, "Haru now stands at " + describe(ctx.store().anchor()) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int placeAt(CommandContext<CommandSourceStack> c) {
        World world = Bukkit.getWorld(StringArgumentType.getString(c, "world"));
        if (world == null) {
            send(c, "No loaded world by that name.");
            return Command.SINGLE_SUCCESS;
        }
        Location at = new Location(world, DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"),
                DoubleArgumentType.getDouble(c, "z"), FloatArgumentType.getFloat(c, "yaw"), 0);
        ctx.npc().place(at);
        send(c, "Haru now stands at " + describe(ctx.store().anchor()) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> c) {
        ctx.npc().forget();
        send(c, "Haru is gone until he is placed again.");
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> c) {
        OwnerState state = ctx.ownerState();
        send(c, "Anchor: " + (ctx.store().anchor() == null ? "not placed" : describe(ctx.store().anchor()))
                + ", villager " + (ctx.npc().villager() == null ? "not loaded" : "present")
                + ", mannequin " + (ctx.npc().mannequin() == null ? "not loaded" : "present"));
        send(c, "Owner: " + ctx.ownerName() + " (" + ctx.config().owner() + ")");
        send(c, "Petals " + state.petals() + ", reputation " + state.reputation() + " (" + state.tier().title()
                + "), requests done " + state.questsCompleted());
        StringBuilder board = new StringBuilder("Board " + state.boardDate() + ":");
        for (ActiveQuest quest : state.board()) {
            board.append(' ').append(quest.questId()).append('=').append(quest.progress())
                    .append(quest.claimed() ? "(claimed)" : "");
        }
        send(c, board.toString());
        send(c, "Rotation " + ctx.shop().rotation() + ", legendary: "
                + ctx.shop().legendary().map(o -> o.id()).orElse("none") + ", unlocks: " + state.unlocks());
        return Command.SINGLE_SUCCESS;
    }

    private int owner(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        OfflinePlayer target = Players.known(name).orElse(null);
        if (target == null) {
            send(c, name + " has never joined this server.");
            return Command.SINGLE_SUCCESS;
        }
        setOwner.accept(target.getUniqueId().toString());
        send(c, "Haru now serves " + name + " (" + target.getUniqueId() + ").");
        return Command.SINGLE_SUCCESS;
    }

    private int petals(CommandContext<CommandSourceStack> c) {
        OwnerState state = ctx.ownerState();
        state.addPetals(IntegerArgumentType.getInteger(c, "amount"));
        ctx.store().save();
        send(c, "Spirit Petals: " + state.petals());
        return Command.SINGLE_SUCCESS;
    }

    private int rep(CommandContext<CommandSourceStack> c) {
        OwnerState state = ctx.ownerState();
        int amount = IntegerArgumentType.getInteger(c, "amount");
        Player owner = Bukkit.getPlayer(ctx.config().owner());
        if (owner != null && amount > 0) {
            ctx.progression().addReputation(owner, state, amount);
        } else {
            state.setReputation(state.reputation() + amount);
        }
        ctx.store().save();
        send(c, "Reputation: " + state.reputation() + " (" + state.tier().title() + ")");
        return Command.SINGLE_SUCCESS;
    }

    /** Makes today's quest board and curio limit fresh again, for testing. */
    private int newDay(CommandContext<CommandSourceStack> c) {
        OwnerState state = ctx.store().state(ctx.config().owner());
        state.setBoardDate(null);
        state.purchases().remove("curio");
        ctx.ownerState();
        ctx.store().save();
        send(c, "A new day for Haru's owner: requests and curios are fresh.");
        return Command.SINGLE_SUCCESS;
    }

    /** Toggles whether Haru hands over rewards bound to his owner at all. */
    private int unbound(CommandContext<CommandSourceStack> c) {
        OwnerState state = ctx.ownerState();
        state.setRewardsUnbound(BoolArgumentType.getBool(c, "value"));
        ctx.store().save();
        send(c, "Haru's rewards are now " + (state.rewardsUnbound() ? "unbound" : "bound to their owner") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static String describe(NpcAnchor anchor) {
        return anchor.world() + " " + anchor.x() + " " + anchor.y() + " " + anchor.z() + " yaw " + anchor.yaw();
    }

    private static void send(CommandContext<CommandSourceStack> c, String text) {
        CommandSender sender = c.getSource().getSender();
        sender.sendMessage(Component.text(text));
    }
}

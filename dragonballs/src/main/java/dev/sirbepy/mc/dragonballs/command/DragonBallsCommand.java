package dev.sirbepy.mc.dragonballs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/**
 * {@code /dragonballs status|scatter|give|wake|reload}, console+op only. Deliberately thin: every
 * branch just calls straight into the right set's {@link BallRegistry}, with teardown-then-give
 * logic for the one command ({@code give}) that needs to physically relocate a ball. {@code scatter}
 * and {@code give} default to the overworld set when no set is named, matching their pre-3-set-split
 * argument order (e.g. {@code scatter stone} still means overworld stone); {@code wake} with no set
 * named wakes all 3.
 */
public final class DragonBallsCommand {

    private final BallSets sets;
    private final Supplier<DragonBallsConfig> config;
    private final Runnable reload;

    public DragonBallsCommand(BallSets sets, Supplier<DragonBallsConfig> config, Runnable reload) {
        this.sets = sets;
        this.config = config;
        this.reload = reload;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("dragonballs")
                .requires(src -> src.getSender().hasPermission("dragonballs.admin"))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("scatter")
                        .executes(ctx -> scatter(ctx.getSource(), BallSet.OVERWORLD, false))
                        .then(Commands.literal("stone").executes(ctx -> scatter(ctx.getSource(), BallSet.OVERWORLD, true)))
                        .then(scatterSetNode(BallSet.NETHER))
                        .then(scatterSetNode(BallSet.END)))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("star", IntegerArgumentType.integer(1, 7))
                                        .executes(ctx -> give(ctx, BallSet.OVERWORLD))
                                        .then(Commands.literal("overworld").executes(ctx -> give(ctx, BallSet.OVERWORLD)))
                                        .then(Commands.literal("nether").executes(ctx -> give(ctx, BallSet.NETHER)))
                                        .then(Commands.literal("end").executes(ctx -> give(ctx, BallSet.END))))))
                .then(Commands.literal("wake")
                        .executes(this::wakeAll)
                        .then(Commands.literal("overworld").executes(ctx -> wake(ctx, BallSet.OVERWORLD)))
                        .then(Commands.literal("nether").executes(ctx -> wake(ctx, BallSet.NETHER)))
                        .then(Commands.literal("end").executes(ctx -> wake(ctx, BallSet.END))))
                .then(Commands.literal("reload").executes(this::reload))
                .build(), "Manage the Dragon Ball hunt");
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> scatterSetNode(BallSet set) {
        return Commands.literal(set.id())
                .executes(ctx -> scatter(ctx.getSource(), set, false))
                .then(Commands.literal("stone").executes(ctx -> scatter(ctx.getSource(), set, true)));
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        for (BallSet set : BallSet.values()) {
            sender.sendMessage(Component.text(header(set)));
            if (!sets.isEnabled(set)) {
                sender.sendMessage(Component.text("  dormant until the End opens"));
                continue;
            }
            for (BallView view : sets.registry(set).getBalls()) {
                sender.sendMessage(Component.text(describe(view)));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String header(BallSet set) {
        return switch (set) {
            case OVERWORLD -> "-- Overworld --";
            case NETHER -> "-- Nether --";
            case END -> "-- End --";
        };
    }

    private String describe(BallView view) {
        String where = switch (view.state()) {
            case PLACED -> "placed at " + view.worldName() + " " + (int) view.x() + "," + (int) view.y() + "," + (int) view.z();
            case HELD -> "held by " + view.holderName();
            case DROPPED -> "dropped at " + view.worldName() + " " + (int) view.x() + "," + (int) view.y() + "," + (int) view.z();
        };
        return view.star() + "-star: " + where + (view.stone() ? " (stone)" : "");
    }

    /** Ends the stone cooldown early for all 3 sets, e.g. after a test summon on a live world. */
    private int wakeAll(CommandContext<CommandSourceStack> ctx) {
        for (BallRegistry registry : sets.all()) {
            registry.clearStone();
        }
        ctx.getSource().getSender().sendMessage(Component.text("The Dragon Balls are awake."));
        return Command.SINGLE_SUCCESS;
    }

    private int wake(CommandContext<CommandSourceStack> ctx, BallSet set) {
        sets.registry(set).clearStone();
        String word = set.displayWord().isEmpty() ? "" : set.displayWord() + " ";
        ctx.getSource().getSender().sendMessage(Component.text("The " + word + "Dragon Balls are awake."));
        return Command.SINGLE_SUCCESS;
    }

    private int scatter(CommandSourceStack source, BallSet set, boolean stone) {
        return scatter(source.getSender(), set, stone);
    }

    int scatter(CommandSender sender, BallSet set, boolean stone) {
        if (!sets.isEnabled(set)) {
            sender.sendMessage(Component.text("The End hasn't opened yet."));
            return Command.SINGLE_SUCCESS;
        }
        BallRegistry registry = sets.registry(set);
        Location center = registry.lastSummon();
        if (center == null) {
            sender.sendMessage(Component.text("No last-summon location yet; its world hasn't loaded."));
            return Command.SINGLE_SUCCESS;
        }
        // The scatter only moves the registry's record, so the old heads (and carried copies) have to
        // go first: a live re-scatter on 2026-10-07 left 2 duplicate Nether heads behind.
        for (BallView view : registry.getBalls()) {
            teardown(registry, view);
        }
        registry.scatterAll(center, stone);
        config.get().messages().send(sender, "scatter-success");
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx, BallSet set) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        int star = IntegerArgumentType.getInteger(ctx, "star");
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Component.text(playerName + " is not online."));
            return Command.SINGLE_SUCCESS;
        }
        if (!sets.isEnabled(set)) {
            sender.sendMessage(Component.text("The End hasn't opened yet."));
            return Command.SINGLE_SUCCESS;
        }
        BallRegistry registry = sets.registry(set);
        BallView view = registry.get(star).orElseThrow();
        teardown(registry, view);
        var leftover = target.getInventory().addItem(sets.items().itemFor(set, star, view.stone()));
        if (!leftover.isEmpty()) {
            Item dropped = target.getWorld().dropItem(target.getLocation(), leftover.values().iterator().next());
            registry.markDropped(star, dropped, view.stone());
        } else {
            registry.markHeld(star, target, view.stone());
        }
        config.get().messages().send(sender, "give-success",
                Placeholder.unparsed("star", String.valueOf(star)),
                Placeholder.unparsed("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    /** Removes a ball from wherever it currently is (block or world item), before /give relocates it. */
    private void teardown(BallRegistry registry, BallView view) {
        switch (view.state()) {
            case PLACED -> {
                Location loc = view.lastKnownLocation();
                if (loc != null) {
                    Block block = loc.getBlock();
                    if (block.getState() instanceof Skull) {
                        block.setType(org.bukkit.Material.AIR);
                    }
                }
            }
            case DROPPED -> {
                if (view.entityId() != null && Bukkit.getEntity(view.entityId()) instanceof Item item) {
                    item.remove();
                }
            }
            case HELD -> {
                Player holder = view.holderId() == null ? null : Bukkit.getPlayer(view.holderId());
                if (holder != null) {
                    holder.getInventory().remove(sets.items().itemFor(registry.set(), view.star(), view.stone()));
                }
            }
        }
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        reload.run();
        config.get().messages().send(ctx.getSource().getSender(), "reload-success");
        return Command.SINGLE_SUCCESS;
    }
}

package dev.sirbepy.mc.dragonballs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
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
 * {@code /dragonballs status|scatter|give|reload}, console+op only. Deliberately thin: every
 * branch just calls straight into {@link BallRegistry}, with teardown-then-give logic for the one
 * command ({@code give}) that needs to physically relocate a ball.
 */
public final class DragonBallsCommand {

    private final BallRegistry registry;
    private final Supplier<DragonBallsConfig> config;
    private final Runnable reload;

    public DragonBallsCommand(BallRegistry registry, Supplier<DragonBallsConfig> config, Runnable reload) {
        this.registry = registry;
        this.config = config;
        this.reload = reload;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("dragonballs")
                .requires(src -> src.getSender().hasPermission("dragonballs.admin"))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("scatter")
                        .executes(ctx -> scatter(ctx.getSource(), false))
                        .then(Commands.literal("stone").executes(ctx -> scatter(ctx.getSource(), true))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("star", IntegerArgumentType.integer(1, 7))
                                        .executes(this::give))))
                .then(Commands.literal("wake").executes(this::wake))
                .then(Commands.literal("reload").executes(this::reload))
                .build(), "Manage the Dragon Ball hunt");
    }

    private int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        for (BallView view : registry.getBalls()) {
            sender.sendMessage(Component.text(describe(view)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private String describe(BallView view) {
        String where = switch (view.state()) {
            case PLACED -> "placed at " + view.worldName() + " " + (int) view.x() + "," + (int) view.y() + "," + (int) view.z();
            case HELD -> "held by " + view.holderName();
            case DROPPED -> "dropped at " + view.worldName() + " " + (int) view.x() + "," + (int) view.y() + "," + (int) view.z();
        };
        return view.star() + "-star: " + where + (view.stone() ? " (stone)" : "");
    }

    /** Ends the stone cooldown early, e.g. after a test summon on a live world. */
    private int wake(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        registry.clearStone();
        ctx.getSource().getSender().sendMessage(Component.text("The Dragon Balls are awake."));
        return Command.SINGLE_SUCCESS;
    }

    private int scatter(CommandSourceStack source, boolean stone) {
        Location center = registry.lastSummon();
        if (center == null) {
            source.getSender().sendMessage(Component.text("No last-summon location yet; the overworld spawn hasn't loaded."));
            return Command.SINGLE_SUCCESS;
        }
        registry.scatterAll(center, stone);
        config.get().messages().send(source.getSender(), "scatter-success");
        return Command.SINGLE_SUCCESS;
    }

    private int give(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        int star = IntegerArgumentType.getInteger(ctx, "star");
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Component.text(playerName + " is not online."));
            return Command.SINGLE_SUCCESS;
        }
        BallView view = registry.get(star).orElseThrow();
        teardown(view);
        var leftover = target.getInventory().addItem(registry.items().itemFor(star, view.stone()));
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
    private void teardown(BallView view) {
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
                    holder.getInventory().remove(registry.items().itemFor(view.star(), view.stone()));
                }
            }
        }
    }

    private int reload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        reload.run();
        config.get().messages().send(ctx.getSource().getSender(), "reload-success");
        return Command.SINGLE_SUCCESS;
    }
}

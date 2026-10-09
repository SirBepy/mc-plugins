package dev.sirbepy.mc.endfight.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.ArenaService;
import dev.sirbepy.mc.endfight.reward.RewardService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** {@code /endfight status|build|rebuild|clear|reload|rewards}, console+op only. Thin: every
 * branch calls straight into {@link ArenaService} or {@link RewardService}. */
public final class EndFightCommand {

    private final ArenaService service;
    private final Supplier<EndFightConfig> config;
    private final Runnable reload;
    private final RewardService rewards;

    public EndFightCommand(ArenaService service, Supplier<EndFightConfig> config, Runnable reload, RewardService rewards) {
        this.service = service;
        this.config = config;
        this.reload = reload;
        this.rewards = rewards;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("endfight")
                .requires(src -> src.getSender().hasPermission("endfight.admin"))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("build").executes(this::build))
                .then(Commands.literal("rebuild").executes(this::build))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("rewards")
                        .then(Commands.literal("status").executes(this::rewardsStatus))
                        .then(Commands.literal("test")
                                .then(Commands.argument("player", StringArgumentType.word()).executes(this::rewardsTest))))
                .build(), "Manage the End Fight arena");
    }

    private int rewardsStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(Component.text(
                "Rewards pending: " + rewards.pendingCount() + ", current dragon fighters: " + rewards.currentFighterCount()));
        return Command.SINGLE_SUCCESS;
    }

    private int rewardsTest(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(name);
        CommandSender sender = ctx.getSource().getSender();
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline: " + name));
            return Command.SINGLE_SUCCESS;
        }
        rewards.testBundle(target);
        sender.sendMessage(Component.text("Gave " + target.getName() + " one test reward bundle."));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        ArenaRegistry registry = service.registry();
        ctx.getSource().getSender().sendMessage(Component.text(
                "Built: " + registry.isBuilt()
                        + ", pillars: " + registry.pillars().size()
                        + ", arena blocks: " + registry.blockCount()
                        + ", guard posts: " + registry.guardPosts().size()));
        return Command.SINGLE_SUCCESS;
    }

    private int build(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean ok = service.buildNow();
        if (ok) {
            config.get().messages().send(sender, "build-success",
                    Placeholder.unparsed("pillars", String.valueOf(service.registry().pillars().size())),
                    Placeholder.unparsed("blocks", String.valueOf(service.registry().blockCount())));
        } else {
            config.get().messages().send(sender, "build-failed");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean ok = service.clearNow();
        config.get().messages().send(sender, ok ? "clear-success" : "clear-failed");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        reload.run();
        config.get().messages().send(ctx.getSource().getSender(), "reload-success");
        return Command.SINGLE_SUCCESS;
    }
}

package dev.sirbepy.mc.endfight.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.ArenaService;
import dev.sirbepy.mc.endfight.cult.CultCommand;
import dev.sirbepy.mc.endfight.reset.ResetCommand;
import dev.sirbepy.mc.endfight.reward.RewardService;
import dev.sirbepy.mc.endfight.tier.Tier;
import dev.sirbepy.mc.endfight.tier.TierService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Supplier;

/** {@code /endfight status|build|rebuild|clear|reload|rewards|tier}, plus whatever
 * {@link CultCommand} attaches ({@code cult status|spawn|clear}), console+op only. Thin: every
 * branch calls straight into {@link ArenaService}, {@link RewardService} or {@link TierService}. */
public final class EndFightCommand {

    private final ArenaService service;
    private final Supplier<EndFightConfig> config;
    private final Runnable reload;
    private final RewardService rewards;
    private final TierService tiers;

    public EndFightCommand(ArenaService service, Supplier<EndFightConfig> config, Runnable reload, RewardService rewards,
                            TierService tiers) {
        this.service = service;
        this.config = config;
        this.reload = reload;
        this.rewards = rewards;
        this.tiers = tiers;
    }

    public void registerInto(Commands registrar, CultCommand cultCommand, ResetCommand resetCommand) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("endfight")
                .requires(src -> src.getSender().hasPermission("endfight.admin"))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("build").executes(this::build))
                .then(Commands.literal("rebuild").executes(this::build))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("rewards")
                        .then(Commands.literal("status").executes(this::rewardsStatus))
                        .then(Commands.literal("test")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(this::rewardsTest)
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .executes(this::rewardsTestWithTier)))))
                .then(Commands.literal("tier")
                        .then(Commands.literal("status").executes(this::tierStatus))
                        .then(Commands.literal("set")
                                .then(Commands.argument("tier", StringArgumentType.word()).executes(this::tierSet))));
        root = cultCommand.attach(root);
        root = resetCommand.attach(root);
        registrar.register(root.build(), "Manage the End Fight arena");
    }

    private int tierStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(Component.text(
                "Next: " + tiers.next().displayName() + ", current fight: " + tiers.current().displayName()
                        + ", first kill ever: " + tiers.firstKillEver()));
        return Command.SINGLE_SUCCESS;
    }

    private int tierSet(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String raw = StringArgumentType.getString(ctx, "tier");
        Tier tier;
        try {
            tier = Tier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Unknown tier: " + raw));
            return Command.SINGLE_SUCCESS;
        }
        if (tiers.firstKillEver()) {
            config.get().messages().send(sender, "tier-locked-first-kill");
            return Command.SINGLE_SUCCESS;
        }
        tiers.setNext(tier, sender instanceof Player player ? player : null);
        config.get().messages().send(sender, "tier-set", Placeholder.unparsed("tier", tier.displayName()));
        return Command.SINGLE_SUCCESS;
    }

    private int rewardsStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(Component.text(
                "Rewards pending: " + rewards.pendingCount() + ", current dragon fighters: " + rewards.currentFighterCount()
                        + ", current tier: " + rewards.currentTier().displayName()));
        return Command.SINGLE_SUCCESS;
    }

    private int rewardsTest(CommandContext<CommandSourceStack> ctx) {
        return rewardsTest(ctx, rewards.currentTier());
    }

    private int rewardsTestWithTier(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "tier");
        Tier tier = parseTier(raw);
        if (tier == null) {
            ctx.getSource().getSender().sendMessage(Component.text("Unknown tier: " + raw));
            return Command.SINGLE_SUCCESS;
        }
        return rewardsTest(ctx, tier);
    }

    private int rewardsTest(CommandContext<CommandSourceStack> ctx, Tier tier) {
        String name = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(name);
        CommandSender sender = ctx.getSource().getSender();
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline: " + name));
            return Command.SINGLE_SUCCESS;
        }
        rewards.testHoard(target, tier);
        sender.sendMessage(Component.text("Gave " + target.getName() + " one test Dragon Hoard (" + tier.displayName() + ")."));
        return Command.SINGLE_SUCCESS;
    }

    private static Tier parseTier(String raw) {
        try {
            return Tier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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

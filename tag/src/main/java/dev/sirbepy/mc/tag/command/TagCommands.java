package dev.sirbepy.mc.tag.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.TagPlugin;
import dev.sirbepy.mc.tag.TagService;
import dev.sirbepy.mc.tag.state.PlayerStats;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Registers every `/tag ...` subcommand through Brigadier (never plugin.yml, per the house rule). */
public final class TagCommands {

    private final TagPlugin plugin;

    public TagCommands(TagPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tag")
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission("tag.admin"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestKnownPlayers)
                                .executes(this::runSet)))
                .then(Commands.literal("clear")
                        .requires(src -> src.getSender().hasPermission("tag.admin"))
                        .executes(this::runClear))
                .then(Commands.literal("optout")
                        .requires(src -> src.getSender().hasPermission("tag.play"))
                        .executes(this::runOptOut))
                .then(Commands.literal("optin")
                        .requires(src -> src.getSender().hasPermission("tag.play"))
                        .executes(this::runOptIn))
                .then(Commands.literal("stats")
                        .requires(src -> src.getSender().hasPermission("tag.play"))
                        .executes(this::runStatsSelf)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestKnownPlayers)
                                .executes(this::runStatsOther)))
                .then(Commands.literal("top")
                        .requires(src -> src.getSender().hasPermission("tag.play"))
                        .executes(this::runTop))
                .then(Commands.literal("board")
                        .requires(src -> src.getSender().hasPermission("tag.play"))
                        .executes(this::runBoard))
                .then(Commands.literal("who")
                        .requires(src -> src.getSender().hasPermission("tag.play"))
                        .executes(this::runWho))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("tag.admin"))
                        .executes(this::runReload))
                .build();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKnownPlayers(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String name = op.getName();
            if (name != null && name.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private Optional<OfflinePlayer> resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online);
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(op.getName())) {
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }

    private int runSet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        TagService service = plugin.service();
        Optional<OfflinePlayer> target = resolvePlayer(name);
        if (target.isEmpty()) {
            service.config().messages.send(sender, "unknown-player", Placeholder.unparsed("player", name));
            return 0;
        }
        service.adminSet(sender, target.get());
        service.config().messages.send(sender, "it-set-confirm", Placeholder.unparsed("player", name));
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runClear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.service().adminClear(sender);
        plugin.service().config().messages.send(sender, "it-cleared-confirm");
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runOptOut(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return 0;
        }
        TagService service = plugin.service();
        TagService.OptResult result = service.optOut(player);
        switch (result) {
            case OK -> service.config().messages.send(player, "opted-out");
            case ALREADY -> service.config().messages.send(player, "already-opted-out");
            case IS_IT -> service.config().messages.send(player, "cannot-optout-as-it");
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runOptIn(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return 0;
        }
        TagService service = plugin.service();
        TagService.OptResult result = service.optIn(player);
        switch (result) {
            case OK -> service.config().messages.send(player, "opted-in");
            case ALREADY -> service.config().messages.send(player, "already-opted-in");
            default -> {
            }
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runStatsSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return 0;
        }
        sendStats(sender, player.getUniqueId(), player.getName());
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runStatsOther(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<OfflinePlayer> target = resolvePlayer(name);
        if (target.isEmpty()) {
            plugin.service().config().messages.send(sender, "unknown-player", Placeholder.unparsed("player", name));
            return 0;
        }
        sendStats(sender, target.get().getUniqueId(), name);
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private void sendStats(CommandSender sender, UUID uuid, String name) {
        PlayerStats stats = plugin.service().state().statsFor(uuid);
        sendStats(sender, plugin.service().config().messages, name, stats);
    }

    /** Package-private so tests can prove a configured override of {@code stats-line} is what gets sent. */
    static void sendStats(CommandSender sender, Messages messages, String name, PlayerStats stats) {
        messages.send(sender, "stats-line",
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("points", TagService.formatPoints(stats.points)),
                Placeholder.unparsed("tagged", String.valueOf(stats.timesTagged)),
                Placeholder.unparsed("made", String.valueOf(stats.tagsMade)),
                Placeholder.unparsed("minutes-online", String.format(Locale.ROOT, "%.1f", stats.minutesItOnline)),
                Placeholder.unparsed("minutes-offline", String.format(Locale.ROOT, "%.1f", stats.minutesItOffline)));
    }

    private int runTop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Map.Entry<UUID, PlayerStats>> ranked = plugin.service().state().stats.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> e.getValue().points))
                .limit(10)
                .toList();
        sendLeaderboard(sender, plugin.service().config().messages, ranked, plugin.service()::nameOf);
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    /** Package-private so tests can prove a configured override of the leaderboard templates is what gets sent. */
    static void sendLeaderboard(CommandSender sender, Messages messages, List<Map.Entry<UUID, PlayerStats>> ranked,
                                 Function<UUID, String> nameOf) {
        messages.send(sender, "leaderboard-header");
        int rank = 1;
        for (Map.Entry<UUID, PlayerStats> entry : ranked) {
            messages.send(sender, "leaderboard-entry",
                    Placeholder.unparsed("rank", String.valueOf(rank)),
                    Placeholder.unparsed("player", nameOf.apply(entry.getKey())),
                    Placeholder.unparsed("points", TagService.formatPoints(entry.getValue().points)));
            rank++;
        }
    }

    private int runBoard(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return 0;
        }
        boolean enabled = plugin.boards().toggle(player);
        if (enabled) {
            plugin.boards().refreshAll(plugin.service().state());
        }
        plugin.service().config().messages.send(player, enabled ? "board-enabled" : "board-disabled");
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runWho(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        java.util.UUID it = plugin.service().state().currentIt;
        if (it == null) {
            plugin.service().config().messages.send(sender, "nobody-it");
        } else {
            plugin.service().config().messages.send(sender, "who-is-it", Placeholder.unparsed("it", plugin.service().nameOf(it)));
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int runReload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reload();
        plugin.service().config().messages.send(sender, "reloaded");
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }
}

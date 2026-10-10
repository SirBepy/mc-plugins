package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@code /market watch}/{@code /market unwatch}: registration only, {@link MarketWatches} owns every
 * refusal and side effect so it stays unit-testable without Brigadier, same split as
 * {@link CosmeticCommand}/{@link CosmeticRequests}. Both nodes are {@code .then()}-ed onto the
 * existing {@code market} literal in {@link MarketPlugin}, which already gates the whole command on
 * {@code market.use} - Joe, 2026-10-10: watch/unwatch need no extra permission, same as
 * {@code today}/{@code prices}. Each node additionally requires a {@link Player} executor since a
 * watch list belongs to one player; {@code watch} with no argument lists it, {@code watch <item>}
 * adds, {@code unwatch <item>} removes. {@code watch}'s argument suggests the whole catalogue (every
 * stall-buy item plus every Heads-stall mob id); {@code unwatch}'s suggests only what that player is
 * currently watching, so tab-complete never offers to unwatch something they never watched.
 */
final class MarketWatchCommand {

    private final MarketWatches watches;
    private final Supplier<MarketConfig> config;

    MarketWatchCommand(MarketWatches watches, Supplier<MarketConfig> config) {
        this.watches = watches;
        this.config = config;
    }

    ArgumentBuilder<CommandSourceStack, ?> watchNode() {
        RequiredArgumentBuilder<CommandSourceStack, String> item = Commands.argument("item", StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, watches.catalogue()))
                .executes(ctx -> {
                    add((Player) ctx.getSource().getExecutor(), StringArgumentType.getString(ctx, "item"));
                    return Command.SINGLE_SUCCESS;
                });
        return Commands.literal("watch")
                .requires(src -> src.getExecutor() instanceof Player)
                .executes(ctx -> {
                    list((Player) ctx.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .then(item);
    }

    ArgumentBuilder<CommandSourceStack, ?> unwatchNode() {
        RequiredArgumentBuilder<CommandSourceStack, String> item = Commands.argument("item", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    Player player = ctx.getSource().getExecutor() instanceof Player p ? p : null;
                    return suggest(builder, player == null ? List.of() : watches.watching(player.getUniqueId()));
                })
                .executes(ctx -> {
                    remove((Player) ctx.getSource().getExecutor(), StringArgumentType.getString(ctx, "item"));
                    return Command.SINGLE_SUCCESS;
                });
        return Commands.literal("unwatch")
                .requires(src -> src.getExecutor() instanceof Player)
                .then(item);
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> options) {
        String typed = builder.getRemainingLowerCase();
        options.stream().filter(o -> o.startsWith(typed)).sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private void list(Player player) {
        var messages = config.get().messages();
        List<String> items = watches.watching(player.getUniqueId());
        if (items.isEmpty()) {
            messages.send(player, "watch-list-empty");
            return;
        }
        messages.send(player, "watch-list-header");
        for (String item : items) {
            messages.send(player, "watch-list-line", Placeholder.unparsed("item", item));
        }
    }

    private void add(Player player, String rawItem) {
        String item = rawItem.toLowerCase(Locale.ROOT);
        var messages = config.get().messages();
        var placeholder = Placeholder.unparsed("item", item);
        switch (watches.add(player.getUniqueId(), item)) {
            case ADDED -> messages.send(player, "watch-added", placeholder);
            case ALREADY_WATCHING -> messages.send(player, "watch-already", placeholder);
            case CAP_REACHED -> messages.send(player, "watch-cap", placeholder);
            case UNKNOWN_ITEM -> messages.send(player, "watch-unknown", placeholder);
        }
    }

    private void remove(Player player, String rawItem) {
        String item = rawItem.toLowerCase(Locale.ROOT);
        var messages = config.get().messages();
        var placeholder = Placeholder.unparsed("item", item);
        if (watches.remove(player.getUniqueId(), item)) {
            messages.send(player, "watch-removed", placeholder);
        } else {
            messages.send(player, "watch-not-watching", placeholder);
        }
    }
}

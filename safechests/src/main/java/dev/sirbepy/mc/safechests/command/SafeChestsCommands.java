package dev.sirbepy.mc.safechests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.sirbepy.mc.core.Durations;
import dev.sirbepy.mc.safechests.SafeChestsConfig;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.grant.GrantManager;
import dev.sirbepy.mc.safechests.grant.WhitelistGrant;
import dev.sirbepy.mc.safechests.request.AccessRequest;
import dev.sirbepy.mc.safechests.request.RequestManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.popcraft.bolt.protection.Protection;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Registers every SafeChests command through Brigadier; none of them are declared in {@code plugin.yml}. */
public final class SafeChestsCommands {

    private static final int LOOK_DISTANCE = 6;

    private final BoltAccess bolt;
    private final GrantManager grants;
    private final RequestManager requests;
    private final Supplier<SafeChestsConfig> config;
    private final Runnable reload;

    public SafeChestsCommands(BoltAccess bolt, GrantManager grants, RequestManager requests,
                               Supplier<SafeChestsConfig> config, Runnable reload) {
        this.bolt = bolt;
        this.grants = grants;
        this.requests = requests;
        this.config = config;
        this.reload = reload;
    }

    public void register(JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(oneTimeCommand(), "Request access to the chest you were last denied");
            commands.register(whitelistCommand(), "Let a player open all your chests");
            commands.register(unwhitelistCommand(), "Revoke a player's access to all your chests");
            commands.register(listCommand(), "List who is whitelisted to your chests");
            commands.register(respondCommand(), "Respond to an access request (internal)");
            commands.register(reloadCommand(), "Reload SafeChests' config");
        });
    }

    private LiteralCommandNode<CommandSourceStack> oneTimeCommand() {
        return Commands.literal("safe-chests-one-time")
                .requires(src -> src.getSender().hasPermission("safechests.use"))
                .executes(ctx -> {
                    Player requester = requirePlayer(ctx.getSource());
                    if (requester == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    sendRequest(requester);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void sendRequest(Player requester) {
        SafeChestsConfig cfg = config.get();
        Protection protection = bolt.protectionLookingAt(requester, LOOK_DISTANCE);
        Block block;
        if (protection != null) {
            block = requester.getTargetBlockExact(LOOK_DISTANCE);
        } else {
            Optional<Block> lastDenied = requests.lastDeniedBlock(requester.getUniqueId());
            if (lastDenied.isEmpty()) {
                cfg.messages().send(requester, "no-target-chest");
                return;
            }
            block = lastDenied.get();
            protection = bolt.api().findProtection(block);
        }
        if (protection == null) {
            cfg.messages().send(requester, "no-target-chest");
            return;
        }
        if (protection.getOwner().equals(requester.getUniqueId())) {
            cfg.messages().send(requester, "self-owned");
            return;
        }
        UUID ownerId = protection.getOwner();
        Player owner = requester.getServer().getPlayer(ownerId);
        String ownerName = bolt.ownerDisplayName(ownerId);
        if (owner == null) {
            cfg.messages().send(requester, "owner-offline", Placeholder.unparsed("owner", ownerName));
            return;
        }
        AccessRequest request = requests.create(requester.getUniqueId(), ownerId,
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), cfg.requestExpiry());
        cfg.messages().send(requester, "request-sent", Placeholder.unparsed("owner", ownerName));
        cfg.messages().send(owner, "request-prompt",
                Placeholder.unparsed("requester", requester.getName()),
                Placeholder.unparsed("id", request.id()),
                Placeholder.unparsed("x", String.valueOf(block.getX())),
                Placeholder.unparsed("y", String.valueOf(block.getY())),
                Placeholder.unparsed("z", String.valueOf(block.getZ())));
    }

    private LiteralCommandNode<CommandSourceStack> respondCommand() {
        return Commands.literal("safe-chests-respond")
                .requires(src -> src.getSender().hasPermission("safechests.use"))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.literal("allow-once").executes(ctx -> respond(ctx, StringArgumentType.getString(ctx, "id"), Action.ALLOW_ONCE)))
                        .then(Commands.literal("five-min").executes(ctx -> respond(ctx, StringArgumentType.getString(ctx, "id"), Action.FIVE_MIN)))
                        .then(Commands.literal("always").executes(ctx -> respond(ctx, StringArgumentType.getString(ctx, "id"), Action.ALWAYS)))
                        .then(Commands.literal("deny").executes(ctx -> respond(ctx, StringArgumentType.getString(ctx, "id"), Action.DENY))))
                .build();
    }

    private enum Action { ALLOW_ONCE, FIVE_MIN, ALWAYS, DENY }

    private int respond(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String id, Action action) {
        SafeChestsConfig cfg = config.get();
        Player owner = requirePlayer(ctx.getSource());
        if (owner == null) {
            return Command.SINGLE_SUCCESS;
        }
        // Authorization boundary: the id alone proves nothing, the responder must be the request's owner.
        Optional<AccessRequest> validated = requests.validate(id, owner.getUniqueId());
        if (validated.isEmpty()) {
            cfg.messages().send(owner, "request-not-found");
            return Command.SINGLE_SUCCESS;
        }
        AccessRequest request = validated.get();
        requests.remove(id);
        Player requester = owner.getServer().getPlayer(request.requester());
        switch (action) {
            case DENY -> {
                if (requester != null) {
                    cfg.messages().send(requester, "request-denied-by-owner", Placeholder.unparsed("owner", owner.getName()));
                }
            }
            case ALLOW_ONCE -> {
                var world = Bukkit.getWorld(request.world());
                if (world == null) {
                    return Command.SINGLE_SUCCESS;
                }
                Block block = world.getBlockAt(request.x(), request.y(), request.z());
                Protection protection = bolt.api().findProtection(block);
                if (protection != null) {
                    grants.grantOneTime(protection, owner.getUniqueId(), request.requester());
                    if (requester != null) {
                        cfg.messages().send(requester, "one-time-granted", Placeholder.unparsed("owner", owner.getName()));
                    }
                }
            }
            case FIVE_MIN -> grantWhitelistAndNotify(owner, request.requester(), requester, cfg.fiveMinuteDuration());
            case ALWAYS -> grantWhitelistAndNotify(owner, request.requester(), requester, null);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void grantWhitelistAndNotify(Player owner, UUID granteeId, Player requester, Duration duration) {
        grants.grantWhitelist(owner.getUniqueId(), granteeId, duration);
        if (requester != null) {
            config.get().messages().send(requester, "whitelist-granted",
                    Placeholder.unparsed("owner", owner.getName()),
                    Placeholder.unparsed("duration", duration == null ? " permanently" : " for " + Durations.format(duration)));
        }
    }

    private LiteralCommandNode<CommandSourceStack> whitelistCommand() {
        return Commands.literal("safe-chests-whitelist")
                .requires(src -> src.getSender().hasPermission("safechests.use"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .executes(ctx -> handleWhitelist(ctx, null))
                        .then(Commands.argument("time", StringArgumentType.greedyString())
                                .executes(ctx -> handleWhitelist(ctx, StringArgumentType.getString(ctx, "time")))))
                .build();
    }

    private int handleWhitelist(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String time) {
        SafeChestsConfig cfg = config.get();
        Player owner = requirePlayer(ctx.getSource());
        if (owner == null) {
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(ctx, "player");
        Optional<OfflinePlayer> target = resolvePlayer(name);
        if (target.isEmpty()) {
            cfg.messages().send(owner, "player-not-found", Placeholder.unparsed("player", name));
            return Command.SINGLE_SUCCESS;
        }
        Duration duration = null;
        if (time != null) {
            Optional<Duration> parsed = Durations.parse(time);
            if (parsed.isEmpty()) {
                cfg.messages().send(owner, "invalid-duration");
                return Command.SINGLE_SUCCESS;
            }
            duration = parsed.get();
        }
        WhitelistGrant grant = grants.grantWhitelist(owner.getUniqueId(), target.get().getUniqueId(), duration);
        cfg.messages().send(owner, "whitelist-added",
                Placeholder.unparsed("player", target.get().getName() != null ? target.get().getName() : name),
                Placeholder.unparsed("duration", grant.isPermanent() ? " permanently" : " for " + Durations.format(duration)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralCommandNode<CommandSourceStack> unwhitelistCommand() {
        return Commands.literal("safe-chests-unwhitelist")
                .requires(src -> src.getSender().hasPermission("safechests.use"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .executes(ctx -> {
                            SafeChestsConfig cfg = config.get();
                            Player owner = requirePlayer(ctx.getSource());
                            if (owner == null) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "player");
                            Optional<OfflinePlayer> target = resolvePlayer(name);
                            if (target.isEmpty()) {
                                cfg.messages().send(owner, "player-not-found", Placeholder.unparsed("player", name));
                                return Command.SINGLE_SUCCESS;
                            }
                            boolean removed = grants.revokeWhitelist(owner.getUniqueId(), target.get().getUniqueId());
                            cfg.messages().send(owner, removed ? "whitelist-removed" : "whitelist-not-found",
                                    Placeholder.unparsed("player", name));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> listCommand() {
        return Commands.literal("safe-chests-list")
                .requires(src -> src.getSender().hasPermission("safechests.use"))
                .executes(ctx -> {
                    SafeChestsConfig cfg = config.get();
                    Player owner = requirePlayer(ctx.getSource());
                    if (owner == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    var entries = grants.whitelistFor(owner.getUniqueId());
                    if (entries.isEmpty()) {
                        cfg.messages().send(owner, "whitelist-list-empty");
                        return Command.SINGLE_SUCCESS;
                    }
                    cfg.messages().send(owner, "whitelist-list-header");
                    for (WhitelistGrant grant : entries) {
                        String name = bolt.ownerDisplayName(grant.grantee());
                        String expiry = grant.isPermanent() ? "permanent" : "expires in " + Durations.format(Duration.between(java.time.Instant.now(), grant.expiresAt()));
                        cfg.messages().send(owner, "whitelist-list-entry",
                                Placeholder.unparsed("player", name), Placeholder.unparsed("expiry", expiry));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> reloadCommand() {
        return Commands.literal("safechests")
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("safechests.admin"))
                        .executes(ctx -> {
                            reload.run();
                            config.get().messages().send(ctx.getSource().getSender(), "reload-success");
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private Player requirePlayer(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (sender instanceof Player player) {
            return player;
        }
        config.get().messages().send(sender, "player-only");
        return null;
    }

    private Optional<OfflinePlayer> resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return Optional.ofNullable(cached);
    }

    private CompletableFuture<Suggestions> suggestPlayers(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }
}

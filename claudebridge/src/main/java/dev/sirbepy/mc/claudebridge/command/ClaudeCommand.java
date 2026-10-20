package dev.sirbepy.mc.claudebridge.command;

import dev.sirbepy.mc.claudebridge.config.ClaudeBridgeConfig;
import dev.sirbepy.mc.claudebridge.cooldown.CooldownTracker;
import dev.sirbepy.mc.claudebridge.data.ClaudeMessageValidation;
import dev.sirbepy.mc.claudebridge.data.ClaudeMessageValidator;
import dev.sirbepy.mc.claudebridge.heartbeat.HeartbeatTracker;
import dev.sirbepy.mc.claudebridge.inbox.InboxWriter;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

/**
 * {@code /shenron <message...>}: forwards a player's message to an outside Claude session via the
 * console log and plugins/ClaudeBridge/inbox.jsonl (see InboxWriter); the reply comes back through
 * server/deploy/claude-reply.mjs as a tellraw, outside this plugin entirely.
 */
public final class ClaudeCommand implements BasicCommand {

    private final JavaPlugin plugin;
    private final ClaudeBridgeConfig config;
    private final CooldownTracker cooldown;
    private final File inboxFile;
    private final HeartbeatTracker heartbeat;

    public ClaudeCommand(JavaPlugin plugin, ClaudeBridgeConfig config, CooldownTracker cooldown, File inboxFile,
            HeartbeatTracker heartbeat) {
        this.plugin = plugin;
        this.config = config;
        this.cooldown = cooldown;
        this.inboxFile = inboxFile;
        this.heartbeat = heartbeat;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            config.messages().send(source.getSender(), "player-only");
            return;
        }
        if (!player.hasPermission("claudebridge.use")) {
            config.messages().send(player, "no-permission");
            return;
        }
        // BasicCommand already hands args pre-split on spaces; rejoining is the greedy string.
        handle(player, String.join(" ", args));
    }

    /** Package-private so tests can drive it without a real Brigadier CommandSourceStack. */
    void handle(Player player, String rawMessage) {
        ClaudeMessageValidation validation = ClaudeMessageValidator.validate(rawMessage);
        if (validation instanceof ClaudeMessageValidation.Invalid invalid) {
            config.messages().send(player, invalid.reason() == ClaudeMessageValidation.Reason.BLANK
                    ? "message-blank" : "message-too-long");
            return;
        }
        String message = ((ClaudeMessageValidation.Valid) validation).message();

        // Only an otherwise-valid message is checked against the cooldown and only an accepted
        // one records a use: a rejected empty/too-long attempt the player is about to retry
        // should never burn their cooldown window.
        long secondsLeft = cooldown.secondsRemaining(player.getUniqueId(), config.cooldownSeconds());
        if (secondsLeft > 0) {
            config.messages().send(player, "cooldown", Placeholder.unparsed("seconds", String.valueOf(secondsLeft)));
            return;
        }
        cooldown.recordUse(player.getUniqueId());

        // Built in full here rather than relying on PluginLogger's own "[PluginName] " prefix:
        // confirmed live (2026-10-04) that this Paper version's plugin logger does not add one
        // (MockBukkit uses the same no-wrapper init(Server, ..., Logger) path Paper itself does).
        plugin.getLogger().info(logLine(player, message));
        try {
            InboxWriter.append(inboxFile, player.getName(), player.getUniqueId(), message, Instant.now());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to append to inbox.jsonl: " + e.getMessage());
        }
        // Written to the inbox and logged either way, so Shenron answers it whenever he's back;
        // only the player-facing reply differs on whether anyone's actually listening right now.
        config.messages().send(player, heartbeat.isOnline(config.onlineThresholdSeconds()) ? "sent" : "shenron-offline");
    }

    /**
     * Package-private so tests can prove the exact format without a real logger handler. The
     * replace calls are a defensive no-op today (ClaudeMessageValidator already strips every
     * control character, newlines included, from any message that reaches here) but keep this
     * method's own "(newlines removed)" guarantee from the spec true even if a future caller
     * ever passes it unvalidated text directly.
     */
    static String logLine(Player player, String message) {
        return "[ClaudeBridge] " + player.getName() + " (" + player.getUniqueId() + "): "
                + message.replace("\n", "").replace("\r", "");
    }
}

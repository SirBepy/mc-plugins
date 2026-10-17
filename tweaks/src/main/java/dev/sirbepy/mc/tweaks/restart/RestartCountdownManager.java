package dev.sirbepy.mc.tweaks.restart;

import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Bukkit wiring around {@link RestartCountdown}: a 1s scheduler tick broadcasts its warnings to
 * every online player (plus a title at 10s and below), runs save-all-flush and logs the go marker
 * on {@code GO}, and a {@link PlayerQuitEvent} listener clears a leaving player's own
 * {@code /dont-restart} delay (the countdown's own 1h timeout covers the other half of
 * "whichever is first"). kinetic.mjs's {@code restart} subcommand drives this entirely through the
 * console command ({@link RestartCommand}): it sends {@code restartcountdown start <token> <reason>}
 * then polls the server log for {@code RESTART-GO <token>} before it sends the panel's own
 * {@code power restart}, which is the only thing that can actually restart the process.
 */
public final class RestartCountdownManager implements Listener {

    // Only matters for the admin-facing /restartcountdown status read-out, so any reasonable zone works.
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final Set<Integer> TITLE_AT_OR_BELOW = Set.of(10, 5, 1);

    private final Server server;
    private final Messages messages;
    private final Logger logger;
    private final Runnable saveAll;
    private final RestartCountdown countdown;

    public RestartCountdownManager(Server server, Messages messages, Logger logger, LongSupplier clockMillis, Runnable saveAll) {
        this.server = server;
        this.messages = messages;
        this.logger = logger;
        this.saveAll = saveAll;
        this.countdown = new RestartCountdown(clockMillis);
    }

    RestartCountdown countdown() {
        return countdown;
    }

    public void start(CommandSender sender, String token, String reason) {
        apply(countdown.start(token, reason));
        messages.send(sender, "restart-countdown-started", Placeholder.unparsed("reason", reason));
    }

    public void cancel(CommandSender sender) {
        if (!countdown.isRunning()) {
            messages.send(sender, "restart-countdown-not-pending");
            return;
        }
        countdown.cancel();
        broadcastAll("restart-countdown-cancelled");
        messages.send(sender, "restart-countdown-cancelled");
    }

    public void status(CommandSender sender) {
        if (!countdown.isRunning()) {
            messages.send(sender, "restart-countdown-not-pending");
            return;
        }
        Map<UUID, Long> delays = countdown.activeDelays();
        String state = countdown.isWaitingOnDelays()
                ? "holding at 0, waiting on " + delays.size() + " delay(s)"
                : countdown.remainingSeconds() + "s remaining";
        sender.sendMessage("restartcountdown: " + state + " (" + countdown.reason() + ")");
        if (delays.isEmpty()) {
            sender.sendMessage("no active delays");
        } else {
            for (Map.Entry<UUID, Long> entry : delays.entrySet()) {
                sender.sendMessage("  " + playerName(entry.getKey()) + " until " + CLOCK.format(Instant.ofEpochMilli(entry.getValue())));
            }
        }
    }

    /** {@code /dont-restart}: delays the pending restart for everyone by up to an hour, or until this player leaves. */
    public void dontRestart(Player player) {
        RestartCountdown.DelayResult result = countdown.delay(player.getUniqueId());
        if (!result.pending()) {
            messages.send(player, "dont-restart-no-pending");
            return;
        }
        messages.send(player, result.renewed() ? "dont-restart-renewed" : "dont-restart-delayed");
        for (Player online : server.getOnlinePlayers()) {
            if (!online.equals(player)) {
                messages.send(online, "dont-restart-broadcast", Placeholder.unparsed("player", player.getName()));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        countdown.clearDelay(event.getPlayer().getUniqueId());
    }

    /** Runs once a second from the plugin's scheduler. */
    public void tick() {
        apply(countdown.tick());
    }

    private void apply(List<RestartCountdown.Event> events) {
        for (RestartCountdown.Event event : events) {
            switch (event.type()) {
                case WARNING -> broadcastWarning(event.remainingSeconds());
                case DELAYED -> broadcastAll("restart-countdown-delayed");
                case REARMED -> broadcastAll("restart-countdown-rearmed");
                case GO -> fire();
            }
        }
    }

    private void broadcastWarning(int seconds) {
        String key = "restart-countdown-warning-" + seconds;
        for (Player player : server.getOnlinePlayers()) {
            messages.send(player, key, Placeholder.unparsed("reason", countdown.reason()));
            if (TITLE_AT_OR_BELOW.contains(seconds)) {
                player.showTitle(Title.title(
                        messages.render("restart-countdown-title", Placeholder.unparsed("seconds", String.valueOf(seconds))),
                        messages.render("restart-countdown-subtitle")));
            }
        }
    }

    private void broadcastAll(String key) {
        for (Player player : server.getOnlinePlayers()) {
            messages.send(player, key);
        }
    }

    private void fire() {
        saveAll.run();
        // kinetic.mjs's restart subcommand polls the server log for this exact line; the token comes
        // from its own `restartcountdown start <token> <reason>` call, so a marker left by some earlier
        // run (or another plugin instance) can never be mistaken for this run's go signal.
        logger.info("RESTART-GO " + countdown.token());
    }

    private String playerName(UUID id) {
        Player online = server.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String name = server.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }
}

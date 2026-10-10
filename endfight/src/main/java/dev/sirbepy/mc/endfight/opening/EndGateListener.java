package dev.sirbepy.mc.endfight.opening;

import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps every portal into the End shut while {@link EndOpening} says it's closed - ops included
 * (Joe, 2026-10-10: nobody should start the fight early by accident) - and runs the countdown:
 * a chat line with a ding at each step, a title and the dragon's growl when it opens.
 */
public final class EndGateListener implements Listener {

    private static final Duration NOTICE_EVERY = Duration.ofSeconds(3);

    private final EndOpening opening;
    private final Supplier<Messages> messages;
    private final Clock clock;
    private final Map<UUID, Instant> lastNotice = new HashMap<>();

    public EndGateListener(EndOpening opening, Supplier<Messages> messages, Clock clock) {
        this.opening = opening;
        this.messages = messages;
        this.clock = clock;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!intoTheEnd(event.getCause(), event.getFrom(), event.getTo())) {
            return;
        }
        Instant now = clock.instant();
        if (!opening.closed(now)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        liftOutOfPortal(player);
        Instant last = lastNotice.get(player.getUniqueId());
        if (last == null || !now.isBefore(last.plus(NOTICE_EVERY))) {
            lastNotice.put(player.getUniqueId(), now);
            messages.get().send(player, "portal-closed", times(now));
        }
    }

    /** Pearls, items and mobs wait too, so nothing reaches the End before the players do. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location to = event.getTo();
        boolean end = to != null && to.getWorld() != null && to.getWorld().getEnvironment() == World.Environment.THE_END
                && event.getFrom().getWorld().getEnvironment() != World.Environment.THE_END;
        if (end && opening.closed(clock.instant())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Instant now = clock.instant();
        if (!opening.closed(now)) {
            return;
        }
        Messages m = messages.get();
        m.send(event.getPlayer(), "closed-join", times(now));
        if (opening.warpHintStarted(now)) {
            m.send(event.getPlayer(), "warp-hint");
        }
    }

    /** Called every second by the plugin's timer. */
    public void tick() {
        Instant now = clock.instant();
        for (EndOpening.Step step : opening.due(now)) {
            announce(step, now);
        }
    }

    private void announce(EndOpening.Step step, Instant now) {
        Messages m = messages.get();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (step.opening()) {
                m.send(player, "open-chat");
                player.showTitle(Title.title(m.render("open-title"), m.render("open-subtitle")));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                continue;
            }
            m.send(player, "countdown", times(now));
            if (step.warpHint()) {
                m.send(player, "warp-hint");
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }
    }

    private TagResolver times(Instant now) {
        return TagResolver.resolver(
                Placeholder.unparsed("time", opening.clockLabel()),
                Placeholder.unparsed("left", opening.leftLabel(now)));
    }

    private static boolean intoTheEnd(PlayerTeleportEvent.TeleportCause cause, Location from, Location to) {
        // The exit portal inside the End leads home, never in.
        if (from.getWorld() != null && from.getWorld().getEnvironment() == World.Environment.THE_END) {
            return false;
        }
        return cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || to != null && to.getWorld() != null && to.getWorld().getEnvironment() == World.Environment.THE_END;
    }

    /** A stronghold portal sits over lava, so a bounced player is set down beside the frames
     * instead of falling through. */
    private static void liftOutOfPortal(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        Optional<PortalRoomFinder.Spot> spot = PortalRoomFinder.findAround(
                (x, y, z) -> world.getType(x, y, z), at.getBlockX(), at.getBlockY(), at.getBlockZ(), 4);
        spot.ifPresent(s -> {
            player.setFallDistance(0);
            player.setVelocity(new org.bukkit.util.Vector());
            player.teleport(new Location(world, s.x() + 0.5, s.y(), s.z() + 0.5, s.yaw(), 0f));
        });
    }
}

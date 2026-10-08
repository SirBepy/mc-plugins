package dev.sirbepy.mc.tag.teleport;

import dev.sirbepy.mc.tag.config.TeleportBlockConfig;
import dev.sirbepy.mc.tag.logic.Playability;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Owns the teleport-block decision, the way {@code AfkService}/{@code TrackerService}/
 * {@code TagEventService} own their own decisions. Read-only: no TagState to mutate, so it is
 * called straight from {@link dev.sirbepy.mc.tag.listener.TeleportBlockListener} rather than
 * through TagService (the single place allowed to mutate TagState).
 */
public final class TeleportBlockService {

    /**
     * The IT that pins {@code player} in place for a teleport starting at {@code from}, or null when
     * they're free to go. An AFK or non-playing IT can't chase anyone, so it pins nobody.
     */
    public Player itBlockingTeleport(Player player, Location from, TeleportBlockConfig config, Player it,
                                      Predicate<UUID> isIt, Predicate<UUID> isOptedOut, Predicate<UUID> isAfk) {
        UUID id = player.getUniqueId();
        if (!config.enabled || isIt.test(id) || isOptedOut.test(id) || !Playability.playable(player)) {
            return null;
        }
        if (it == null || !Playability.playable(it) || isAfk.test(it.getUniqueId()) || !it.getWorld().equals(from.getWorld())) {
            return null;
        }
        double radius = config.radius;
        return it.getLocation().distanceSquared(from) <= radius * radius ? it : null;
    }
}

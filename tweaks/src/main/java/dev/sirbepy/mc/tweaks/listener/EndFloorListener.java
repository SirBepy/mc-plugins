package dev.sirbepy.mc.tweaks.listener;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Lifts Bedrock players out of the End below y 0. The End here is taller than vanilla, but Geyser
 * only sends Bedrock clients the_end between y 0 and 256, so below 0 the client has no world, hovers,
 * and the server kicks it for floating a few seconds after every join: a Bedrock player who logged
 * out there was locked out (.SuperspiderSam, 2026-10-10, 12+ kicks until an op ran /spawn on his
 * login line). Java players see the whole End and are left alone.
 */
public final class EndFloorListener implements Listener {

    /** Bedrock's lowest End block row; anything lower doesn't exist for the client. */
    static final int BEDROCK_FLOOR = 0;

    private final Plugin plugin;
    private final Predicate<UUID> isBedrock;
    private final Messages messages;
    private final Logger logger;

    public EndFloorListener(Plugin plugin, Predicate<UUID> isBedrock, Messages messages) {
        this.plugin = plugin;
        this.isBedrock = isBedrock;
        this.messages = messages;
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // A tick later, once the join has finished placing the player; the floating kick takes
        // about 4 s, so this is well inside it.
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> rescue(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        rescue(event.getPlayer());
    }

    // HIGHEST, not MONITOR: this rewrites the destination, and MONITOR handlers expect a settled event.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getY() >= BEDROCK_FLOOR || event.getFrom().getY() < BEDROCK_FLOOR) {
            return;
        }
        if (!below(event.getPlayer(), to)) {
            return;
        }
        event.setTo(safeSpot(to));
        report(event.getPlayer(), to, event.getTo());
    }

    /** Moves the player to safety if they are a Bedrock player under the End's Bedrock floor. */
    boolean rescue(Player player) {
        if (!player.isOnline()) {
            return false;
        }
        Location from = player.getLocation();
        if (!below(player, from)) {
            return false;
        }
        Location to = safeSpot(from);
        player.teleport(to);
        report(player, from, to);
        return true;
    }

    private boolean below(Player player, Location at) {
        World world = at.getWorld();
        return world != null && world.getEnvironment() == World.Environment.THE_END
                && at.getY() < BEDROCK_FLOOR && isBedrock.test(player.getUniqueId());
    }

    /** On top of the highest solid block in the column if Bedrock can see it, else overworld spawn. */
    static Location safeSpot(Location from) {
        World world = from.getWorld();
        int x = from.getBlockX();
        int z = from.getBlockZ();
        // A scan rather than getHighestBlockYAt: it only runs on a rescue, and it reads plain block
        // types, so it can't be fooled by a heightmap that counts non-solid blocks.
        for (int y = world.getMaxHeight() - 1; y >= BEDROCK_FLOOR + 1; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5, from.getYaw(), from.getPitch());
            }
        }
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private void report(Player player, Location from, Location to) {
        logger.info("Moved Bedrock player " + player.getName() + " out of the End below y " + BEDROCK_FLOOR
                + ": " + describe(from) + " -> " + describe(to));
        messages.send(player, "end-floor-rescue");
    }

    private static String describe(Location at) {
        return at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
    }
}

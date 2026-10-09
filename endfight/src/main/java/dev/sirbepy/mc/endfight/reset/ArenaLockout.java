package dev.sirbepy.mc.endfight.reset;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Keeps players off the island while a respawn is in progress: every 20 ticks (configurable),
 * teleports anyone in survival/adventure within {@code lockout-radius} blocks of 0,0 to the
 * obsidian arrival platform, making sure the platform itself exists first (vanilla's own
 * {@code EndPlatformFeature.createEndPlatform}: 5x5 obsidian one block below the stand point, 3
 * air blocks above it - verified by javap against the 26.3 server jar). The platform sits at
 * x=100, 20+ blocks past an 80-block lockout radius around the portal, so a player already
 * standing on it is never picked up as a candidate in the first place - nothing special has to
 * exclude it.
 */
public final class ArenaLockout {

    private ArenaLockout() {
    }

    public static List<Player> candidates(World world, ResetConfig cfg) {
        double radiusSq = cfg.lockoutRadius() * cfg.lockoutRadius();
        return world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                .filter(p -> horizontalDistanceSquared(p.getLocation()) <= radiusSq)
                .toList();
    }

    /** Vanilla's shape, centred on {@code cfg}'s platform position: a 5x5 obsidian pad one block
     * below the stand point, with 3 blocks of air above it so nobody suffocates if the real
     * platform was ever mined out. */
    public static void ensurePlatform(World world, ResetConfig cfg) {
        int cx = (int) Math.floor(cfg.platformX());
        int cz = (int) Math.floor(cfg.platformZ());
        int standY = (int) Math.floor(cfg.platformY());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, standY - 1, cz + dz).setType(Material.OBSIDIAN, false);
                for (int dy = 0; dy < 3; dy++) {
                    world.getBlockAt(cx + dx, standY + dy, cz + dz).setType(Material.AIR, false);
                }
            }
        }
    }

    public static Location standLocation(World world, ResetConfig cfg) {
        return new Location(world, cfg.platformX(), cfg.platformY(), cfg.platformZ(), cfg.platformYaw(), 0f);
    }

    public static void teleportAll(World world, ResetConfig cfg) {
        ensurePlatform(world, cfg);
        Location at = standLocation(world, cfg);
        for (Player player : candidates(world, cfg)) {
            player.teleport(at);
            cfg.messages().send(player, "lockout-teleport");
        }
    }

    private static double horizontalDistanceSquared(Location at) {
        return at.getX() * at.getX() + at.getZ() * at.getZ();
    }
}

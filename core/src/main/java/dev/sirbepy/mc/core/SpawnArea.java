package dev.sirbepy.mc.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * The spawn area: a square (Chebyshev, the shape vanilla spawn protection used) around the main
 * world's spawn point. Tweaks guards its blocks and keeps it free of PvP and hostile mobs, and Tag
 * never tags inside it (Joe, 2026-10-10: "spawn is a safe space from PVP and mobs").
 */
public final class SpawnArea {

    /**
     * Covers the whole spawn build around live's spawn (427, -868): the market plaza reaches 24
     * blocks out and the shop street 43 blocks south (server/spawn-market/core.mjs).
     */
    public static final int RADIUS = 48;

    private SpawnArea() {
    }

    /** Within {@link #RADIUS} of the main world's spawn; only the overworld has one. */
    public static boolean contains(Location location) {
        World world = location.getWorld();
        if (world == null || !world.equals(Bukkit.getWorlds().get(0))) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        return Math.max(Math.abs(location.getBlockX() - spawn.getBlockX()),
                Math.abs(location.getBlockZ() - spawn.getBlockZ())) <= RADIUS;
    }
}

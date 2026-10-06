package dev.sirbepy.mc.dragonballs.registry;

import org.bukkit.Location;

import java.util.Map;

/**
 * The world-mutating half of scattering (async chunk loads, block placement), kept behind an
 * interface so {@link BallRegistry} stays plain bookkeeping and unit-testable without a server.
 * Implemented by {@code dev.sirbepy.mc.dragonballs.scatter.ScatterService}.
 */
public interface BallScatterer {

    /**
     * Scatters all 7 balls around {@code center}, each at least min-spacing apart (within its own
     * dimension; a Nether ball and an overworld ball never compete for space). Returns each star's
     * chosen destination immediately for phase 2's flying-ball animation; the actual block appears
     * once that star's async surface search/placement finishes.
     */
    Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry registry);

    /** Scatters just one ball, in the overworld, around {@code center} (self-heal, void respawn,
     * logout timeout: recovery paths that don't go through the difficulty ladder). */
    void scatterOne(int star, Location center, boolean stone, BallRegistry registry);
}

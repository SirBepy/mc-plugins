package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds the 10 vanilla end-spike pillars by reading blocks, not by re-deriving vanilla's seeded
 * shuffle (idx -&gt; radius/height is per-world random; obsidian that's actually there is ground
 * truth). The 10 column centres themselves are NOT random
 * ({@code EndSpikeFeature$SpikeCacheLoader.load}: {@code angle = 2*(-PI + PI/10*i)},
 * {@code x=floor(42*cos(angle))}, {@code z=floor(42*sin(angle))}), so those are computed directly.
 */
public final class PillarScanner {

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 256;
    /** How far outside the pillar to look for the island surface (gen_island.py's outposts start
     * at radius+1, so radius+3 clears every pillar's own structure footprint). */
    private static final int SURFACE_PROBE_OFFSET = 3;

    private PillarScanner() {
    }

    /** The 10 fixed (cx, cz) pillar centres, in vanilla's i=0..9 slot order. */
    public static List<int[]> vanillaCenters() {
        List<int[]> centers = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            double angle = 2 * (-Math.PI + Math.PI / 10 * i);
            int cx = (int) Math.floor(42 * Math.cos(angle));
            int cz = (int) Math.floor(42 * Math.sin(angle));
            centers.add(new int[] {cx, cz});
        }
        return centers;
    }

    /** Scans every vanilla pillar slot, skipping (not guessing at) any column with no obsidian. */
    public static List<Pillar> scanAll(BlockLookup lookup) {
        List<Pillar> pillars = new ArrayList<>(10);
        for (int[] center : vanillaCenters()) {
            scan(lookup, center[0], center[1]).ifPresent(pillars::add);
        }
        return pillars;
    }

    /** Reads one pillar at (cx, cz): topY from the centre column's highest obsidian, radius from
     * the obsidian's extent along +x at that height, surfaceY from the highest end_stone a few
     * blocks outside. Empty if any of those can't be read (missing/ungenerated chunk, no pillar). */
    public static Optional<Pillar> scan(BlockLookup lookup, int cx, int cz) {
        int topObsidianY = -1;
        for (int y = MAX_Y - 1; y >= MIN_Y; y--) {
            if (lookup.at(cx, y, cz) == Material.OBSIDIAN) {
                topObsidianY = y;
                break;
            }
        }
        if (topObsidianY < 0) {
            return Optional.empty();
        }
        int topY = topObsidianY + 1;

        int radius = 0;
        for (int dx = 1; dx <= 8; dx++) {
            if (lookup.at(cx + dx, topObsidianY, cz) == Material.OBSIDIAN) {
                radius = dx;
            } else {
                break;
            }
        }
        if (radius <= 0) {
            return Optional.empty();
        }

        int surfaceY = -1;
        int probeX = cx + radius + SURFACE_PROBE_OFFSET;
        for (int y = MAX_Y - 1; y >= MIN_Y; y--) {
            if (lookup.at(probeX, y, cz) == Material.END_STONE) {
                surfaceY = y;
                break;
            }
        }
        if (surfaceY < 0) {
            return Optional.empty();
        }

        return Optional.of(new Pillar(cx, cz, radius, topY, surfaceY));
    }
}

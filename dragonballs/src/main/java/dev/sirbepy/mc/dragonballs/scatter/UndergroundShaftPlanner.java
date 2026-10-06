package dev.sirbepy.mc.dragonballs.scatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure shape math for "a hole going down" (Joe's words), not a clean 1-wide mineshaft: a 2x2 core
 * column (kept at every layer so the column is trivially connected top to bottom, which is what the
 * connectivity test checks) with a few randomly jittered extra blocks per layer for rough edges,
 * ending in a small chamber whose footprint fully contains the core (so the shaft-to-chamber joint
 * is connected the same way). No Bukkit here: the actual world safety/carving lives in
 * {@link UndergroundSafety} and {@code ScatterService}, which turn these offsets into real blocks.
 */
public final class UndergroundShaftPlanner {

    private static final int CHAMBER_HEIGHT = 2;
    // Candidates for the rim ring: the 4x4 area centered on the core, minus the core itself.
    private static final int RIM_MIN = -1;
    private static final int RIM_MAX = 2;

    private UndergroundShaftPlanner() {
    }

    /**
     * Offsets relative to the surface ground block at (0, 0, 0). {@code clearOffsets} runs from the
     * opening (dy = 0) down through the chamber floor's ceiling; {@code rimOffsets} sit at dy = 0,
     * just outside the core, for the impact-debris ring; {@code headOffset} is where the ball head
     * stands, one block above the (uncleared) chamber floor.
     */
    public record ShaftShape(List<int[]> clearOffsets, List<int[]> rimOffsets, int[] headOffset) {

        /** The one block the ball head must be standing on; carve-time fills it in if not solid. */
        public int[] floorOffset() {
            return new int[]{headOffset[0], headOffset[1] - 1, headOffset[2]};
        }
    }

    public static ShaftShape plan(Random random, int depth) {
        List<int[]> clear = new ArrayList<>();
        for (int dy = 0; dy > -depth; dy--) {
            addCore(clear, dy);
            addJitter(random, clear, dy);
        }
        int chamberTop = -depth;
        for (int dy = chamberTop; dy > chamberTop - CHAMBER_HEIGHT; dy--) {
            addChamberLayer(clear, dy);
        }
        int[] headOffset = {0, chamberTop - CHAMBER_HEIGHT + 1, 0};
        List<int[]> rim = pickRim(random);
        return new ShaftShape(clear, rim, headOffset);
    }

    private static void addCore(List<int[]> clear, int dy) {
        clear.add(new int[]{0, dy, 0});
        clear.add(new int[]{1, dy, 0});
        clear.add(new int[]{0, dy, 1});
        clear.add(new int[]{1, dy, 1});
    }

    /** Edge widening: each layer has a chance of punching one extra block into the ring around the
     * core, so the shaft silhouette looks torn open rather than drilled. */
    private static void addJitter(Random random, List<int[]> clear, int dy) {
        if (random.nextDouble() < 0.45) {
            int dx = RIM_MIN + random.nextInt(RIM_MAX - RIM_MIN + 1);
            int dz = RIM_MIN + random.nextInt(RIM_MAX - RIM_MIN + 1);
            if (!isCore(dx, dz)) {
                clear.add(new int[]{dx, dy, dz});
            }
        }
    }

    private static void addChamberLayer(List<int[]> clear, int dy) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                clear.add(new int[]{dx, dy, dz});
            }
        }
    }

    private static boolean isCore(int dx, int dz) {
        return (dx == 0 || dx == 1) && (dz == 0 || dz == 1);
    }

    /** A few (4-6) of the ring cells around the core, for the surface impact-debris rim. */
    private static List<int[]> pickRim(Random random) {
        List<int[]> candidates = new ArrayList<>();
        for (int dx = RIM_MIN; dx <= RIM_MAX; dx++) {
            for (int dz = RIM_MIN; dz <= RIM_MAX; dz++) {
                if (!isCore(dx, dz)) {
                    candidates.add(new int[]{dx, dz});
                }
            }
        }
        java.util.Collections.shuffle(candidates, random);
        int count = Math.min(candidates.size(), 4 + random.nextInt(3));
        return new ArrayList<>(candidates.subList(0, count));
    }
}

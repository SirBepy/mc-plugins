package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * Reads {@link Terrain} off natural end stone. The fortress stands at the height three quarters of
 * the ring's ground is at or below (its foundations reach down over the lower quarter, and the
 * walls are just partly sunk where the ground is higher); the altar stands on the highest ground
 * under its own dais.
 */
public final class TerrainScanner {

    static final double RING_RADIUS = 15.5;
    static final int RING_SAMPLES = 64;
    static final double RING_PERCENTILE = 0.75;
    static final int MIN_Y = 0;
    static final int MAX_Y = 256;

    private TerrainScanner() {
    }

    /** Empty when no end stone is found at all (the island hasn't generated there). */
    public static java.util.Optional<Terrain> scan(BlockLookup lookup) {
        List<Integer> ring = new ArrayList<>(RING_SAMPLES);
        for (int i = 0; i < RING_SAMPLES; i++) {
            double angle = 2 * Math.PI * i / RING_SAMPLES;
            int x = (int) Math.round(RING_RADIUS * Math.cos(angle));
            int z = (int) Math.round(RING_RADIUS * Math.sin(angle));
            topEndStone(lookup, x, z).ifPresent(ring::add);
        }
        if (ring.isEmpty()) {
            return java.util.Optional.empty();
        }
        Collections.sort(ring);
        int fortressY = ring.get(Math.min(ring.size() - 1, (int) Math.floor(RING_PERCENTILE * (ring.size() - 1))));

        int altarY = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                OptionalInt top = topEndStone(lookup, dx, AltarStructures.CLICK_OFFSET_Z + dz);
                if (top.isPresent()) {
                    altarY = Math.max(altarY, top.getAsInt());
                }
            }
        }
        return java.util.Optional.of(new Terrain(fortressY, altarY == Integer.MIN_VALUE ? fortressY : altarY));
    }

    static OptionalInt topEndStone(BlockLookup lookup, int x, int z) {
        for (int y = MAX_Y - 1; y >= MIN_Y; y--) {
            if (lookup.at(x, y, z) == Material.END_STONE) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }
}

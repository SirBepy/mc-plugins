package dev.sirbepy.mc.endfight.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dragon Altar: a small crying-obsidian/polished-blackstone dais just inside the fortress
 * courtyard, north of the exit portal (centred {@code (0, surfaceY+1, -9)} - 9 blocks out, well
 * inside the courtyard's own inner radius 14 and this altar's own "stays inside radius 13" budget,
 * and far enough from the portal itself at {@code (0,0)} that nothing of it is ever above it).
 * Right-clicking the lodestone at its centre (the "clickable block", see {@link #CLICK_OFFSET_Z})
 * opens the tier menu - see {@code endfight.altar}. The corner posts are capped with soul
 * lanterns rather than candles: vanilla has no plain "soul candle" block (checked against
 * {@code Material} directly - only the 16 dyed {@code _CANDLE}/{@code _CANDLE_CAKE} variants
 * exist), and soul lantern is already this fortress's own ambience symbol (see the gatehouses and
 * pillar rings in {@link FortressStructures}/{@link PillarStructures}).
 *
 * <p>Its blocks are folded into {@link ArenaLayout}'s own placement list (one call in
 * {@link ArenaLayout#build}), so {@link ArenaRegistry}/{@link ArenaProtection} track and protect
 * them exactly like every other arena structure, with no separate wiring needed.
 */
public final class AltarStructures {

    /** North of the portal (negative z), comfortably inside the courtyard's inner radius 14. */
    static final int CLICK_OFFSET_Z = -9;

    private AltarStructures() {
    }

    /** One block the altar wants placed, plus the position of its clickable block (the lodestone
     * at the dais's centre), both relative to the portal at (0,0) and offset by {@code surfaceY}. */
    public record AltarResult(List<Placement> placements, int clickX, int clickY, int clickZ) {
    }

    public static AltarResult build(int surfaceY) {
        List<Placement> placements = new ArrayList<>();
        int floorY = surfaceY;
        int clickY = surfaceY + 1;

        // The dais stands on the highest ground under it; a short foundation fills any dip.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = floorY - 3; y <= floorY; y++) {
                    placements.add(new Placement(dx, y, CLICK_OFFSET_Z + dz, "minecraft:polished_blackstone"));
                }
            }
        }
        placements.add(new Placement(0, floorY, CLICK_OFFSET_Z, "minecraft:crying_obsidian"));
        placements.add(new Placement(0, clickY, CLICK_OFFSET_Z, "minecraft:lodestone"));

        for (int dx : new int[] {-1, 1}) {
            for (int dz : new int[] {-1, 1}) {
                placements.add(new Placement(dx, clickY, CLICK_OFFSET_Z + dz, "minecraft:crying_obsidian"));
                placements.add(new Placement(dx, clickY + 1, CLICK_OFFSET_Z + dz, "minecraft:soul_lantern[hanging=false]"));
            }
        }

        return new AltarResult(placements, 0, clickY, CLICK_OFFSET_Z);
    }
}

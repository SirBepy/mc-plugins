package dev.sirbepy.mc.endfight.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cult fortress ring around the exit portal at 0,0, ported from gen_island.py's
 * generate_fortress/build_gatehouse/build_watchtower, plus the new decoration Joe approved on top:
 * real ominous banners, soul-fire braziers, polished-blackstone gate arches and hanging soul
 * lanterns (gen_island.py only stood these in with wool markers / nothing at all - see its module
 * docstring's "Renderer gaps/approximations" section).
 *
 * <p>Far enough from every pillar (inner radius 14 vs. the pillars' own radius-42 ring) that none
 * of this needs the box/cylinder exclusion check {@link PillarStructures} uses.
 */
final class FortressStructures {

    private static final int INNER_R = 14;
    private static final int THICKNESS = 3;
    private static final int HEIGHT = 8;
    private static final int OUTER_R = INNER_R + THICKNESS;
    // The fortress stands on one surface height (the pillars' average), but the island's real
    // surface under the ring varies by several blocks; walls reach this far below it so they meet
    // the ground instead of floating over a dip (below ground they just replace end stone).
    private static final int FOUNDATION = 6;
    private static final Map<String, int[]> GATES = Map.of(
            "north", new int[] {0, -1},
            "south", new int[] {0, 1},
            "east", new int[] {1, 0},
            "west", new int[] {-1, 0});

    private FortressStructures() {
    }

    static FortressResult build(int surfaceY) {
        List<Placement> placements = new ArrayList<>();
        List<GuardPost> guards = new ArrayList<>();
        List<WaveSpawn> waveSpawns = new ArrayList<>();

        curtainWall(surfaceY, placements);
        for (Map.Entry<String, int[]> gate : GATES.entrySet()) {
            gatehouse(surfaceY, gate.getKey(), gate.getValue()[0], gate.getValue()[1], placements, guards, waveSpawns);
        }
        for (int angDeg : new int[] {45, 135, 225, 315}) {
            watchtower(surfaceY, angDeg, placements, guards);
        }
        wallWalkwayGuards(surfaceY, guards);

        return new FortressResult(placements, guards, waveSpawns);
    }

    private static void curtainWall(int surfaceY, List<Placement> out) {
        int rrIn = INNER_R * INNER_R;
        int rrOut = OUTER_R * OUTER_R;
        for (int dx = -OUTER_R - 1; dx <= OUTER_R + 1; dx++) {
            for (int dz = -OUTER_R - 1; dz <= OUTER_R + 1; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 >= rrIn && d2 <= rrOut) {
                    for (int y = surfaceY + 1 - FOUNDATION; y <= surfaceY + HEIGHT; y++) {
                        out.add(new Placement(dx, y, dz, "minecraft:polished_blackstone_bricks"));
                    }
                    out.add(new Placement(dx, surfaceY + HEIGHT + 1, dz, "minecraft:deepslate_bricks"));
                }
            }
        }
        int merlonCount = 64;
        for (int i = 0; i < merlonCount; i += 2) {
            double ang = Math.toRadians(i * 360.0 / merlonCount);
            int x = Geometry.round(OUTER_R * Math.cos(ang));
            int z = Geometry.round(OUTER_R * Math.sin(ang));
            out.add(new Placement(x, surfaceY + HEIGHT + 2, z, "minecraft:polished_blackstone_bricks"));
        }
    }

    /** One gatehouse spanning inner_r..outer_r, a 3-wide x 4-tall opening carved through it, plus
     * the cult's new fire/banner/arch dressing. */
    private static void gatehouse(int surfaceY, String name, int sx, int sz, List<Placement> out,
                                   List<GuardPost> guards, List<WaveSpawn> waveSpawns) {
        int px = -sz;
        int pz = sx;
        int gateY0 = surfaceY + 1;
        int gateY1 = surfaceY + 4;
        int towerH = surfaceY + 11;
        int halfW = 3;

        for (int d = INNER_R; d <= OUTER_R; d++) {
            for (int w = -halfW; w <= halfW; w++) {
                int cx = sx * d + px * w;
                int cz = sz * d + pz * w;
                boolean inOpening = w >= -1 && w <= 1;
                for (int y = surfaceY + 1 - FOUNDATION; y <= towerH; y++) {
                    if (inOpening && y >= gateY0 && y <= gateY1) {
                        continue; // carved-out passage
                    }
                    out.add(new Placement(cx, y, cz, "minecraft:polished_blackstone_bricks"));
                }
            }
        }
        // A paved, cleared path through the gate and a few blocks either side of it, so ground that
        // rises above the fortress's surface height can never block the passage.
        for (int d = INNER_R - 3; d <= OUTER_R + 3; d++) {
            for (int w = -1; w <= 1; w++) {
                int cx = sx * d + px * w;
                int cz = sz * d + pz * w;
                for (int y = surfaceY - 2; y <= surfaceY; y++) {
                    out.add(new Placement(cx, y, cz, "minecraft:polished_blackstone_bricks"));
                }
                for (int y = gateY0; y <= gateY1; y++) {
                    out.add(new Placement(cx, y, cz, "minecraft:air"));
                }
            }
        }
        // roof crenellation at the mass's 4 corners
        for (int w : new int[] {-halfW, halfW}) {
            for (int d : new int[] {INNER_R, OUTER_R}) {
                out.add(new Placement(sx * d + px * w, towerH + 1, sz * d + pz * w, "minecraft:polished_blackstone_bricks"));
            }
        }
        // gate arch: upside-down blackstone brick stairs framing the top of the opening
        for (int w = -1; w <= 1; w++) {
            out.add(new Placement(sx * INNER_R + px * w, gateY1 + 1, sz * INNER_R + pz * w,
                    "minecraft:polished_blackstone_brick_stairs[facing=" + Geometry.cardinalFacing(sx, sz) + ",half=top]"));
        }
        // hanging soul lantern mid-passage
        int midD = (INNER_R + OUTER_R) / 2;
        out.add(new Placement(sx * midD, gateY1 + 1, sz * midD, "minecraft:iron_chain"));
        out.add(new Placement(sx * midD, gateY1, sz * midD, "minecraft:soul_lantern[hanging=true]"));
        // braziers beside the gate (ground level, flush with the wall's inner face so neither
        // dips into the open courtyard) and on the gatehouse roof
        brazier(sx * INNER_R + px * 2, surfaceY + 1, sz * INNER_R + pz * 2, out);
        brazier(sx * INNER_R - px * 2, surfaceY + 1, sz * INNER_R - pz * 2, out);
        brazier(sx * midD, towerH + 1, sz * midD, out);
        // banner hung on the outer face, centred over the opening
        int bx = sx * (OUTER_R + 1);
        int bz = sz * (OUTER_R + 1);
        out.add(new Placement(bx, gateY1 + 2, bz,
                "minecraft:white_wall_banner[facing=" + Geometry.cardinalFacing(sx, sz) + "]", PillarStructures.OMINOUS_BANNER));

        for (int w : new int[] {-2, 2}) {
            guards.add(new GuardPost(sx * (INNER_R - 1) + px * w, surfaceY + 1, sz * (INNER_R - 1) + pz * w, GuardKind.GATEHOUSE));
        }
        for (int w : new int[] {-1, 1}) {
            guards.add(new GuardPost(sx * midD + px * w, towerH + 1, sz * midD + pz * w, GuardKind.GATEHOUSE));
        }
        waveSpawns.add(new WaveSpawn(sx * (INNER_R - 2), surfaceY + 1, sz * (INNER_R - 2), name));
    }

    private static void brazier(int x, int y, int z, List<Placement> out) {
        out.add(new Placement(x, y, z, "minecraft:polished_blackstone"));
        out.add(new Placement(x, y + 1, z, "minecraft:soul_fire"));
    }

    private static void watchtower(int surfaceY, int angDeg, List<Placement> out, List<GuardPost> guards) {
        // gen_island.py centres this at inner_r+thickness/2 (straddling the wall), but that leaves
        // its nearest shell corner (centre - 2*sqrt2) inside the inner_r=14 courtyard boundary -
        // moved out to just past the outer wall face so nothing of it ever reaches the courtyard.
        double towerRadius = OUTER_R + 1.0;
        double ang = Math.toRadians(angDeg);
        int tx = Geometry.round(towerRadius * Math.cos(ang));
        int tz = Geometry.round(towerRadius * Math.sin(ang));
        int top = surfaceY + 10;
        // Solid box, minus a hollow interior from the 2nd floor up to just under the roof - leaves
        // a solid floor (y=surfaceY+1) and a solid roof cap (y=top) a guard can stand on.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = surfaceY + 1 - FOUNDATION; y <= top; y++) {
                    boolean hollow = dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 && y >= surfaceY + 2 && y < top;
                    if (!hollow) {
                        out.add(new Placement(tx + dx, y, tz + dz, "minecraft:deepslate_tiles"));
                    }
                }
            }
        }
        for (int[] corner : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            out.add(new Placement(tx + corner[0], top + 1, tz + corner[1], "minecraft:deepslate_tiles"));
        }
        out.add(new Placement(tx, top + 1, tz, "minecraft:white_banner[rotation="
                + PillarStructures.outwardRotation(Math.cos(ang), Math.sin(ang)) + "]", PillarStructures.OMINOUS_BANNER));
        guards.add(new GuardPost(tx, surfaceY + 2, tz, GuardKind.WALL));
        guards.add(new GuardPost(tx, top + 1, tz, GuardKind.WALL));
    }

    /** A handful of extra guard spots along the plain walkway, between the gates/towers. */
    private static void wallWalkwayGuards(int surfaceY, List<GuardPost> guards) {
        int r = (INNER_R + OUTER_R) / 2;
        int y = surfaceY + HEIGHT + 2;
        for (int angDeg : new int[] {22, 68, 112, 158, 202, 248, 292, 338}) {
            double ang = Math.toRadians(angDeg);
            int x = Geometry.round(r * Math.cos(ang));
            int z = Geometry.round(r * Math.sin(ang));
            guards.add(new GuardPost(x, y, z, GuardKind.WALL));
        }
    }
}

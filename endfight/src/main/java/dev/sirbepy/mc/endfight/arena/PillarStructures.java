package dev.sirbepy.mc.endfight.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.sirbepy.mc.endfight.arena.Geometry.cardinalFacing;
import static dev.sirbepy.mc.endfight.arena.Geometry.round;

/**
 * Per-pillar structures, ported from gen_island.py's generate_pillars: the 5 tallest pillars get a
 * spiral staircase, two outposts and a top balcony, the 5 shortest get a ladder, a halfway outpost,
 * a top balcony and vanilla's cage around the crystal. Rings and stair lanes are circles around the
 * pillar exactly as in the approved mockup.
 *
 * <p>Everything is collected per position, so a later block replaces an earlier one at the same
 * spot: a tread or ladder at an outpost's height becomes part of that floor, and the two blocks
 * above every tread are cleared, which cuts the hole each staircase climbs through.
 */
final class PillarStructures {

    /** The vanilla ominous-banner pattern (verified against the 26.3 jar's BannerPatterns
     * registry keys: rhombus, stripe_bottom, stripe_center, border, stripe_middle,
     * half_horizontal, circle are all real {@code minecraft:} pattern ids). White base comes from
     * using {@code minecraft:white_banner} as the block itself. */
    static final List<BannerPattern> OMINOUS_BANNER = List.of(
            new BannerPattern("cyan", "rhombus"),
            new BannerPattern("light_gray", "stripe_bottom"),
            new BannerPattern("gray", "stripe_center"),
            new BannerPattern("light_gray", "border"),
            new BannerPattern("black", "stripe_middle"),
            new BannerPattern("light_gray", "half_horizontal"),
            new BannerPattern("light_gray", "circle"),
            new BannerPattern("black", "border"));

    /** A Nether war banner: black with a red rise, an orange piglin snout and a black rim. The
     * {@code piglin} and {@code gradient_up} pattern ids are checked against the 26.3 jar's
     * {@code data/minecraft/banner_pattern/} listing. */
    static final List<BannerPattern> PIGLIN_BANNER = List.of(
            new BannerPattern("red", "gradient_up"),
            new BannerPattern("orange", "piglin"),
            new BannerPattern("black", "border"));

    /** One faction's palette. {@code support} is what holds up a stair tread, {@code light} stands
     * on an outpost floor; Nether outposts also get a soul fire on soul soil near the rim. */
    record Theme(Faction faction, String floor, String support, String fence, String stairs, String light,
                 String banner, List<BannerPattern> bannerPatterns, boolean soulFire) {
    }

    static final Theme ILLAGER = new Theme(Faction.ILLAGER, "minecraft:polished_blackstone_bricks",
            "minecraft:polished_blackstone_bricks", "minecraft:dark_oak_fence", "minecraft:dark_oak_stairs",
            "minecraft:soul_lantern[hanging=false]", "minecraft:white_banner", OMINOUS_BANNER, false);
    // Joe's Nether palette (2026-10-09): blackstone, nether bricks, crimson wood, shroomlights, soul fire.
    static final Theme NETHER = new Theme(Faction.NETHER, "minecraft:nether_bricks", "minecraft:blackstone",
            "minecraft:crimson_fence", "minecraft:crimson_stairs", "minecraft:shroomlight", "minecraft:black_banner",
            PIGLIN_BANNER, true);

    private PillarStructures() {
    }

    static Theme theme(Faction faction) {
        return faction == Faction.NETHER ? NETHER : ILLAGER;
    }

    static PillarResult tallPillar(Pillar p, List<Pillar> allPillars) {
        return tallPillar(p, allPillars, Faction.ILLAGER);
    }

    static PillarResult shortPillar(Pillar p, List<Pillar> allPillars) {
        return shortPillar(p, allPillars, Faction.ILLAGER);
    }

    static PillarResult tallPillar(Pillar p, List<Pillar> allPillars, Faction faction) {
        Theme theme = theme(faction);
        Blocks blocks = new Blocks(allPillars);
        List<GuardPost> guards = new ArrayList<>();
        int baseY = p.surfaceY() + 1;
        int balconyY = p.topY() - 4;
        int third = baseY + (balconyY - baseY) / 3;
        int twoThird = baseY + 2 * (balconyY - baseY) / 3;
        double r = p.radius();

        ring(p, theme, blocks, third, r + 1, r + 3, true, true, 3, GuardKind.STAIR_OUTPOST, guards);
        ring(p, theme, blocks, twoThird, r + 1, r + 3, false, true, 2, GuardKind.STAIR_OUTPOST, guards);
        ring(p, theme, blocks, balconyY, r + 0.5, r + 2, true, false, 2, GuardKind.BALCONY, guards);
        List<Tread> treads = spiralStaircase(p, theme, blocks, baseY, balconyY);
        lightSoulFires(theme, blocks);

        return new PillarResult(blocks.placements(), standable(guards, blocks), treads);
    }

    static PillarResult shortPillar(Pillar p, List<Pillar> allPillars, Faction faction) {
        Theme theme = theme(faction);
        Blocks blocks = new Blocks(allPillars);
        List<GuardPost> guards = new ArrayList<>();
        int baseY = p.surfaceY() + 1;
        int balconyY = p.topY() - 4;
        int half = baseY + (balconyY - baseY) / 2;
        double r = p.radius();

        ring(p, theme, blocks, half, r + 1, r + 3, true, true, 2, GuardKind.LADDER_OUTPOST, guards);
        ring(p, theme, blocks, balconyY, r + 0.5, r + 2, false, false, 2, GuardKind.BALCONY, guards);
        ladder(p, blocks, baseY, balconyY);
        cage(p, blocks);
        lightSoulFires(theme, blocks);

        return new PillarResult(blocks.placements(), standable(guards, blocks), List.of());
    }

    /** Vanilla's own cage shape (EndSpikeFeature.placeSpike, isGuarded branch, javap-verified): a
     * 5x5 lattice from topY to topY+3, iron bars on every edge column and the full roof at topY+3. */
    private static void cage(Pillar p, Blocks blocks) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int dy = 0; dy <= 3; dy++) {
                    if (edge || dy == 3) {
                        blocks.put(p.cx() + dx, p.topY() + dy, p.cz() + dz, "minecraft:iron_bars");
                    }
                }
            }
        }
    }

    /** A ladder up the north face, from the ground to the balcony floor, through a hole in each ring
     * floor. A ladder faces away from the block holding it, so north of the pillar it faces north. */
    private static void ladder(Pillar p, Blocks blocks, int baseY, int balconyY) {
        int x = p.cx();
        int z = p.cz() - p.radius() - 1;
        for (int y = baseY; y <= balconyY; y++) {
            blocks.put(x, y, z, "minecraft:ladder[facing=north]");
        }
        // Room to climb off the top onto the balcony.
        blocks.remove(x, balconyY + 1, z);
        blocks.remove(x, balconyY + 2, z);
    }

    /**
     * A 2-wide spiral rising one block per step from the ground to the balcony floor. The outer lane
     * walks a ring of blocks 2 out from the pillar where each block shares a side with the next, so
     * every step is a plain one-forward-one-up stair. Beside each outer tread, one block toward the
     * pillar, sits an inner tread, left out where it would land under another tread. Every tread gets
     * a solid block under it and two clear blocks above it, and a fence rail runs along the outside.
     */
    private static List<Tread> spiralStaircase(Pillar p, Theme theme, Blocks blocks, int baseY, int balconyY) {
        List<int[]> loop = sideConnectedRing(p.cx(), p.cz(), p.radius() + 2.0);

        List<Tread> treads = new ArrayList<>();
        List<String> facings = new ArrayList<>();
        for (int k = 0, y = baseY; y <= balconyY; k++, y++) {
            int[] cell = loop.get(k % loop.size());
            int[] next = loop.get((k + 1) % loop.size());
            treads.add(new Tread(p.cx(), p.cz(), "outer", cell[0], y, cell[1]));
            facings.add(cardinalFacing(next[0] - cell[0], next[1] - cell[1]));
            int[] out = stepRadially(p, cell, 1);
            blocks.put(out[0], y + 1, out[1], theme.fence());
        }
        int outerCount = treads.size();
        long lastInner = Long.MIN_VALUE;
        for (int i = 0; i < outerCount; i++) {
            Tread o = treads.get(i);
            int[] in = stepRadially(p, new int[] {o.x(), o.z()}, -1);
            long column = key(in[0], 0, in[1]);
            if (column == lastInner || underAnOuterTread(treads, outerCount, in[0], o.y(), in[1])) {
                continue;
            }
            lastInner = column;
            treads.add(new Tread(p.cx(), p.cz(), "inner", in[0], o.y(), in[1]));
            facings.add(facings.get(i));
        }

        Map<Long, Tread> treadAt = new LinkedHashMap<>();
        for (Tread t : treads) {
            treadAt.put(key(t.x(), t.y(), t.z()), t);
        }
        for (Tread t : treads) {
            blocks.remove(t.x(), t.y() + 1, t.z());
            blocks.remove(t.x(), t.y() + 2, t.z());
        }
        for (int i = 0; i < treads.size(); i++) {
            Tread t = treads.get(i);
            blocks.put(t.x(), t.y(), t.z(), theme.stairs() + "[facing=" + facings.get(i) + ",half=bottom]");
        }
        for (Tread t : treads) {
            long below = key(t.x(), t.y() - 1, t.z());
            if (!treadAt.containsKey(below) && !inHeadroom(treads, t.x(), t.y() - 1, t.z())) {
                blocks.put(t.x(), t.y() - 1, t.z(), theme.support());
            }
        }
        return treads;
    }

    /** True if an outer tread stands within two blocks above or below (x, y, z)'s column. */
    private static boolean underAnOuterTread(List<Tread> treads, int outerCount, int x, int y, int z) {
        for (int i = 0; i < outerCount; i++) {
            Tread t = treads.get(i);
            if (t.x() == x && t.z() == z && Math.abs(t.y() - y) <= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * The blocks at distance {@code radius} around (cx, cz), in order of angle, one full loop, each
     * sharing a side with the next (a corner-only step gets the in-between block that sits closest to
     * the circle), so a staircase walking it never has to cut a corner.
     */
    static List<int[]> sideConnectedRing(int cx, int cz, double radius) {
        List<int[]> cells = new ArrayList<>();
        int samples = (int) Math.ceil(radius * 64);
        for (int i = 0; i < samples; i++) {
            double theta = 2 * Math.PI * i / samples;
            int[] cell = {round(cx + radius * Math.cos(theta)), round(cz + radius * Math.sin(theta))};
            if (cells.isEmpty() || !java.util.Arrays.equals(cells.get(cells.size() - 1), cell)) {
                cells.add(cell);
            }
        }
        if (cells.size() > 1 && java.util.Arrays.equals(cells.get(0), cells.get(cells.size() - 1))) {
            cells.remove(cells.size() - 1);
        }
        List<int[]> loop = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            int[] a = cells.get(i);
            int[] b = cells.get((i + 1) % cells.size());
            loop.add(a);
            if (Math.abs(b[0] - a[0]) == 1 && Math.abs(b[1] - a[1]) == 1) {
                int[] viaX = {b[0], a[1]};
                int[] viaZ = {a[0], b[1]};
                loop.add(offCircle(viaX, cx, cz, radius) <= offCircle(viaZ, cx, cz, radius) ? viaX : viaZ);
            }
        }
        return loop;
    }

    private static double offCircle(int[] cell, int cx, int cz, double radius) {
        return Math.abs(Math.hypot(cell[0] - cx, cell[1] - cz) - radius);
    }

    /** One block away from (+1) or toward (-1) the pillar, along whichever axis points at it most. */
    private static int[] stepRadially(Pillar p, int[] cell, int direction) {
        int dx = cell[0] - p.cx();
        int dz = cell[1] - p.cz();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] {cell[0] + direction * Integer.signum(dx), cell[1]};
        }
        return new int[] {cell[0], cell[1] + direction * Integer.signum(dz)};
    }

    private static boolean inHeadroom(List<Tread> treads, int x, int y, int z) {
        for (Tread t : treads) {
            if (t.x() == x && t.z() == z && (t.y() + 1 == y || t.y() + 2 == y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A ring platform around the pillar at height y, from {@code inner} to {@code outer} blocks out:
     * floor, a fence rail around the rim, guards and (optionally) a banner facing outward and a
     * light. Guard posts are feet positions, one above the floor.
     */
    private static void ring(Pillar p, Theme theme, Blocks blocks, int y, double inner, double outer, boolean withBanner,
                             boolean withLantern, int guardCount, GuardKind kind, List<GuardPost> guards) {
        double in2 = inner * inner;
        double out2 = outer * outer;
        int reach = (int) Math.ceil(outer) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 >= in2 && d2 <= out2) {
                    blocks.put(p.cx() + dx, y, p.cz() + dz, theme.floor());
                }
            }
        }
        for (int i = 0; i < 16; i++) {
            double ang = Math.toRadians(i * 360.0 / 16);
            blocks.put(round(p.cx() + outer * Math.cos(ang)), y + 1, round(p.cz() + outer * Math.sin(ang)), theme.fence());
        }
        double mid = (inner + outer) / 2;
        if (withLantern) {
            double ang = Math.toRadians(20);
            blocks.put(round(p.cx() + mid * Math.cos(ang)), y + 1, round(p.cz() + mid * Math.sin(ang)), theme.light());
        }
        if (withLantern && theme.soulFire()) {
            // Just inside the rim at 340 degrees: the guards stand between 90 and 270, the light at
            // 20 and the banner at 160, so nobody spawns in it. Lit after the stairs are cut.
            double ang = Math.toRadians(340);
            double rim = outer - 1;
            blocks.fireSpots.add(new int[] {round(p.cx() + rim * Math.cos(ang)), y, round(p.cz() + rim * Math.sin(ang))});
        }
        if (withBanner) {
            double ang = Math.toRadians(160);
            int bx = round(p.cx() + outer * Math.cos(ang));
            int bz = round(p.cz() + outer * Math.sin(ang));
            blocks.put(new Placement(bx, y + 1, bz,
                    theme.banner() + "[rotation=" + outwardRotation(Math.cos(ang), Math.sin(ang)) + "]", theme.bannerPatterns()));
        }
        for (int i = 0; i < guardCount; i++) {
            double ang = Math.toRadians(90 + i * (180.0 / Math.max(guardCount - 1, 1)));
            guards.add(new GuardPost(round(p.cx() + mid * Math.cos(ang)), y + 1, round(p.cz() + mid * Math.sin(ang)), kind,
                    theme.faction()));
        }
    }

    /** A standing banner's rotation (0 = facing south, +x east, 16 steps) facing direction (ux, uz). */
    static int outwardRotation(double ux, double uz) {
        double degrees = Math.toDegrees(Math.atan2(-ux, uz));
        return Math.floorMod((int) Math.round(degrees / 22.5), 16);
    }

    /** Drops posts whose floor was cut away by a staircase hole or whose space is taken. */
    private static List<GuardPost> standable(List<GuardPost> posts, Blocks blocks) {
        List<GuardPost> out = new ArrayList<>();
        for (GuardPost g : posts) {
            if (blocks.has(g.x(), g.y() - 1, g.z()) && !blocks.has(g.x(), g.y(), g.z()) && !blocks.has(g.x(), g.y() + 1, g.z())) {
                out.add(g);
            }
        }
        return out;
    }

    /** Soul fire on soul soil at each spot an outpost asked for, but only where the floor is still
     * floor with nothing above it: a staircase climbing through that spot would leave the fire
     * hanging in its headroom or standing on a stair. */
    private static void lightSoulFires(Theme theme, Blocks blocks) {
        for (int[] spot : blocks.fireSpots) {
            int x = spot[0];
            int y = spot[1];
            int z = spot[2];
            if (theme.floor().equals(blocks.at(x, y, z)) && !blocks.has(x, y + 1, z) && !blocks.has(x, y + 2, z)) {
                blocks.put(x, y, z, "minecraft:soul_soil");
                blocks.put(x, y + 1, z, "minecraft:soul_fire");
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ArenaRegistry.pack(x, y, z);
    }

    /** One pillar's placements by position; never lets anything replace the pillar's obsidian. */
    private static final class Blocks {
        private final List<Pillar> pillars;
        private final Map<Long, Placement> byPos = new LinkedHashMap<>();
        final List<int[]> fireSpots = new ArrayList<>();

        Blocks(List<Pillar> pillars) {
            this.pillars = pillars;
        }

        void put(int x, int y, int z, String blockData) {
            put(new Placement(x, y, z, blockData));
        }

        void put(Placement placement) {
            if (!Geometry.insideAnyPillar(pillars, placement.x(), placement.y(), placement.z())) {
                byPos.put(key(placement.x(), placement.y(), placement.z()), placement);
            }
        }

        void remove(int x, int y, int z) {
            byPos.remove(key(x, y, z));
        }

        boolean has(int x, int y, int z) {
            return byPos.containsKey(key(x, y, z));
        }

        /** The block data placed at (x, y, z), or null. */
        String at(int x, int y, int z) {
            Placement placement = byPos.get(key(x, y, z));
            return placement != null ? placement.blockData() : null;
        }

        List<Placement> placements() {
            return new ArrayList<>(byPos.values());
        }
    }
}

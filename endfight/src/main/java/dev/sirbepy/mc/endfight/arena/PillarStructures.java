package dev.sirbepy.mc.endfight.arena;

import java.util.ArrayList;
import java.util.List;

import static dev.sirbepy.mc.endfight.arena.Geometry.cardinalFacing;
import static dev.sirbepy.mc.endfight.arena.Geometry.round;
import static dev.sirbepy.mc.endfight.arena.Geometry.squarePoint;
import static dev.sirbepy.mc.endfight.arena.Geometry.violatesAnyPillar;

/**
 * Per-pillar structures, ported from gen_island.py's pillar-level functions: the 5 tallest pillars
 * get a spiral staircase + outposts + a top balcony, the 5 shortest get a ladder strip, a halfway
 * outpost, a top balcony and the vanilla cage shape around every one of their crystals.
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

    private PillarStructures() {
    }

    static PillarResult tallPillar(Pillar p, List<Pillar> allPillars) {
        List<Placement> placements = new ArrayList<>();
        List<GuardPost> guards = new ArrayList<>();
        int balconyY = p.topY() - 1 - 3;

        List<Tread> treads = spiralStaircase(p, allPillars, p.surfaceY(), balconyY, placements);

        int third = p.surfaceY() + (balconyY - p.surfaceY()) / 3;
        int twoThird = p.surfaceY() + 2 * (balconyY - p.surfaceY()) / 3;
        // baseRadius is always pillar.radius()+2, never a bare literal: it must scale with the
        // pillar (see outpostRing's javadoc) or a wide pillar's box swallows the whole ring.
        int baseRadius = p.radius() + 2;
        outpostRing(p, allPillars, third, baseRadius, true, true, 3, GuardKind.STAIR_OUTPOST, placements, guards);
        outpostRing(p, allPillars, twoThird, baseRadius, false, true, 2, GuardKind.STAIR_OUTPOST, placements, guards);
        outpostRing(p, allPillars, balconyY, baseRadius, true, false, 2, GuardKind.BALCONY, placements, guards);

        return new PillarResult(placements, guards, treads);
    }

    static PillarResult shortPillar(Pillar p, List<Pillar> allPillars) {
        List<Placement> placements = new ArrayList<>();
        List<GuardPost> guards = new ArrayList<>();
        int balconyY = p.topY() - 1 - 3;

        placements.addAll(cage(p));
        placements.addAll(ladderStrip(p, allPillars));

        int half = p.surfaceY() + (balconyY - p.surfaceY()) / 2;
        int baseRadius = p.radius() + 2;
        outpostRing(p, allPillars, half, baseRadius, true, true, 2, GuardKind.LADDER_OUTPOST, placements, guards);
        outpostRing(p, allPillars, balconyY, baseRadius, false, false, 2, GuardKind.BALCONY, placements, guards);

        return new PillarResult(placements, guards, List.of());
    }

    /** Vanilla's own cage shape (EndSpikeFeature.placeSpike, isGuarded branch, javap-verified): a
     * 5x5 lattice from topY to topY+3, iron bars on every edge column and the full roof at topY+3.
     * Deliberately NOT run through the box/cylinder exclusion check below - this is the one
     * sanctioned "on top of a pillar" exception. */
    private static List<Placement> cage(Pillar p) {
        List<Placement> placements = new ArrayList<>(100);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int dy = 0; dy <= 3; dy++) {
                    if (edge || dy == 3) {
                        placements.add(new Placement(p.cx() + dx, p.topY() + dy, p.cz() + dz, "minecraft:iron_bars"));
                    }
                }
            }
        }
        return placements;
    }

    /** A ladder strip up one face. On a cardinal axis (dz = radius+1, dx = 0), so it's always
     * exactly 1 block outside the respawn box regardless of pillar radius - no diagonal risk the
     * way a ring has, so this needs no square-metric adjustment. */
    private static List<Placement> ladderStrip(Pillar p, List<Pillar> allPillars) {
        List<Placement> placements = new ArrayList<>();
        int z = p.cz() - p.radius() - 1;
        for (int y = p.surfaceY(); y < p.topY() - 4; y++) {
            addIfSafe(placements, allPillars, p.cx(), y, z, "minecraft:ladder[facing=south]");
        }
        return placements;
    }

    private static List<Tread> spiralStaircase(Pillar p, List<Pillar> allPillars, int baseY, int balconyY,
                                                List<Placement> out) {
        List<Tread> treads = new ArrayList<>();
        double innerR = p.radius() + 1.0;
        double outerR = p.radius() + 2.0;
        double railR = outerR + 1.0;
        // Conservative worst-case step: a square-metric lane's actual radius can be up to
        // outerR*sqrt(2) at a 45-degree angle, so size the angle step off that (not outerR alone,
        // the way gen_island.py does for its circular lane) to keep every arc length <= 1 block
        // even at the diagonal; the extra 1.5x margin absorbs the +/-0.5 rounding each endpoint
        // can drift by once it snaps to the integer block grid. See Geometry.squarePoint's javadoc
        // for why this plugin can't use a constant-Euclidean-radius spiral the way the mockup does.
        double angleStep = 1.0 / (outerR * Geometry.SQRT2 * 1.5);

        int i = 0;
        for (int y = baseY; y < balconyY; y++, i++) {
            double theta = i * angleStep;
            String facing = cardinalFacing(-Math.sin(theta), Math.cos(theta));

            double[] inner = squarePoint(p.cx(), p.cz(), innerR, theta);
            double[] outer = squarePoint(p.cx(), p.cz(), outerR, theta);
            placeTread(p, allPillars, inner, y, facing, "inner", treads, out);
            placeTread(p, allPillars, outer, y, facing, "outer", treads, out);

            double[] rail = squarePoint(p.cx(), p.cz(), railR, theta);
            addIfSafe(out, allPillars, round(rail[0]), y + 1, round(rail[1]), "minecraft:dark_oak_fence");
        }
        return treads;
    }

    private static void placeTread(Pillar p, List<Pillar> allPillars, double[] xz, int y, String facing,
                                    String lane, List<Tread> treads, List<Placement> out) {
        int x = round(xz[0]);
        int z = round(xz[1]);
        addIfSafe(out, allPillars, x, y, z, "minecraft:dark_oak_stairs[facing=" + facing + ",half=bottom]");
        addIfSafe(out, allPillars, x, y - 1, z, "minecraft:polished_blackstone_bricks");
        treads.add(new Tread(p.cx(), p.cz(), lane, x, y, z));
    }

    /**
     * A ring platform around the pillar at height y, from {@code baseRadius} to
     * {@code baseRadius + 2}: floor, a fence rail around the rim, guards, and (optionally) a
     * banner and a lantern. Unlike gen_island.py's circular annulus, this fills a SQUARE
     * (Chebyshev) annulus with {@code baseRadius >= pillar.radius() + 2} - a guaranteed 1-block
     * safety margin outside the respawn-clear box at every angle, not just the cardinals (see
     * Geometry.squarePoint). The corners read a little more angular than the mockup's round
     * platform; functionally identical.
     */
    private static void outpostRing(Pillar p, List<Pillar> allPillars, int y, int baseRadius, boolean withBanner,
                                     boolean withLantern, int guardCount, GuardKind kind, List<Placement> out,
                                     List<GuardPost> guards) {
        int outerRadius = baseRadius + 2;
        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                if (cheb >= baseRadius && cheb <= outerRadius) {
                    addIfSafe(out, allPillars, p.cx() + dx, y, p.cz() + dz, "minecraft:polished_blackstone_bricks");
                }
            }
        }
        int midR = (baseRadius + outerRadius) / 2;
        for (int i = 0; i < 16; i++) {
            double ang = Math.toRadians(i * 360.0 / 16);
            double[] rim = squarePoint(p.cx(), p.cz(), outerRadius, ang);
            addIfSafe(out, allPillars, round(rim[0]), y + 1, round(rim[1]), "minecraft:dark_oak_fence");
        }
        if (withLantern) {
            double[] lp = squarePoint(p.cx(), p.cz(), midR, Math.toRadians(20));
            addIfSafe(out, allPillars, round(lp[0]), y + 1, round(lp[1]), "minecraft:soul_lantern[hanging=false]");
        }
        if (withBanner) {
            double[] bp = squarePoint(p.cx(), p.cz(), outerRadius, Math.toRadians(160));
            int bx = round(bp[0]);
            int bz = round(bp[1]);
            if (!violatesAnyPillar(allPillars, bx, y + 1, bz)) {
                out.add(new Placement(bx, y + 1, bz, "minecraft:white_banner[rotation=8]", OMINOUS_BANNER));
            }
        }
        for (int i = 0; i < guardCount; i++) {
            double ang = Math.toRadians(90 + i * (180.0 / Math.max(guardCount - 1, 1)));
            double[] gp = squarePoint(p.cx(), p.cz(), midR, ang);
            int gx = round(gp[0]);
            int gz = round(gp[1]);
            if (!violatesAnyPillar(allPillars, gx, y, gz)) {
                guards.add(new GuardPost(gx, y, gz, kind));
            }
        }
    }

    static void addIfSafe(List<Placement> out, List<Pillar> allPillars, int x, int y, int z, String blockData) {
        if (!violatesAnyPillar(allPillars, x, y, z)) {
            out.add(new Placement(x, y, z, blockData));
        }
    }
}

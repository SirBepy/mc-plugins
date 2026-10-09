package dev.sirbepy.mc.endfight.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure geometry: 10 {@link Pillar}s in, an ordered list of block placements plus named guard posts
 * and wave spawns out. No world access, no randomness - same input always gives the same output,
 * so this is the one class {@code ArenaLayoutTest} exercises directly.
 *
 * <p>Ports gen_island.py's {@code generate_pillars}/{@code generate_fortress} for the overall
 * shape. Deviations from the mockup, all for the real world's sake: the watchtowers sit just past
 * the outer wall, the fortress has foundations and paved gate paths for uneven ground, and stairs
 * and ladders cut holes through the floors they climb past (see {@link PillarStructures} and
 * {@link FortressStructures}).
 */
public final class ArenaLayout {

    private final List<Placement> placements;
    private final List<GuardPost> guardPosts;
    private final List<WaveSpawn> waveSpawns;
    private final List<Tread> treads;

    private ArenaLayout(List<Placement> placements, List<GuardPost> guardPosts, List<WaveSpawn> waveSpawns,
                         List<Tread> treads) {
        this.placements = List.copyOf(placements);
        this.guardPosts = List.copyOf(guardPosts);
        this.waveSpawns = List.copyOf(waveSpawns);
        this.treads = List.copyOf(treads);
    }

    /** The layout with the fortress and the altar both standing on {@code surfaceY}. */
    public static ArenaLayout build(List<Pillar> pillars, int surfaceY) {
        return build(pillars, new Terrain(surfaceY, surfaceY));
    }

    /**
     * @param pillars   exactly the pillars {@link PillarScanner} found (10 on a healthy world, may
     *                  be fewer if some chunks weren't generated yet)
     * @param terrain   the ground the fortress ring and the altar stand on
     */
    public static ArenaLayout build(List<Pillar> pillars, Terrain terrain) {
        List<Pillar> byHeight = new ArrayList<>(pillars);
        byHeight.sort(Comparator.comparingInt(Pillar::topY));
        int tallFrom = Math.max(0, byHeight.size() - 5);

        List<Placement> placements = new ArrayList<>();
        List<GuardPost> guardPosts = new ArrayList<>();
        List<WaveSpawn> waveSpawns = new ArrayList<>();
        List<Tread> treads = new ArrayList<>();

        Map<Pillar, Faction> factions = factions(pillars);
        for (int i = 0; i < byHeight.size(); i++) {
            Pillar p = byHeight.get(i);
            Faction faction = factions.get(p);
            PillarResult result = i >= tallFrom
                    ? PillarStructures.tallPillar(p, pillars, faction)
                    : PillarStructures.shortPillar(p, pillars, faction);
            placements.addAll(result.placements());
            guardPosts.addAll(result.guardPosts());
            treads.addAll(result.treads());
        }

        FortressResult fortress = FortressStructures.build(terrain.fortressY());
        placements.addAll(fortress.placements());
        guardPosts.addAll(fortress.guardPosts());
        waveSpawns.addAll(fortress.waveSpawns());

        placements.addAll(AltarStructures.build(terrain.altarY()).placements());

        return new ArenaLayout(placements, guardPosts, waveSpawns, treads);
    }

    /**
     * Pillars alternate illager, Nether, illager... walking the ring by angle around the portal
     * (Joe, 2026-10-09), starting from the one nearest due east (angle 0), so the same pillars
     * always get the same faction. Vanilla's 10 pillars sit evenly around the ring, so this splits
     * them 5 and 5 with no two neighbours alike.
     */
    public static Map<Pillar, Faction> factions(List<Pillar> pillars) {
        List<Pillar> byAngle = new ArrayList<>(pillars);
        byAngle.sort(Comparator.comparingDouble(ArenaLayout::angle));
        Map<Pillar, Faction> out = new HashMap<>();
        for (int i = 0; i < byAngle.size(); i++) {
            out.put(byAngle.get(i), i % 2 == 0 ? Faction.ILLAGER : Faction.NETHER);
        }
        return out;
    }

    /** 0 to 2pi, counting from +x towards +z. */
    private static double angle(Pillar p) {
        double a = Math.atan2(p.cz(), p.cx());
        return a < 0 ? a + 2 * Math.PI : a;
    }

    public List<Placement> placements() {
        return placements;
    }

    public List<GuardPost> guardPosts() {
        return guardPosts;
    }

    public List<WaveSpawn> waveSpawns() {
        return waveSpawns;
    }

    /** Stair treads in climb order, for continuity tests (see gen_island.py's measure_stair_gaps). */
    public List<Tread> treads() {
        return treads;
    }
}

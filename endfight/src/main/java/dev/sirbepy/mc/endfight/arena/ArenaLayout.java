package dev.sirbepy.mc.endfight.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /**
     * @param pillars   exactly the pillars {@link PillarScanner} found (10 on a healthy world, may
     *                  be fewer if some chunks weren't generated yet)
     * @param surfaceY  the End island's surface height at the portal, used for the fortress ring
     */
    public static ArenaLayout build(List<Pillar> pillars, int surfaceY) {
        List<Pillar> byHeight = new ArrayList<>(pillars);
        byHeight.sort(Comparator.comparingInt(Pillar::topY));
        int tallFrom = Math.max(0, byHeight.size() - 5);

        List<Placement> placements = new ArrayList<>();
        List<GuardPost> guardPosts = new ArrayList<>();
        List<WaveSpawn> waveSpawns = new ArrayList<>();
        List<Tread> treads = new ArrayList<>();

        for (int i = 0; i < byHeight.size(); i++) {
            Pillar p = byHeight.get(i);
            PillarResult result = i >= tallFrom
                    ? PillarStructures.tallPillar(p, pillars)
                    : PillarStructures.shortPillar(p, pillars);
            placements.addAll(result.placements());
            guardPosts.addAll(result.guardPosts());
            treads.addAll(result.treads());
        }

        FortressResult fortress = FortressStructures.build(surfaceY);
        placements.addAll(fortress.placements());
        guardPosts.addAll(fortress.guardPosts());
        waveSpawns.addAll(fortress.waveSpawns());

        placements.addAll(AltarStructures.build(surfaceY).placements());

        return new ArenaLayout(placements, guardPosts, waveSpawns, treads);
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

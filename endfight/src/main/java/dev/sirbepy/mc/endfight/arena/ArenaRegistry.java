package dev.sirbepy.mc.endfight.arena;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The live, in-memory picture of the arena: which block positions belong to it (for
 * {@link ArenaProtection}), and the guard posts / wave spawns a later phase will use. Rebuilt
 * wholesale from a fresh {@link ArenaLayout} every time the arena is (re)built or the registry is
 * restored from {@code state.yml} on enable - never persisted itself.
 */
public final class ArenaRegistry {

    private final List<ArenaListener> listeners = new CopyOnWriteArrayList<>();

    private volatile Set<Long> arenaBlocks = Set.of();
    private volatile List<GuardPost> guardPosts = List.of();
    private volatile List<WaveSpawn> waveSpawns = List.of();
    private volatile List<Pillar> pillars = List.of();
    private volatile boolean built = false;
    private volatile Terrain terrain;

    public void registerListener(ArenaListener listener) {
        listeners.add(listener);
    }

    /** Replaces the whole registry from a freshly computed layout and notifies listeners. */
    public void update(List<Pillar> pillars, ArenaLayout layout, boolean built) {
        update(pillars, layout, built, null);
    }

    /** As above, also remembering the ground the layout stands on ({@code null} if unknown). */
    public void update(List<Pillar> pillars, ArenaLayout layout, boolean built, Terrain terrain) {
        this.terrain = terrain;
        Set<Long> blocks = new HashSet<>(layout.placements().size() * 2);
        for (Placement placement : layout.placements()) {
            blocks.add(pack(placement.x(), placement.y(), placement.z()));
        }
        this.arenaBlocks = Set.copyOf(blocks);
        this.guardPosts = layout.guardPosts();
        this.waveSpawns = layout.waveSpawns();
        this.pillars = List.copyOf(pillars);
        this.built = built;
        for (ArenaListener listener : listeners) {
            listener.onArenaBuilt(this);
        }
    }

    /** Drops every tracked block, e.g. after {@code endfight clear}. Guard posts/wave spawns and
     * the pillar list are kept (the structures they're over still physically exist until the next
     * build overwrites them; only the protection/lookup set needs to go empty). */
    public void clearBlocks() {
        this.arenaBlocks = Set.of();
        this.built = false;
    }

    public boolean isArenaBlock(int x, int y, int z) {
        return arenaBlocks.contains(pack(x, y, z));
    }

    /** The ground the fortress and altar stand on, or null before any build or restore. */
    public Terrain terrain() {
        return terrain;
    }

    public boolean isBuilt() {
        return built;
    }

    public int blockCount() {
        return arenaBlocks.size();
    }

    public List<GuardPost> guardPosts() {
        return guardPosts;
    }

    public List<WaveSpawn> waveSpawns() {
        return waveSpawns;
    }

    public List<Pillar> pillars() {
        return pillars;
    }

    Set<Long> arenaBlockKeys() {
        return arenaBlocks;
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }

    static int[] unpack(long packed) {
        int x = (int) (packed >> 38);
        int y = (int) ((packed >> 26) & 0xFFF);
        int z = (int) (packed & 0x3FFFFFF);
        // sign-extend each field back out of its packed width
        x = (x << (32 - 26)) >> (32 - 26);
        y = (y << (32 - 12)) >> (32 - 12);
        z = (z << (32 - 26)) >> (32 - 26);
        return new int[] {x, y, z};
    }
}

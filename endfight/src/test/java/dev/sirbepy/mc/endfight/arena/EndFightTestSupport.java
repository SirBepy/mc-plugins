package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Material;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

/** Shared fixtures: the 10 vanilla pillar centres with a fixed (not vanilla-shuffled, but
 * same shape) radius 2..5 / height 76..103 assignment, exactly as gen_island.py's own sample
 * layout describes it, so {@code ArenaLayoutTest}, {@code PillarScannerTest} and
 * {@code ArenaServiceTest} all agree on what "the sample pillar set" looks like. */
final class EndFightTestSupport {

    private EndFightTestSupport() {
    }

    static final int SURFACE_Y = 64;

    /** idx == i (no shuffle - a fixed stand-in is fine per the task brief), giving the same
     * radius 2..5 / height 76..103 spread vanilla's own idx formula would. */
    static List<Pillar> samplePillars() {
        List<int[]> centers = PillarScanner.vanillaCenters();
        List<Pillar> pillars = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            int radius = 2 + i / 3;
            int topY = 76 + i * 3;
            int[] c = centers.get(i);
            pillars.add(new Pillar(c[0], c[1], radius, topY, SURFACE_Y));
        }
        return pillars;
    }

    /** Writes just enough blocks for {@link PillarScanner} to read each pillar back: a disk of
     * obsidian at topY-1 and one end_stone block a few blocks outside. */
    static void seedWorld(WorldMock world, List<Pillar> pillars) {
        for (Pillar p : pillars) {
            int r = p.radius();
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (dx * dx + dz * dz <= r * r + 1) {
                        world.getBlockAt(p.cx() + dx, p.topY() - 1, p.cz() + dz).setType(Material.OBSIDIAN);
                    }
                }
            }
            world.getBlockAt(p.cx() + r + 3, p.surfaceY(), p.cz()).setType(Material.END_STONE);
        }
    }
}

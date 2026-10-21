package dev.sirbepy.mc.minibosses.engine;

import dev.sirbepy.mc.minibosses.arena.Arena;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FightAreaTest extends EngineTestBase {

    @Test
    void clampPullsAnyPointInsideAndLeavesInsidePointsAlone() {
        FightArea area = area();
        for (Location far : new Location[] {at(-50, 64, 15), at(80, 64, 15), at(15, 64, -9), at(15, 64, 99),
                at(15, 5, 15), at(15, 500, 15), at(-5, 5, 99)}) {
            assertFalse(area.contains(far));
            assertTrue(area.contains(area.clamp(far)), "clamped " + far + " must be inside");
        }
        Location inside = at(12.5, 66, 7.25);
        Location same = area.clamp(inside);
        assertEquals(inside.getX(), same.getX());
        assertEquals(inside.getY(), same.getY());
        assertEquals(inside.getZ(), same.getZ());
    }

    @Test
    void randomGroundPointsNeverFallOutsideEvenFromTheEdgeOrOutside() {
        FightArea area = area();
        Random rng = new Random(7);
        for (Location near : new Location[] {at(29.5, 64, 29.5), at(0.2, 64, 15), at(15, 64, 15), at(100, 64, 100)}) {
            for (int i = 0; i < 300; i++) {
                Location point = area.randomGroundPoint(rng, near, 20);
                assertTrue(area.contains(point), "point " + point + " near " + near + " left the area");
            }
        }
    }

    @Test
    void containsHonoursTheWorldAndTheBoxEdges() {
        FightArea area = area();
        assertTrue(area.contains(at(0, 60, 0)));
        assertTrue(area.contains(at(29.99, 79.99, 29.99)));
        assertFalse(area.contains(at(30, 64, 15)), "the max edge belongs to the next block");
        assertFalse(area.contains(new Location(server.addSimpleWorld("other"), 15, 64, 15)));
        assertFalse(area.contains(null));
    }

    @Test
    void aroundBuildsAThirtyBlockSquareCentredOnTheSpawnPoint() {
        FightArea area = FightArea.around(at(100, 64, -40), 15, 20);
        assertTrue(area.contains(at(100, 64, -40)));
        assertTrue(area.contains(at(85.1, 64, -54.9)));
        assertFalse(area.contains(at(84.9, 64, -40)));
        assertFalse(area.contains(at(115.1, 64, -40)));
        assertEquals(30, area.box().getWidthX(), 1.0e-9);
        assertEquals(30, area.box().getWidthZ(), 1.0e-9);
        assertTrue(area.contains(at(100, 83.9, -40)), "20 blocks of headroom for flyers and jumpers");
        assertFalse(area.contains(at(100, 84.1, -40)));
    }

    @Test
    void anArenaBecomesAFightAreaOverTheSameBox() {
        Arena arena = Arena.ofCorners("overgrown_sentinel", world, 0, 64, 0, 29, 80, 29);
        FightArea area = FightArea.of(arena);
        assertTrue(area.contains(at(29.9, 64, 29.9)));
        assertFalse(area.contains(at(30.1, 64, 10)));
    }
}

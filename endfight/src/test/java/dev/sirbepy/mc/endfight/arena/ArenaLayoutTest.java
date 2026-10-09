package dev.sirbepy.mc.endfight.arena;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaLayoutTest {

    private final List<Pillar> pillars = EndFightTestSupport.samplePillars();
    private final ArenaLayout layout = ArenaLayout.build(pillars, EndFightTestSupport.SURFACE_Y);

    private List<Pillar> tallPillars() {
        List<Pillar> sorted = new ArrayList<>(pillars);
        sorted.sort(Comparator.comparingInt(Pillar::topY));
        return sorted.subList(5, 10);
    }

    private List<Pillar> shortPillars() {
        List<Pillar> sorted = new ArrayList<>(pillars);
        sorted.sort(Comparator.comparingInt(Pillar::topY));
        return sorted.subList(0, 5);
    }

    @Test
    void sameInputAlwaysGivesTheSameOutput() {
        ArenaLayout again = ArenaLayout.build(pillars, EndFightTestSupport.SURFACE_Y);

        assertEquals(layout.placements(), again.placements());
        assertEquals(layout.guardPosts(), again.guardPosts());
        assertEquals(layout.waveSpawns(), again.waveSpawns());
    }

    @Test
    void everyConsecutiveTreadIsChebyshevAdjacentAndExactlyOneStepHigher() {
        assertFalse(layout.treads().isEmpty());
        Map<String, List<Tread>> byPillarAndLane = new HashMap<>();
        for (Tread t : layout.treads()) {
            byPillarAndLane.computeIfAbsent(t.pillarCx() + "," + t.pillarCz() + "," + t.lane(), k -> new ArrayList<>()).add(t);
        }
        for (List<Tread> treads : byPillarAndLane.values()) {
            for (int i = 1; i < treads.size(); i++) {
                Tread prev = treads.get(i - 1);
                Tread cur = treads.get(i);
                int chebyshev = Math.max(Math.abs(cur.x() - prev.x()), Math.abs(cur.z() - prev.z()));
                assertTrue(chebyshev <= 1, "gap of " + chebyshev + " between " + prev + " and " + cur);
                assertEquals(prev.y() + 1, cur.y(), "tread should climb exactly 1 block at a time: " + prev + " -> " + cur);
            }
        }
    }

    @Test
    void everyTreadHasASolidSupportBlockDirectlyBelowIt() {
        Set<String> solidBelow = new HashSet<>();
        for (Placement p : layout.placements()) {
            if (p.blockData().equals("minecraft:polished_blackstone_bricks")) {
                solidBelow.add(p.x() + "," + p.y() + "," + p.z());
            }
        }
        for (Tread t : layout.treads()) {
            assertTrue(solidBelow.contains(t.x() + "," + (t.y() - 1) + "," + t.z()),
                    "no support under tread " + t);
        }
    }

    @Test
    void nothingIsPlacedInsideAnyPillarsCylinderOrItsRespawnClearBox() {
        for (Placement p : layout.placements()) {
            if (p.blockData().equals("minecraft:iron_bars")) {
                continue; // the cage is the one sanctioned on-top-of-a-pillar exception
            }
            assertFalse(Geometry.violatesAnyPillar(pillars, p.x(), p.y(), p.z()),
                    "placement " + p + " is inside a pillar's cylinder or respawn-clear box");
        }
    }

    @Test
    void allFiveShortPillarsGetTheVanillaCageShape() {
        Set<String> ironBarRoofs = new HashSet<>();
        for (Placement p : layout.placements()) {
            if (p.blockData().equals("minecraft:iron_bars")) {
                ironBarRoofs.add(p.x() + "," + p.y() + "," + p.z());
            }
        }
        for (Pillar p : shortPillars()) {
            assertTrue(ironBarRoofs.contains(p.cx() + "," + (p.topY() + 3) + "," + p.cz()),
                    "no cage roof over short pillar " + p);
        }
        for (Pillar p : tallPillars()) {
            assertFalse(ironBarRoofs.contains(p.cx() + "," + (p.topY() + 3) + "," + p.cz()),
                    "a tall pillar should not be caged: " + p);
        }
    }

    @Test
    void allFiveTallPillarsGetASpiralStaircase() {
        for (Pillar p : tallPillars()) {
            assertTrue(layout.treads().stream().anyMatch(t -> t.pillarCx() == p.cx() && t.pillarCz() == p.cz()),
                    "no staircase treads for tall pillar " + p);
        }
        for (Pillar p : shortPillars()) {
            assertTrue(layout.treads().stream().noneMatch(t -> t.pillarCx() == p.cx() && t.pillarCz() == p.cz()),
                    "a short pillar should not have stairs: " + p);
        }
    }

    @Test
    void everyPillarHasAtLeastOneNearbyGuardPost() {
        for (Pillar p : pillars) {
            boolean hasGuard = layout.guardPosts().stream()
                    .anyMatch(g -> Math.max(Math.abs(g.x() - p.cx()), Math.abs(g.z() - p.cz())) <= 12);
            assertTrue(hasGuard, "no guard post near pillar " + p);
        }
    }

    @Test
    void fortressBlocksStayWithinRadius22AndNeverAboveTheCourtyard() {
        FortressResult fortress = FortressStructures.build(EndFightTestSupport.SURFACE_Y);
        for (Placement p : fortress.placements()) {
            double distance = Math.hypot(p.x(), p.z());
            assertTrue(distance <= 22.0, "fortress block " + p + " is further than 22 from the portal");
            assertTrue(distance >= 14.0, "fortress block " + p + " is above the open courtyard");
        }
    }

    @Test
    void fourWaveSpawnsSitJustInsideTheGatesOnTheCourtyardSide() {
        assertEquals(4, layout.waveSpawns().size());
        Set<String> gates = new HashSet<>();
        for (WaveSpawn spawn : layout.waveSpawns()) {
            gates.add(spawn.gate());
            double distance = Math.hypot(spawn.x(), spawn.z());
            assertTrue(distance < 14.0, "wave spawn " + spawn + " should be on the courtyard side of the wall");
        }
        assertEquals(Set.of("north", "south", "east", "west"), gates);
    }
}

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

    // ArenaBuilder skips a block whose id the server rejects with only a log warning, so a renamed
    // block (26.x calls the chain iron_chain) would quietly leave holes in the arena.
    @Test
    void everyPlacementNamesABlockTheServerKnows() {
        for (Placement placement : layout.placements()) {
            String id = placement.blockData().replaceFirst("\\[.*$", "");
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(id);
            assertTrue(material != null && material.isBlock(), "unknown block " + id + " at "
                    + placement.x() + "," + placement.y() + "," + placement.z());
        }
    }

    @Test
    void everyConsecutiveTreadIsChebyshevAdjacentAndExactlyOneStepHigher() {
        assertFalse(layout.treads().isEmpty());
        Map<String, List<Tread>> byPillarAndLane = new HashMap<>();
        for (Tread t : layout.treads()) {
            byPillarAndLane.computeIfAbsent(t.pillarCx() + "," + t.pillarCz() + "," + t.lane(), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<String, List<Tread>> lane : byPillarAndLane.entrySet()) {
            if (!lane.getKey().endsWith(",outer")) {
                continue;
            }
            List<Tread> treads = lane.getValue();
            for (int i = 1; i < treads.size(); i++) {
                Tread prev = treads.get(i - 1);
                Tread cur = treads.get(i);
                int sideSteps = Math.abs(cur.x() - prev.x()) + Math.abs(cur.z() - prev.z());
                assertEquals(1, sideSteps, "the walking lane should step one block to the side between " + prev + " and " + cur);
                assertEquals(prev.y() + 1, cur.y(), "tread should climb exactly 1 block at a time: " + prev + " -> " + cur);
            }
        }
    }

    @Test
    void everyInnerTreadSitsBesideAnOuterTreadAtTheSameHeight() {
        Set<String> outer = new HashSet<>();
        for (Tread t : layout.treads()) {
            if (t.lane().equals("outer")) {
                outer.add(t.pillarCx() + "," + t.pillarCz() + "," + t.y() + ":" + t.x() + "," + t.z());
            }
        }
        for (Tread t : layout.treads()) {
            if (!t.lane().equals("inner")) {
                continue;
            }
            String prefix = t.pillarCx() + "," + t.pillarCz() + "," + t.y() + ":";
            boolean beside = outer.contains(prefix + (t.x() + 1) + "," + t.z()) || outer.contains(prefix + (t.x() - 1) + "," + t.z())
                    || outer.contains(prefix + t.x() + "," + (t.z() + 1)) || outer.contains(prefix + t.x() + "," + (t.z() - 1));
            assertTrue(beside, "inner tread " + t + " has no outer tread beside it");
        }
    }

    @Test
    void everyTreadStandsOnASolidBlockOrTheTreadBelowIt() {
        Map<String, String> at = blocksByPosition();
        for (Tread t : layout.treads()) {
            String below = at.get(t.x() + "," + (t.y() - 1) + "," + t.z());
            assertTrue(below != null && !below.equals("minecraft:air"), "nothing under tread " + t);
        }
    }

    @Test
    void everyTreadHasTwoClearBlocksAboveIt() {
        Map<String, String> at = blocksByPosition();
        for (Tread t : layout.treads()) {
            for (int dy = 1; dy <= 2; dy++) {
                String above = at.get(t.x() + "," + (t.y() + dy) + "," + t.z());
                assertTrue(above == null || above.equals("minecraft:air"),
                        "tread " + t + " has " + above + " " + dy + " above it, so nobody can climb past");
            }
        }
    }

    @Test
    void eachSpiralClimbsAllTheWayToItsBalconyFloor() {
        for (Pillar p : tallPillars()) {
            int top = layout.treads().stream().filter(t -> t.pillarCx() == p.cx() && t.pillarCz() == p.cz())
                    .mapToInt(Tread::y).max().orElseThrow();
            assertEquals(p.topY() - 4, top, "stairs on " + p + " stop short of the balcony");
        }
    }

    @Test
    void laddersFaceAwayFromThePillarHoldingThemAndReachTheBalcony() {
        for (Pillar p : shortPillars()) {
            List<Placement> ladders = layout.placements().stream()
                    .filter(pl -> pl.blockData().startsWith("minecraft:ladder")
                            && Math.abs(pl.x() - p.cx()) <= p.radius() + 1 && Math.abs(pl.z() - p.cz()) <= p.radius() + 1)
                    .toList();
            assertFalse(ladders.isEmpty(), "no ladder on " + p);
            for (Placement ladder : ladders) {
                assertEquals("minecraft:ladder[facing=north]", ladder.blockData());
                // facing north means the block holding it is to the south (+z)
                assertTrue(Geometry.insideCylinder(p, ladder.x(), ladder.y(), ladder.z() + 1),
                        "ladder " + ladder + " has no obsidian behind it");
            }
            assertEquals(p.topY() - 4, ladders.stream().mapToInt(Placement::y).max().orElseThrow(),
                    "ladder on " + p + " stops short of the balcony");
        }
    }

    @Test
    void pillarGuardPostsStandOnAFloorWithRoomAbove() {
        Map<String, String> at = blocksByPosition();
        for (GuardPost g : layout.guardPosts()) {
            if (g.kind() == GuardKind.GATEHOUSE || g.kind() == GuardKind.WALL) {
                continue;
            }
            assertTrue(at.containsKey(g.x() + "," + (g.y() - 1) + "," + g.z()), "no floor under guard post " + g);
            assertFalse(at.containsKey(g.x() + "," + g.y() + "," + g.z()), "guard post " + g + " is inside a block");
            assertFalse(at.containsKey(g.x() + "," + (g.y() + 1) + "," + g.z()), "no headroom at guard post " + g);
        }
    }

    @Test
    void nothingReplacesAPillarsObsidian() {
        for (Placement p : layout.placements()) {
            assertFalse(Geometry.insideAnyPillar(pillars, p.x(), p.y(), p.z()), "placement " + p + " is inside a pillar");
        }
    }

    /** The block each position ends up as, last placement winning, the way ArenaBuilder applies them. */
    private Map<String, String> blocksByPosition() {
        Map<String, String> at = new HashMap<>();
        for (Placement p : layout.placements()) {
            at.put(p.x() + "," + p.y() + "," + p.z(), p.blockData());
        }
        return at;
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
    void fortressBlocksStayWithinRadius22AndNeverAboveTheCourtyardExceptTheAltar() {
        FortressResult fortress = FortressStructures.build(EndFightTestSupport.SURFACE_Y);
        Set<String> altarPositions = new HashSet<>();
        for (Placement p : AltarStructures.build(EndFightTestSupport.SURFACE_Y).placements()) {
            altarPositions.add(p.x() + "," + p.y() + "," + p.z());
        }
        List<Placement> all = new ArrayList<>(fortress.placements());
        all.addAll(AltarStructures.build(EndFightTestSupport.SURFACE_Y).placements());
        for (Placement p : all) {
            double distance = Math.hypot(p.x(), p.z());
            assertTrue(distance <= 22.0, "fortress block " + p + " is further than 22 from the portal");
            boolean isAltar = altarPositions.contains(p.x() + "," + p.y() + "," + p.z());
            // Paving at or below the surface and air cleared out of the gate paths may reach
            // inside, and so does the altar itself - a small dais deliberately placed inside the
            // courtyard, just north of the portal (Joe, 2026-10-09).
            if (p.y() > EndFightTestSupport.SURFACE_Y && !p.blockData().equals("minecraft:air") && !isAltar) {
                assertTrue(distance >= 14.0, "fortress block " + p + " is above the open courtyard");
            }
        }
    }

    @Test
    void altarClickableBlockSitsWellInsideTheCourtyard() {
        AltarStructures.AltarResult altar = AltarStructures.build(EndFightTestSupport.SURFACE_Y);
        double distance = Math.hypot(altar.clickX(), altar.clickZ());
        assertTrue(distance <= 13.0, "the altar's clickable block should stay within radius 13 of the portal");
    }

    @Test
    void noAltarBlockSitsAboveThePortal() {
        for (Placement p : AltarStructures.build(EndFightTestSupport.SURFACE_Y).placements()) {
            boolean overPortal = Math.abs(p.x()) <= 2 && Math.abs(p.z()) <= 2;
            assertFalse(overPortal, "altar block " + p + " is above the portal");
        }
    }

    @Test
    void everyAltarBlockIsFoldedIntoTheFullArenaLayoutAndSoProtected() {
        AltarStructures.AltarResult altar = AltarStructures.build(EndFightTestSupport.SURFACE_Y);
        assertFalse(altar.placements().isEmpty());
        Set<String> layoutPositions = new HashSet<>();
        for (Placement p : layout.placements()) {
            layoutPositions.add(p.x() + "," + p.y() + "," + p.z());
        }
        for (Placement p : altar.placements()) {
            assertTrue(layoutPositions.contains(p.x() + "," + p.y() + "," + p.z()), "altar block " + p + " missing from the full arena layout");
        }
    }

    @Test
    void everyGatePassageIsClearedAndPaved() {
        FortressResult fortress = FortressStructures.build(EndFightTestSupport.SURFACE_Y);
        Map<String, String> at = new HashMap<>();
        for (Placement p : fortress.placements()) {
            at.put(p.x() + "," + p.y() + "," + p.z(), p.blockData());
        }
        int y = EndFightTestSupport.SURFACE_Y;
        for (int[] gate : new int[][] {{0, -1}, {0, 1}, {1, 0}, {-1, 0}}) {
            for (int d = 11; d <= 20; d++) {
                int x = gate[0] * d;
                int z = gate[1] * d;
                assertEquals("minecraft:polished_blackstone_bricks", at.get(x + "," + y + "," + z), "no paving at " + x + "," + z);
                assertEquals("minecraft:air", at.get(x + "," + (y + 1) + "," + z), "gate path blocked at " + x + "," + z);
                assertEquals("minecraft:air", at.get(x + "," + (y + 2) + "," + z), "gate path blocked at " + x + "," + z);
            }
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

    // Joe, 2026-10-09: every other pillar around the ring is Nether-themed.
    @Test
    void pillarsAlternateIllagerAndNetherAroundTheRing() {
        Map<Pillar, Faction> factions = ArenaLayout.factions(pillars);
        List<Pillar> byAngle = new ArrayList<>(pillars);
        byAngle.sort(Comparator.comparingDouble(p -> {
            double a = Math.atan2(p.cz(), p.cx());
            return a < 0 ? a + 2 * Math.PI : a;
        }));

        assertEquals(5, factions.values().stream().filter(f -> f == Faction.NETHER).count());
        for (int i = 0; i < byAngle.size(); i++) {
            Faction here = factions.get(byAngle.get(i));
            Faction next = factions.get(byAngle.get((i + 1) % byAngle.size()));
            assertTrue(here != next, "neighbours " + i + " and " + (i + 1) + " differ");
        }
    }

    @Test
    void netherPillarsAreBuiltInNetherBlocksAndHeldByTheNether() {
        Map<Pillar, Faction> factions = ArenaLayout.factions(pillars);
        for (Pillar p : pillars) {
            boolean nether = factions.get(p) == Faction.NETHER;
            Set<String> blocks = new HashSet<>();
            for (Placement placement : layout.placements()) {
                if (Math.hypot(placement.x() - p.cx(), placement.z() - p.cz()) <= p.radius() + 4
                        && placement.y() > EndFightTestSupport.SURFACE_Y) {
                    blocks.add(placement.blockData().replaceFirst("\\[.*$", ""));
                }
            }
            assertEquals(nether, blocks.contains("minecraft:nether_bricks"), "nether bricks on " + p);
            assertEquals(nether, blocks.contains("minecraft:shroomlight"), "shroomlight on " + p);
            assertEquals(!nether, blocks.contains("minecraft:polished_blackstone_bricks"), "illager floor on " + p);
            List<GuardPost> posts = layout.guardPosts().stream()
                    .filter(g -> Math.hypot(g.x() - p.cx(), g.z() - p.cz()) <= p.radius() + 4)
                    .toList();
            assertFalse(posts.isEmpty());
            for (GuardPost post : posts) {
                assertEquals(nether ? Faction.NETHER : Faction.ILLAGER, post.faction(), "post " + post);
            }
        }
        assertTrue(layout.guardPosts().stream().filter(g -> g.kind() == GuardKind.GATEHOUSE || g.kind() == GuardKind.WALL)
                .allMatch(g -> g.faction() == Faction.ILLAGER), "the fortress stays the illagers'");
    }

    @Test
    void netherOutpostsHaveASoulFireOnSoulSoil() {
        Map<String, String> at = new HashMap<>();
        for (Placement placement : layout.placements()) {
            at.put(placement.x() + "," + placement.y() + "," + placement.z(), placement.blockData());
        }
        int fires = 0;
        for (Placement placement : layout.placements()) {
            // The fortress's own braziers (inside radius 25) burn on blackstone; only the pillars'.
            if (placement.blockData().equals("minecraft:soul_fire") && Math.hypot(placement.x(), placement.z()) > 25) {
                fires++;
                assertEquals("minecraft:soul_soil", at.get(placement.x() + "," + (placement.y() - 1) + "," + placement.z()),
                        "under the fire at " + placement.x() + "," + placement.y() + "," + placement.z());
            }
        }
        assertTrue(fires >= 5, "at least one per Nether pillar (got " + fires + ")");
    }
}

package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.GuardKind;
import dev.sirbepy.mc.endfight.arena.GuardPost;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardPlanTest {

    private static Map<GuardKind, GuardPlan.Composition> defaults() {
        Map<GuardKind, GuardPlan.Composition> out = new EnumMap<>(GuardKind.class);
        out.put(GuardKind.STAIR_OUTPOST, new GuardPlan.Composition("pillager", "vindicator"));
        out.put(GuardKind.LADDER_OUTPOST, new GuardPlan.Composition("pillager"));
        out.put(GuardKind.BALCONY, new GuardPlan.Composition("pillager"));
        out.put(GuardKind.GATEHOUSE, new GuardPlan.Composition("pillager", "evoker"));
        out.put(GuardKind.WALL, new GuardPlan.Composition("pillager"));
        return out;
    }

    @Test
    void everyPostGetsExactlyOnePlannedEntryInOrder() {
        List<GuardPost> posts = List.of(
                new GuardPost(0, 70, 0, GuardKind.WALL),
                new GuardPost(5, 70, 0, GuardKind.WALL));

        List<GuardPlan.PlannedGuard> plan = GuardPlan.plan(posts, defaults());

        assertEquals(2, plan.size());
        assertEquals(posts.get(0), plan.get(0).post());
        assertEquals(posts.get(1), plan.get(1).post());
    }

    @Test
    void stairOutpostTierGetsOneVindicatorButEveryPostGetsAPillager() {
        // Two posts at the same Y (one outpost tier on one pillar) plus one post at a different Y
        // (the next tier up) - same shape PillarStructures.outpostRing actually produces.
        List<GuardPost> posts = List.of(
                new GuardPost(10, 80, 0, GuardKind.STAIR_OUTPOST),
                new GuardPost(0, 80, 10, GuardKind.STAIR_OUTPOST),
                new GuardPost(-10, 80, 0, GuardKind.STAIR_OUTPOST),
                new GuardPost(10, 90, 0, GuardKind.STAIR_OUTPOST),
                new GuardPost(0, 90, 10, GuardKind.STAIR_OUTPOST));

        List<GuardPlan.PlannedGuard> plan = GuardPlan.plan(posts, defaults());

        long vindicators = plan.stream().filter(p -> p.types().contains("vindicator")).count();
        assertEquals(2, vindicators, "one bonus vindicator per Y tier (2 tiers here)");
        for (GuardPlan.PlannedGuard planned : plan) {
            assertTrue(planned.types().contains("pillager"), "every post still gets its base pillager");
        }
    }

    @Test
    void eachGatehouseGetsExactlyOneEvokerAcrossBothItsYLevels() {
        // One gatehouse's 4 posts: 2 at ground level (y=65) and 2 on the tower roof (y=75), all on
        // the same side of the portal (east, +x dominant) - exactly how FortressStructures.gatehouse
        // lays one gatehouse out. A second gatehouse on the opposite side (west) must get its own
        // evoker, not share the first one's.
        List<GuardPost> east = List.of(
                new GuardPost(16, 65, 1, GuardKind.GATEHOUSE),
                new GuardPost(16, 65, -1, GuardKind.GATEHOUSE),
                new GuardPost(20, 75, 1, GuardKind.GATEHOUSE),
                new GuardPost(20, 75, -1, GuardKind.GATEHOUSE));
        List<GuardPost> west = List.of(
                new GuardPost(-16, 65, 1, GuardKind.GATEHOUSE),
                new GuardPost(-16, 65, -1, GuardKind.GATEHOUSE),
                new GuardPost(-20, 75, 1, GuardKind.GATEHOUSE),
                new GuardPost(-20, 75, -1, GuardKind.GATEHOUSE));
        List<GuardPost> posts = new java.util.ArrayList<>();
        posts.addAll(east);
        posts.addAll(west);

        List<GuardPlan.PlannedGuard> plan = GuardPlan.plan(posts, defaults());

        long evokers = plan.stream().filter(p -> p.types().contains("evoker")).count();
        assertEquals(2, evokers, "one evoker per gatehouse, 2 gatehouses here");
        long eastEvokers = plan.subList(0, 4).stream().filter(p -> p.types().contains("evoker")).count();
        long westEvokers = plan.subList(4, 8).stream().filter(p -> p.types().contains("evoker")).count();
        assertEquals(1, eastEvokers);
        assertEquals(1, westEvokers);
    }

    @Test
    void ladderOutpostBalconyAndWallNeverGetABonusMobByDefault() {
        List<GuardPost> posts = List.of(
                new GuardPost(0, 70, 0, GuardKind.LADDER_OUTPOST),
                new GuardPost(0, 90, 0, GuardKind.BALCONY),
                new GuardPost(0, 100, 0, GuardKind.WALL));

        List<GuardPlan.PlannedGuard> plan = GuardPlan.plan(posts, defaults());

        for (GuardPlan.PlannedGuard planned : plan) {
            assertEquals(List.of("pillager"), planned.types());
        }
    }

    @Test
    void aKindWithNoConfiguredCompositionGetsNoMobsButStillAPlanEntry() {
        List<GuardPost> posts = List.of(new GuardPost(0, 70, 0, GuardKind.WALL));
        Map<GuardKind, GuardPlan.Composition> empty = new EnumMap<>(GuardKind.class);

        List<GuardPlan.PlannedGuard> plan = GuardPlan.plan(posts, empty);

        assertEquals(1, plan.size());
        assertTrue(plan.get(0).types().isEmpty());
    }
}

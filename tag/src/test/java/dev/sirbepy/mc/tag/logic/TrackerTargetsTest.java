package dev.sirbepy.mc.tag.logic;

import dev.sirbepy.mc.tag.logic.TrackerTargets.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrackerTargetsTest {

    private static Candidate here(String name, double distance) {
        return new Candidate(UUID.randomUUID(), name, true, distance, false, false, true);
    }

    private static Candidate elsewhere(String name) {
        return new Candidate(UUID.randomUUID(), name, false, 0.0, false, false, true);
    }

    private static Candidate afk(String name, double distance) {
        return new Candidate(UUID.randomUUID(), name, true, distance, true, false, true);
    }

    private static List<String> names(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::name).toList();
    }

    @Test
    void optedOutAndNonSurvivalPlayersAreNeverTrackableButAfkOnesAre() {
        Candidate optedOut = new Candidate(UUID.randomUUID(), "Opted", true, 5, false, true, true);
        Candidate creative = new Candidate(UUID.randomUUID(), "Creative", true, 5, false, false, false);
        Candidate idle = afk("Idle", 5);

        assertEquals(List.of("Idle"), names(TrackerTargets.trackable(List.of(optedOut, creative, idle))));
    }

    @Test
    void cycleOrderIsSameWorldNearestFirstThenOtherRealmsByName() {
        List<Candidate> trackable = TrackerTargets.trackable(List.of(
                elsewhere("zed"), here("Far", 300), elsewhere("Bob"), here("Near", 20)));

        assertEquals(List.of("Near", "Far", "Bob", "zed"), names(trackable));
    }

    @Test
    void defaultTargetIsTheNearestTaggablePlayerInThisWorld() {
        Candidate idle = afk("Idle", 3);
        Candidate near = here("Near", 40);
        List<Candidate> trackable = TrackerTargets.trackable(List.of(idle, near, elsewhere("Away")));

        assertEquals(near.uuid(), TrackerTargets.defaultTarget(trackable));
    }

    @Test
    void defaultTargetFallsBackToAnyoneTrackableWhenNobodyHereIsTaggable() {
        Candidate away = elsewhere("Away");
        Candidate idle = afk("Idle", 3);

        assertEquals(idle.uuid(), TrackerTargets.defaultTarget(TrackerTargets.trackable(List.of(away, idle))));
    }

    @Test
    void nobodyTrackableMeansNoTarget() {
        assertNull(TrackerTargets.defaultTarget(List.of()));
        assertNull(TrackerTargets.next(List.of(), UUID.randomUUID()));
        assertNull(TrackerTargets.resolve(List.of(), UUID.randomUUID()));
    }

    @Test
    void resolveKeepsTheChosenTargetUntilTheyStopBeingTrackable() {
        Candidate near = here("Near", 10);
        Candidate far = here("Far", 90);
        List<Candidate> both = TrackerTargets.trackable(List.of(near, far));

        assertEquals(far.uuid(), TrackerTargets.resolve(both, far.uuid()), "a cycled-to target must stick");
        assertEquals(near.uuid(), TrackerTargets.resolve(List.of(near), far.uuid()),
                "once the target leaves or opts out, the compass goes back to the default");
    }

    @Test
    void nextWrapsAroundTheCycleOrder() {
        Candidate near = here("Near", 10);
        Candidate far = here("Far", 90);
        Candidate away = elsewhere("Away");
        List<Candidate> trackable = TrackerTargets.trackable(List.of(away, far, near));

        assertEquals(far.uuid(), TrackerTargets.next(trackable, near.uuid()));
        assertEquals(away.uuid(), TrackerTargets.next(trackable, far.uuid()));
        assertEquals(near.uuid(), TrackerTargets.next(trackable, away.uuid()));
    }
}

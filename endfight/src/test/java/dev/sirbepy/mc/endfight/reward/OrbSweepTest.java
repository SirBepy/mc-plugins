package dev.sirbepy.mc.endfight.reward;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure radius/window logic for {@link OrbSweep} - given orb positions, the dragon's death
 * location and a tick, which orbs get removed (see {@link RewardService#onDragonDeath} for why
 * the sweep replaced cancelling the orbs' spawn event). */
class OrbSweepTest {

    private WorldMock end;
    private WorldMock overworld;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        overworld = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void activeFromTickZeroThroughTheWindowInclusive() {
        assertTrue(OrbSweep.activeAt(0, 240));
        assertTrue(OrbSweep.activeAt(240, 240));
        assertFalse(OrbSweep.activeAt(241, 240));
        assertFalse(OrbSweep.activeAt(-1, 240));
    }

    @Test
    void anOrbWithinRadiusIsInRange() {
        Location death = new Location(end, 0, 80, 0);
        Location orb = new Location(end, 20, 80, 0);

        assertTrue(OrbSweep.inRange(orb, death, 24.0));
    }

    @Test
    void anOrbThatFellFarBelowTheDragonIsStillInRange() {
        Location death = new Location(end, 0, 100, 0);
        Location orb = new Location(end, 5, 60, 3);

        assertTrue(OrbSweep.inRange(orb, death, 24.0));
    }

    @Test
    void anOrbBeyondRadiusIsNotInRange() {
        Location death = new Location(end, 0, 80, 0);
        Location orb = new Location(end, 30, 80, 0);

        assertFalse(OrbSweep.inRange(orb, death, 24.0));
    }

    @Test
    void anOrbInADifferentWorldIsNeverInRange() {
        Location death = new Location(end, 0, 80, 0);
        Location orb = new Location(overworld, 0, 80, 0);

        assertFalse(OrbSweep.inRange(orb, death, 24.0));
    }

    @Test
    void toRemoveCombinesTheWindowAndTheRadius() {
        Location death = new Location(end, 0, 80, 0);
        List<Location> orbs = List.of(
                new Location(end, 10, 80, 0),  // in range
                new Location(end, 50, 80, 0),  // out of range
                new Location(overworld, 0, 80, 0)); // in range distance-wise, wrong world

        List<Location> withinWindow = OrbSweep.toRemove(orbs, death, 24.0, 100, 240);
        assertEquals(1, withinWindow.size());
        assertEquals(10.0, withinWindow.get(0).getX());

        List<Location> afterWindow = OrbSweep.toRemove(orbs, death, 24.0, 300, 240);
        assertTrue(afterWindow.isEmpty(), "nothing should be swept once the window has closed");
    }
}

package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RadarTrackingTest {

    private final RadarTracking tracking = new RadarTracking();
    private final UUID player = UUID.randomUUID();

    @Test
    void cyclesFromNearestThroughEveryBallAndBackToNearest() {
        List<Integer> stars = List.of(4, 1, 7);

        assertEquals(4, tracking.cycle(player, BallSet.OVERWORLD, stars), "the first click pins the nearest ball");
        assertEquals(1, tracking.cycle(player, BallSet.OVERWORLD, stars));
        assertEquals(7, tracking.cycle(player, BallSet.OVERWORLD, stars));
        assertNull(tracking.cycle(player, BallSet.OVERWORLD, stars), "after the furthest ball it goes back to nearest");
        assertNull(tracking.picked(player, BallSet.OVERWORLD));
        assertEquals(4, tracking.cycle(player, BallSet.OVERWORLD, stars));
    }

    @Test
    void theOrderIsReadAfreshEachClick() {
        tracking.cycle(player, BallSet.OVERWORLD, List.of(2, 5, 6));

        // The player walked: 5 is now nearest and 2 sits in the middle.
        assertEquals(6, tracking.cycle(player, BallSet.OVERWORLD, List.of(5, 2, 6)));
    }

    @Test
    void aPickNoLongerInSightRestartsFromTheNearest() {
        tracking.cycle(player, BallSet.OVERWORLD, List.of(3));

        assertEquals(1, tracking.cycle(player, BallSet.OVERWORLD, List.of(1, 2)));
    }

    @Test
    void eachSetAndPlayerKeepsItsOwnPick() {
        UUID other = UUID.randomUUID();
        tracking.cycle(player, BallSet.OVERWORLD, List.of(3, 4));
        tracking.cycle(player, BallSet.NETHER, List.of(6));

        assertEquals(3, tracking.picked(player, BallSet.OVERWORLD));
        assertEquals(6, tracking.picked(player, BallSet.NETHER));
        assertNull(tracking.picked(other, BallSet.OVERWORLD));

        tracking.forget(player);
        assertNull(tracking.picked(player, BallSet.OVERWORLD));
        assertNull(tracking.picked(player, BallSet.NETHER));
    }
}

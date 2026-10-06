package dev.sirbepy.mc.dragonballs.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure flight-path math for a ball after a wish: rise, then either fly toward the destination
 * (same world) or keep climbing straight up (other world / no destination). No Bukkit involved,
 * so these run as plain JUnit, no MockBukkit required.
 */
class FlightMathTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void horizontalDirectionPointsTowardTheDestination() {
        double[] dir = FlightMath.horizontalDirection(0, 0, 10, 0);

        assertEquals(1.0, dir[0], EPSILON);
        assertEquals(0.0, dir[1], EPSILON);
    }

    @Test
    void horizontalDirectionIsZeroRatherThanNaNWhenDestinationEqualsOrigin() {
        double[] dir = FlightMath.horizontalDirection(5, 5, 5, 5);

        assertEquals(0.0, dir[0]);
        assertEquals(0.0, dir[1]);
        assertFalse(Double.isNaN(dir[0]));
        assertFalse(Double.isNaN(dir[1]));
    }

    @Test
    void offsetTowardDestinationRisesStraightUpFirst() {
        double[] midRise = FlightMath.offsetTowardDestination(FlightMath.RISE_TICKS / 2, 1.0, 0.0);

        assertEquals(0.0, midRise[0], EPSILON);
        assertEquals(0.0, midRise[2], EPSILON);
        assertTrue(midRise[1] > 0 && midRise[1] < FlightMath.RISE_HEIGHT);
    }

    @Test
    void offsetTowardDestinationReachesFullRiseHeightBeforeFlyingOut() {
        double[] atRiseEnd = FlightMath.offsetTowardDestination(FlightMath.RISE_TICKS, 1.0, 0.0);

        assertEquals(FlightMath.RISE_HEIGHT, atRiseEnd[1], EPSILON);
        assertEquals(0.0, atRiseEnd[0], EPSILON);
    }

    @Test
    void offsetTowardDestinationMovesHorizontallyAfterTheRise() {
        long t = FlightMath.RISE_TICKS + FlightMath.FLY_TICKS / 2;
        double[] offset = FlightMath.offsetTowardDestination(t, 1.0, 0.0);

        assertTrue(offset[0] > 0, "should have moved toward +X by the midpoint of the flight");
        assertTrue(offset[1] > FlightMath.RISE_HEIGHT, "should still be slowly climbing");
        assertFalse(Double.isNaN(offset[0]));
    }

    @Test
    void offsetTowardDestinationNeverNaNWhenDirectionIsZero() {
        double[] offset = FlightMath.offsetTowardDestination(FlightMath.RISE_TICKS + FlightMath.FLY_TICKS, 0.0, 0.0);

        assertFalse(Double.isNaN(offset[0]));
        assertFalse(Double.isNaN(offset[1]));
        assertFalse(Double.isNaN(offset[2]));
        assertEquals(0.0, offset[0], EPSILON);
        assertEquals(0.0, offset[2], EPSILON);
    }

    @Test
    void offsetStraightUpReachesFullHeightAtEndAndStaysThereAfterwards() {
        double[] atEnd = FlightMath.offsetStraightUp(FlightMath.TOTAL_TICKS_STRAIGHT);
        double[] pastEnd = FlightMath.offsetStraightUp(FlightMath.TOTAL_TICKS_STRAIGHT + 1000);

        assertEquals(FlightMath.STRAIGHT_UP_HEIGHT, atEnd[1], EPSILON);
        assertEquals(FlightMath.STRAIGHT_UP_HEIGHT, pastEnd[1], EPSILON);
    }

    @Test
    void doneFlagsFlipExactlyAtTheirDurations() {
        assertFalse(FlightMath.isHorizontalFlightDone(FlightMath.TOTAL_TICKS_HORIZONTAL - 1));
        assertTrue(FlightMath.isHorizontalFlightDone(FlightMath.TOTAL_TICKS_HORIZONTAL));
        assertFalse(FlightMath.isStraightUpDone(FlightMath.TOTAL_TICKS_STRAIGHT - 1));
        assertTrue(FlightMath.isStraightUpDone(FlightMath.TOTAL_TICKS_STRAIGHT));
    }
}

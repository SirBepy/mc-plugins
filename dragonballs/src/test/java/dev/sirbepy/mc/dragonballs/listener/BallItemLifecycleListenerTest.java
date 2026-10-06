package dev.sirbepy.mc.dragonballs.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pickup delay is checked as a value: MockBukkit's Item mock doesn't implement
 * setUnlimitedLifetime, so driving onSpawn there only marks the test skipped.
 */
class BallItemLifecycleListenerTest {

    @Test
    void aDroppedBallCannotBeRepickedTheSameTick() {
        assertTrue(BallItemLifecycleListener.PICKUP_DELAY > 0,
                "with no pickup delay the dropper grabs the ball straight back, so it can never be dropped");
    }

    @Test
    void theDelayNeverMatchesWhatGravesSweepsUp() {
        assertNotEquals(40, BallItemLifecycleListener.PICKUP_DELAY);
        assertNotEquals(39, BallItemLifecycleListener.PICKUP_DELAY);
    }
}

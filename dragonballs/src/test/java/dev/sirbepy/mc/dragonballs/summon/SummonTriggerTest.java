package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.registry.BallState;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure checks: no MockBukkit, every ball is a plain {@link BallView} record. */
class SummonTriggerTest {

    private static BallView placed(int star, String world, double x, double y, double z) {
        return new BallView(star, BallState.PLACED, false, world, x, y, z, null, null, 0, 0, null);
    }

    private static List<BallView> sevenAt(String world, double cx, double cy, double cz) {
        List<BallView> balls = new ArrayList<>();
        for (int star = 1; star <= 7; star++) {
            balls.add(placed(star, world, cx + (star % 2), cy, cz + (star % 2)));
        }
        return balls;
    }

    @Test
    void allSevenWithinTwoBlocksOfTheClickedBallPasses() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        assertTrue(SummonTrigger.allWithinRadius(balls, "world", 100, 64, 100, 2));
    }

    @Test
    void aBallThreeBlocksAwayFails() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        balls.set(0, placed(1, "world", 103, 64, 100)); // 3 away on X: outside radius 2
        assertFalse(SummonTrigger.allWithinRadius(balls, "world", 100, 64, 100, 2));
    }

    @Test
    void aStoneBallFails() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        BallView stoneBall = balls.get(0);
        balls.set(0, new BallView(stoneBall.star(), BallState.PLACED, true, stoneBall.worldName(),
                stoneBall.x(), stoneBall.y(), stoneBall.z(), null, null, 0, 0, null));
        assertFalse(SummonTrigger.allWithinRadius(balls, "world", 100, 64, 100, 2));
    }

    @Test
    void aHeldBallFails() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        BallView held = balls.get(0);
        balls.set(0, new BallView(held.star(), BallState.HELD, false, held.worldName(),
                held.x(), held.y(), held.z(), null, "Someone", 0, 0, null));
        assertFalse(SummonTrigger.allWithinRadius(balls, "world", 100, 64, 100, 2));
    }

    @Test
    void aBallInADifferentWorldFails() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        BallView wrongWorld = balls.get(0);
        balls.set(0, new BallView(wrongWorld.star(), BallState.PLACED, false, "world_nether",
                wrongWorld.x(), wrongWorld.y(), wrongWorld.z(), null, null, 0, 0, null));
        assertFalse(SummonTrigger.allWithinRadius(balls, "world", 100, 64, 100, 2));
    }

    @Test
    void fewerThanSevenBallsFails() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        balls.remove(0);
        assertFalse(SummonTrigger.allWithinRadius(balls, "world", 100, 64, 100, 2));
    }

    @Test
    void averagePositionIsThePlainMean() {
        List<BallView> balls = List.of(
                placed(1, "world", 0, 0, 0),
                placed(2, "world", 10, 20, 30));
        double[] avg = SummonTrigger.averagePosition(balls);
        assertTrue(Math.abs(avg[0] - 5) < 1e-9);
        assertTrue(Math.abs(avg[1] - 10) < 1e-9);
        assertTrue(Math.abs(avg[2] - 15) < 1e-9);
    }

    @Test
    void evaluateOrdersSessionActiveBeforeDormantBeforeBanned() {
        List<BallView> balls = sevenAt("world", 100, 64, 100);
        assertTrue(SummonTrigger.evaluate(balls, true, true, true, null, 2) == SummonTrigger.Readiness.SESSION_ACTIVE);
        assertTrue(SummonTrigger.evaluate(balls, true, false, true, null, 2) == SummonTrigger.Readiness.DORMANT);
        assertTrue(SummonTrigger.evaluate(balls, false, false, true, null, 2) == SummonTrigger.Readiness.BANNED);
    }
}

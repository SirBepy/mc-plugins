package dev.sirbepy.mc.cartel.call;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarksmanTest {

    /** Ticks from {@code from} to {@code to} inclusive on which a bolt left. */
    private static List<Long> shots(Marksman marksman, long from, long to, double distance, boolean clear) {
        List<Long> fired = new ArrayList<>();
        for (long t = from; t <= to; t++) {
            if (marksman.tick(t, distance, clear) != Marksman.Shot.NONE) {
                fired.add(t);
            }
        }
        return fired;
    }

    @Test
    void loadsInHalfASecondThenAimsLikeAPillagerBeforeEachBolt() {
        Marksman marksman = new Marksman(() -> 5);
        int perBolt = Marksman.CHARGE_TICKS + Marksman.AIM_TICKS + 5;

        assertEquals(10, Marksman.CHARGE_TICKS, "Quick Charge III");
        assertEquals(List.of((long) perBolt, 2L * perBolt, 3L * perBolt), shots(marksman, 0, 3 * perBolt, 8, true));
    }

    @Test
    void everyThirdShotIsAFireworkRocket() {
        Marksman marksman = new Marksman(() -> 0);
        List<Marksman.Shot> fired = new ArrayList<>();
        for (long t = 0; fired.size() < 7; t++) {
            Marksman.Shot shot = marksman.tick(t, 8, true);
            if (shot != Marksman.Shot.NONE) {
                fired.add(shot);
            }
        }

        assertEquals(List.of(Marksman.Shot.BOLT, Marksman.Shot.BOLT, Marksman.Shot.ROCKET, Marksman.Shot.BOLT,
                Marksman.Shot.BOLT, Marksman.Shot.ROCKET, Marksman.Shot.BOLT), fired);
    }

    @Test
    void holdsItsBoltWhileTheShotIsBlockedOrOutOfRange() {
        Marksman marksman = new Marksman(() -> 0);
        int ready = Marksman.CHARGE_TICKS + Marksman.AIM_TICKS;

        assertTrue(shots(marksman, 0, ready + 40, 8, false).isEmpty(), "a pillar's blocks in the way");
        assertTrue(shots(marksman, ready + 41, ready + 80, Marksman.RANGE + 1, true).isEmpty(), "too far");
        assertEquals(List.of(ready + 81L), shots(marksman, ready + 81, ready + 81, 8, true),
                "loaded and aimed already, it fires the moment the shot clears");
    }

    @Test
    void closesInWhenFarOrBlockedHoldsAtRangeAndBacksOffWhenTooClose() {
        Marksman marksman = new Marksman(() -> 0);

        assertEquals(Marksman.Move.CLOSE_IN, marksman.move(12, true));
        assertEquals(Marksman.Move.CLOSE_IN, marksman.move(6, false));
        assertEquals(Marksman.Move.HOLD, marksman.move(6, true));
        assertEquals(Marksman.Move.BACK_OFF, marksman.move(1, false), "at a pillar's base, back off to see over it");
        assertFalse(marksman.move(Marksman.TOO_CLOSE, true) == Marksman.Move.BACK_OFF);
    }
}

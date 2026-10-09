package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Joe, 2026-10-09: "always prioritize putting it in a structure" - the widened second search round
 * covers a bigger annulus than before, so a ball is more likely to find a listed structure before
 * ever reaching the plain-ground fallback. Widening is free of extra chunk loads: StructurePlacement
 * searches with findUnexplored=false, so a bigger ring only searches more of what pregen-limit
 * already generated, never triggers new chunk generation. */
class ScatterServiceTest {

    @Test
    void theWidenedRingCoversAMuchBiggerAnnulusThanTheOwnRing() {
        ScatterService.Ring ring = new ScatterService.Ring(BallSet.OVERWORLD, null, 0, 0, 1000, 2000, 4900, 200);

        ScatterService.Ring widened = ring.widened();

        assertEquals(250, widened.minRadius(), "a quarter of the own ring's min radius");
        assertEquals(4000, widened.maxRadius(), "double the own ring's max radius");
    }
}

package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarBandTest {

    @Test
    void overworldAndEndBandsBreakAt50And300And1000Blocks() {
        for (BallSet set : new BallSet[] {BallSet.OVERWORLD, BallSet.END}) {
            assertEquals(RadarBand.HERE, RadarBand.of(49, set));
            assertEquals(RadarBand.CLOSE, RadarBand.of(50, set));
            assertEquals(RadarBand.CLOSE, RadarBand.of(299, set));
            assertEquals(RadarBand.WARM, RadarBand.of(300, set));
            assertEquals(RadarBand.WARM, RadarBand.of(999, set));
            assertEquals(RadarBand.FAR, RadarBand.of(1000, set));
        }
    }

    @Test
    void netherBandsAreHalfAsWideSinceItsBallsLandCloser() {
        assertEquals(RadarBand.HERE, RadarBand.of(24, BallSet.NETHER));
        assertEquals(RadarBand.CLOSE, RadarBand.of(25, BallSet.NETHER));
        assertEquals(RadarBand.WARM, RadarBand.of(150, BallSet.NETHER));
        assertEquals(RadarBand.FAR, RadarBand.of(500, BallSet.NETHER));
    }
}

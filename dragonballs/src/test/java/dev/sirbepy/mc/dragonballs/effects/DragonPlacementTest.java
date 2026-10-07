package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure math, no MockBukkit needed: the Nether height clamp and the per-set look selection. */
class DragonPlacementTest {

    private static final EffectsConfig CONFIG = new EffectsConfig(18.0, 1.0, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);

    @Test
    void netherHeightKeepsTheRequestedMarginWhenThereIsPlentyOfRoom() {
        // ceiling 18 up, scale .5 (body top 4): desired = min(18, 18-2-4) = 12.
        double height = DragonPlacement.netherHoverHeight(18.0, 0.5, 18.0, 2.0, 3.0);
        assertEquals(12.0, height, 1.0e-9);
    }

    @Test
    void netherHeightClampsUnderALowCavernCeiling() {
        // ceiling 10 up: desired = min(18, 10-2-4) = 4, still above the never-inside-blocks cap (6).
        double height = DragonPlacement.netherHoverHeight(10.0, 0.5, 18.0, 2.0, 3.0);
        assertEquals(4.0, height, 1.0e-9);
    }

    @Test
    void netherHeightNeverPutsTheBodyInsideTheCeilingEvenBelowTheMinHeightFloor() {
        // ceiling only 5 up: the 3-block floor would put the body top at 3+4=7, past the ceiling at
        // 5, so the hard "never inside blocks" cap (ceiling - bodyTop = 1) wins instead.
        double height = DragonPlacement.netherHoverHeight(5.0, 0.5, 18.0, 2.0, 3.0);
        assertEquals(1.0, height, 1.0e-9);
    }

    @Test
    void netherHeightNeverGoesNegativeAgainstAnImpossiblyLowCeiling() {
        double height = DragonPlacement.netherHoverHeight(2.0, 0.5, 18.0, 2.0, 3.0);
        assertEquals(0.0, height, 1.0e-9);
    }

    @Test
    void scaleForNetherScalesProportionally() {
        assertEquals(1.5, DragonPlacement.scaleForNether(3.0, 0.5), 1.0e-9);
        assertEquals(0.375, DragonPlacement.scaleForNether(0.75, 0.5), 1.0e-9);
    }

    @Test
    void overworldLookIsUnchanged() {
        DragonPlacement.Look look = DragonPlacement.lookFor(BallSet.OVERWORLD, CONFIG, 3.0, 0.75);
        assertEquals(1.0, look.scale(), 1.0e-9);
        assertEquals(3.0, look.circleRadius(), 1.0e-9);
        assertEquals(0.75, look.bobAmplitude(), 1.0e-9);
        assertFalse(look.smokeColumn());
        assertFalse(look.purpleAura());
    }

    @Test
    void netherLookIsHalfScaleWithShrunkMotionAndASmokeColumn() {
        DragonPlacement.Look look = DragonPlacement.lookFor(BallSet.NETHER, CONFIG, 3.0, 0.75);
        assertEquals(0.5, look.scale(), 1.0e-9);
        assertEquals(1.5, look.circleRadius(), 1.0e-9);
        assertEquals(0.375, look.bobAmplitude(), 1.0e-9);
        assertTrue(look.smokeColumn());
        assertFalse(look.purpleAura());
    }

    @Test
    void endLookKeepsFullSizeAndMotionWithAPurpleAura() {
        DragonPlacement.Look look = DragonPlacement.lookFor(BallSet.END, CONFIG, 3.0, 0.75);
        assertEquals(1.0, look.scale(), 1.0e-9);
        assertEquals(3.0, look.circleRadius(), 1.0e-9);
        assertEquals(0.75, look.bobAmplitude(), 1.0e-9);
        assertFalse(look.smokeColumn());
        assertTrue(look.purpleAura());
    }
}

package dev.sirbepy.mc.endfight.perch;

import dev.sirbepy.mc.endfight.arena.Pillar;
import org.bukkit.entity.EnderDragon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerchGateTest {

    private static final Pillar PILLAR = new Pillar(10, 20, 3, 90, 64);

    @Test
    void flyToPortalAndLandOnPortalAreTheOnlyLandingPhases() {
        assertTrue(PerchGate.isLandingPhase(EnderDragon.Phase.FLY_TO_PORTAL));
        assertTrue(PerchGate.isLandingPhase(EnderDragon.Phase.LAND_ON_PORTAL));
        assertFalse(PerchGate.isLandingPhase(EnderDragon.Phase.CIRCLING));
        assertFalse(PerchGate.isLandingPhase(EnderDragon.Phase.DYING));
        assertFalse(PerchGate.isLandingPhase(EnderDragon.Phase.STRAFING));
    }

    @Test
    void aCrystalStillOnItsPillarBlocksLanding() {
        PerchGate.Point crystalAtPillarTop = new PerchGate.Point(PILLAR.cx(), PILLAR.topY() + 1, PILLAR.cz());

        boolean guarded = PerchGate.anyPillarCrystalAlive(List.of(PILLAR), List.of(crystalAtPillarTop), 3, 60, 70);

        assertTrue(guarded);
    }

    @Test
    void noCrystalsAliveAnywhereAllowsLanding() {
        boolean guarded = PerchGate.anyPillarCrystalAlive(List.of(PILLAR), List.of(), 3, 60, 70);

        assertFalse(guarded);
    }

    @Test
    void aCrystalFarFromEveryPillarDoesNotBlockLanding() {
        PerchGate.Point farAway = new PerchGate.Point(500, 90, 500);

        boolean guarded = PerchGate.anyPillarCrystalAlive(List.of(PILLAR), List.of(farAway), 3, 60, 70);

        assertFalse(guarded);
    }

    @Test
    void withNoPillarsKnownAFallbackCrystalHighAboveTheCentreBlocksLanding() {
        PerchGate.Point highAboveCentre = new PerchGate.Point(10, 85, -10);

        boolean guarded = PerchGate.anyPillarCrystalAlive(List.of(), List.of(highAboveCentre), 3, 60, 70);

        assertTrue(guarded);
    }

    @Test
    void theFallbackNeverCountsThePortalsOwnLowAltitudeRespawnCrystals() {
        // The 4 crystals a player places on the exit portal during a respawn sit near the portal's
        // own low Y, nowhere near the fallback's "above y 70" floor.
        PerchGate.Point onThePortal = new PerchGate.Point(3, 9, 3);

        boolean guarded = PerchGate.anyPillarCrystalAlive(List.of(), List.of(onThePortal), 3, 60, 70);

        assertFalse(guarded);
    }

    @Test
    void theFallbackNeverCountsACrystalFarOutsideItsRadius() {
        PerchGate.Point farOut = new PerchGate.Point(200, 90, 0);

        boolean guarded = PerchGate.anyPillarCrystalAlive(List.of(), List.of(farOut), 3, 60, 70);

        assertFalse(guarded);
    }
}

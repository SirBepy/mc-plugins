package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure ground-plane geometry of the telegraph shapes; no fight, no particles. */
class ShapeTest extends EngineTestBase {

    @Test
    void aCircleHitsInsideItsRadiusAndNotOutside() {
        Shape circle = new Shape.Circle(at(10, 64, 10), 4);
        assertTrue(circle.contains(at(10, 64, 10)));
        assertTrue(circle.contains(at(13.9, 64, 10)));
        assertFalse(circle.contains(at(14.1, 64, 10)));
        assertFalse(circle.contains(at(13, 64, 13)), "the corner of the bounding square is outside the round shape");
    }

    @Test
    void aCircleMissesSomeoneFarAboveOrBelowIt() {
        Shape circle = new Shape.Circle(at(10, 64, 10), 4);
        assertTrue(circle.contains(at(10, 64 + Shape.VERTICAL_REACH, 10)));
        assertFalse(circle.contains(at(10, 64 + Shape.VERTICAL_REACH + 0.5, 10)));
        assertFalse(circle.contains(at(10, 64 - Shape.VERTICAL_REACH - 0.5, 10)));
    }

    @Test
    void aShapeNeverHitsAPlayerInAnotherWorld() {
        Location elsewhere = new Location(server.addSimpleWorld("other"), 10, 64, 10);
        assertFalse(new Shape.Circle(at(10, 64, 10), 4).contains(elsewhere));
    }

    @Test
    void aRingsHoleIsSafe() {
        Shape ring = new Shape.Ring(at(10, 64, 10), 3, 6);
        assertFalse(ring.contains(at(10, 64, 10)), "the centre is inside the hole");
        assertFalse(ring.contains(at(12.5, 64, 10)), "just inside the inner radius is still the hole");
        assertTrue(ring.contains(at(14, 64, 10)));
        assertTrue(ring.contains(at(10, 64, 15.9)));
        assertFalse(ring.contains(at(16.5, 64, 10)), "beyond the outer radius is safe");
    }

    @Test
    void aSectorHitsInFrontAndNotBehindAndYawZeroFacesSouth() {
        Shape south = new Shape.Sector(at(10, 64, 10), 0, 45, 8);
        assertTrue(south.contains(at(10, 64, 15)), "yaw 0 faces +Z");
        assertFalse(south.contains(at(10, 64, 5)), "directly behind is safe");
        assertFalse(south.contains(at(15, 64, 10)), "90 degrees off to the side is outside a 45 degree half angle");
        assertFalse(south.contains(at(10, 64, 19)), "past the radius is safe");
        assertTrue(south.contains(at(12, 64, 14)), "about 27 degrees off the axis is inside");
        assertFalse(south.contains(at(16, 64, 12)), "about 71 degrees off the axis is outside");
    }

    @Test
    void aSectorFollowsMinecraftYawSoNinetyFacesWest() {
        Shape west = new Shape.Sector(at(10, 64, 10), 90, 30, 8);
        assertTrue(west.contains(at(5, 64, 10)), "yaw 90 faces -X");
        assertFalse(west.contains(at(15, 64, 10)));
        assertFalse(west.contains(at(10, 64, 15)));

        Shape north = new Shape.Sector(at(10, 64, 10), 180, 30, 8);
        assertTrue(north.contains(at(10, 64, 5)), "yaw 180 faces -Z");
        Shape east = new Shape.Sector(at(10, 64, 10), -90, 30, 8);
        assertTrue(east.contains(at(15, 64, 10)), "yaw -90 faces +X");
    }

    @Test
    void aLineHitsAlongTheBeamAndNotBesideOrBeyondIt() {
        Shape line = new Shape.Line(at(5, 64, 10), at(20, 64, 10), 1.5);
        assertTrue(line.contains(at(12, 64, 10)));
        assertTrue(line.contains(at(12, 64, 11.4)));
        assertFalse(line.contains(at(12, 64, 11.6)), "beside the beam");
        assertFalse(line.contains(at(22, 64, 10)), "past the far end");
        assertFalse(line.contains(at(3, 64, 10)), "behind the near end");
        assertTrue(line.contains(at(4.5, 64, 10)), "the ends are rounded by the half width");
    }

    @Test
    void grownToOneIsTheFullShapeAndGrownToHalfIsSmaller() {
        Shape circle = new Shape.Circle(at(10, 64, 10), 8);
        assertTrue(circle.grown(1).contains(at(17.5, 64, 10)));
        assertFalse(circle.grown(0.5).contains(at(17.5, 64, 10)));
        assertTrue(circle.grown(0.5).contains(at(13.5, 64, 10)));

        Shape ring = new Shape.Ring(at(10, 64, 10), 2, 10);
        assertFalse(ring.grown(0.5).contains(at(10, 64, 18)), "the band has only grown halfway out");
        assertTrue(ring.grown(1).contains(at(10, 64, 18)));

        Shape line = new Shape.Line(at(0, 64, 10), at(20, 64, 10), 1);
        assertFalse(line.grown(0.5).contains(at(15, 64, 10)));
        assertTrue(line.grown(1).contains(at(15, 64, 10)));
    }

    @Test
    void outlinePointsLieOnTheShapesEdge() {
        Location centre = at(10, 64, 10);
        for (Vector p : new Shape.Circle(centre, 6).outline(0.8)) {
            assertEquals(6, Math.hypot(p.getX() - 10, p.getZ() - 10), 1.0e-6);
        }
        Shape.Sector sector = new Shape.Sector(centre, 0, 40, 6);
        for (Vector p : sector.outline(0.8)) {
            assertTrue(Math.hypot(p.getX() - 10, p.getZ() - 10) <= 6 + 1.0e-6, "no outline point past the radius");
            assertTrue(p.getZ() >= 10 - 1.0e-6, "a south-facing cone draws nothing behind its origin");
        }
        assertFalse(new Shape.Ring(centre, 3, 6).outline(0.8).isEmpty());
        assertFalse(new Shape.Line(at(0, 64, 0), at(10, 64, 0), 1).outline(0.8).isEmpty());
    }
}

package dev.sirbepy.mc.minibosses.rig;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The rig composition against the Python twin that previews the same rig file
 * ({@code .for_bepy/guardian-models/render_sentinel.py}, whose {@code check_twin()} proves the
 * display path equals the straight rig-frame path). Golden numbers were produced with, from the repo root:
 *
 * <pre>
 * python -c "import sys; sys.path.insert(0,'.for_bepy/guardian-models'); import render_sentinel as r;
 * o=r.compose_rig('normal',POSE); d=o[PART]; print(d['translation'], d['left'], d['scale'])"
 * </pre>
 *
 * with POSE in idle, slam_windup and PART in pelvis (a root), torso (its child), fist_r (a great-grandchild
 * through arm_r_upper and arm_r_fore), heart; and the anchor rows with {@code r.compose_rig('mythic', POSE)[PART]['rig']}
 * times the anchor's local point, then {@code feet + Ry(-yaw) * Ry(pi)} of it.
 */
class RigMathTest {

    private static final double EPS = 1.0e-4;
    private final RigDefinition def = RigDefinition.load("overgrown_sentinel", Logger.getAnonymousLogger()).orElseThrow();

    /** translation xyz, left rotation xyzw, scale. */
    private static void assertPlacement(RigMath.Placement actual, double[] expected) {
        Transformation t = actual.toTransformation();
        assertEquals(expected[0], t.getTranslation().x(), EPS, "translation x");
        assertEquals(expected[1], t.getTranslation().y(), EPS, "translation y");
        assertEquals(expected[2], t.getTranslation().z(), EPS, "translation z");
        // q and -q are the same rotation, so line the signs up before comparing components.
        Quaternionf q = t.getLeftRotation();
        double dot = q.x * expected[3] + q.y * expected[4] + q.z * expected[5] + q.w * expected[6];
        double sign = dot < 0 ? -1 : 1;
        assertEquals(expected[3], sign * q.x, EPS, "rotation x");
        assertEquals(expected[4], sign * q.y, EPS, "rotation y");
        assertEquals(expected[5], sign * q.z, EPS, "rotation z");
        assertEquals(expected[6], sign * q.w, EPS, "rotation w");
        assertEquals(expected[7], t.getScale().x(), EPS, "scale");
        assertEquals(expected[7], t.getScale().y(), EPS);
        assertEquals(expected[7], t.getScale().z(), EPS);
        assertEquals(new Quaternionf(), t.getRightRotation(), "the right rotation stays identity");
    }

    @Test
    void idleMatchesThePythonTwin() {
        Map<String, RigMath.Placement> idle = RigMath.place(def, Tier.NORMAL, "idle");

        assertPlacement(idle.get("pelvis"), new double[] {0.0, 1.75, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0});
        assertPlacement(idle.get("torso"), new double[] {0.0, 2.125, 0.0, 0.052336, 0.0, 0.0, 0.99863, 2.0});
        assertPlacement(idle.get("fist_r"),
                new double[] {-1.778605, 1.622923, 0.104742, 0.0, 0.002736, -0.026034, 0.999657, 2.0});
        assertPlacement(idle.get("heart"), new double[] {0.0, 2.818627, 0.575657, 0.052336, 0.0, 0.0, 0.99863, 2.0});
    }

    @Test
    void slamWindupMatchesThePythonTwin() {
        Map<String, RigMath.Placement> windup = RigMath.place(def, Tier.NORMAL, "slam_windup");

        assertPlacement(windup.get("pelvis"), new double[] {0.0, 1.75, -0.125, 0.0, 0.0, 0.0, 1.0, 2.0});
        assertPlacement(windup.get("torso"), new double[] {0.0, 2.125, -0.125, -0.121869, 0.0, 0.0, 0.992546, 2.0});
        assertPlacement(windup.get("fist_r"),
                new double[] {-1.402068, 5.01566, -0.389433, 0.959964, -0.087126, -0.002281, 0.266221, 2.0});
        assertPlacement(windup.get("heart"), new double[] {0.0, 2.973683, 0.178706, -0.121869, 0.0, 0.0, 0.992546, 2.0});
    }

    @Test
    void aPosesOwnScaleReachesOnlyThatPartsDrawnSize() {
        // heartguard enlarges the heart 1.45x (modelScale 2.0 gives 2.9); its children and siblings stay 2.0.
        Map<String, RigMath.Placement> guard = RigMath.place(def, Tier.NORMAL, "heartguard");

        assertEquals(2.9f, guard.get("heart").scale(), 1e-5f);
        assertEquals(2.0f, guard.get("torso").scale(), 1e-5f);
        assertEquals(2.0f, guard.get("head").scale(), 1e-5f);
    }

    @Test
    void aBlendInterpolatesRotationOffsetAndScalePerPart() {
        Map<String, RigMath.Placement> none = RigMath.blend(def, Tier.NORMAL, "idle", "slam_windup", 0.0);
        Map<String, RigMath.Placement> all = RigMath.blend(def, Tier.NORMAL, "idle", "slam_windup", 1.0);
        Map<String, RigMath.Placement> half = RigMath.blend(def, Tier.NORMAL, "idle", "slam_windup", 0.5);

        assertEquals(RigMath.place(def, Tier.NORMAL, "idle").get("fist_r").translation().y(),
                none.get("fist_r").translation().y(), EPS);
        assertEquals(RigMath.place(def, Tier.NORMAL, "slam_windup").get("fist_r").translation().y(),
                all.get("fist_r").translation().y(), EPS);
        // The pelvis offset runs (0,0,0) to (0,0,0.125): half way is 0.0625, and the Ry(pi) conjugation negates z.
        assertEquals(-0.0625f, half.get("pelvis").translation().z(), 1e-5f);
        assertEquals(1.75f, half.get("pelvis").translation().y(), 1e-5f);
    }

    @Test
    void aTierSizeScalesTheWholeBodyAboutTheFeet() {
        Map<String, RigMath.Placement> normal = RigMath.place(def, Tier.NORMAL, "slam_windup");
        Map<String, RigMath.Placement> mythic = RigMath.place(def, Tier.MYTHIC, "slam_windup");

        for (String part : normal.keySet()) {
            assertEquals(normal.get(part).translation().x() * 1.4f, mythic.get(part).translation().x(), 1e-4f, part);
            assertEquals(normal.get(part).translation().y() * 1.4f, mythic.get(part).translation().y(), 1e-4f, part);
            assertEquals(normal.get(part).translation().z() * 1.4f, mythic.get(part).translation().z(), 1e-4f, part);
            assertEquals(normal.get(part).scale() * 1.4f, mythic.get(part).scale(), 1e-5f, part);
        }
        assertEquals(1.0, RigMath.tierSize(Tier.NORMAL));
        assertEquals(1.2, RigMath.tierSize(Tier.HEROIC));
        assertEquals(1.4, RigMath.tierSize(Tier.MYTHIC));
    }

    @Test
    void aTierOnlyPlacesItsOwnParts() {
        assertEquals(def.partsFor(Tier.NORMAL).size(), RigMath.place(def, Tier.NORMAL, "idle").size());
        assertEquals(def.partsFor(Tier.MYTHIC).size(), RigMath.place(def, Tier.MYTHIC, "idle").size());
        assertFalse(RigMath.place(def, Tier.NORMAL, "idle").containsKey("crown"));
    }

    @Test
    void anchorsLandWhereThePythonTwinPutsThemInTheWorld() {
        // Rig frame -Z is the way the entity looks: at yaw 0 (south, +Z) the slam fist is ahead of the feet.
        Location feet = new Location(null, 3.0, 64.0, -2.0, 0f, 0f);

        Location fist = RigMath.anchor(def, Tier.NORMAL, "slam_hit", "slam_fist", feet);
        assertEquals(1.323759, fist.getX(), EPS);
        assertEquals(65.486068, fist.getY(), EPS);
        assertEquals(1.380085, fist.getZ(), EPS);

        Location turned = RigMath.anchor(def, Tier.NORMAL, "slam_hit", "slam_fist", new Location(null, 3.0, 64.0, -2.0, 90f, 0f));
        assertEquals(-0.380085, turned.getX(), EPS);
        assertEquals(-3.676241, turned.getZ(), EPS);

        Location heart = RigMath.anchor(def, Tier.NORMAL, "idle", "heart", new Location(null, 3.0, 64.0, -2.0, 37f, 0f));
        assertEquals(2.503931, heart.getX(), EPS);
        assertEquals(66.792495, heart.getY(), EPS);
        assertEquals(-1.341695, heart.getZ(), EPS);

        Location claw = RigMath.anchor(def, Tier.NORMAL, "sweep_hit", "sweep_claw", new Location(null, 3.0, 64.0, -2.0, 180f, 0f));
        assertEquals(5.448207, claw.getX(), EPS);
        assertEquals(66.986319, claw.getY(), EPS);
        assertEquals(-2.912773, claw.getZ(), EPS);
    }

    @Test
    void anchorsGrowWithTheTier() {
        Vector3d normal = RigMath.anchorInRig(def, Tier.NORMAL, "idle", "heart");
        Vector3d heroic = RigMath.anchorInRig(def, Tier.HEROIC, "idle", "heart");

        assertEquals(normal.y * 1.2, heroic.y, 1e-9);
        assertEquals(normal.z * 1.2, heroic.z, 1e-9);
    }

    @Test
    void anUnknownPoseOrAnchorIsAnErrorTheCallerCanSee() {
        assertThrows(IllegalArgumentException.class, () -> RigMath.place(def, Tier.NORMAL, "no_such_pose"));
        assertThrows(IllegalArgumentException.class, () -> RigMath.anchorInRig(def, Tier.NORMAL, "idle", "no_such_anchor"));
    }
}

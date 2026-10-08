package dev.sirbepy.mc.dragonballs.placed;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reads a display's spin back out of its transformation, for tests in any package. */
public final class FloatingBallsTestSupport {

    private FloatingBallsTestSupport() {
    }

    /** Heading of the display's x axis after its rotation, in radians. MockBukkit hands the rotation
     * back unnormalised (scale folded in), so it's normalised before use. */
    public static float yaw(Transformation transformation) {
        Vector3f x = new Quaternionf(transformation.getLeftRotation()).normalize().transform(new Vector3f(1, 0, 0));
        return (float) Math.atan2(x.z, x.x);
    }
}

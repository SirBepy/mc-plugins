package dev.sirbepy.mc.minibosses.rig;

import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.RigDefinition.Part;
import dev.sirbepy.mc.minibosses.rig.RigDefinition.PartPose;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The rig's pose composition, as the rig file's {@code _doc} defines it:
 * {@code local = T(pivot + offset) * R(rot)} with {@code R = Rz * Ry * Rx}, and
 * {@code world = world(parent) * local}. Each part is drawn by an item display whose Bukkit
 * {@link Transformation} is the part's world placement conjugated by Ry(pi), because the 26.3 item
 * renderer turns a model half a turn about Y: translation {@code (-x, y, -z)}, left rotation
 * {@code (-qx, qy, -qz, qw)}, uniform scale {@code part.scale * pose.scale * modelScale}.
 *
 * <p>A tier's size factor scales every translation and every part scale, which grows the whole body
 * about the point between its feet. Maths is done in doubles so a six-part chain does not drift.
 */
public final class RigMath {

    private RigMath() {
    }

    /** One part's display placement, before it becomes a Bukkit {@link Transformation}. */
    public record Placement(Vector3f translation, Quaternionf leftRotation, float scale) {
        public Transformation toTransformation() {
            return new Transformation(translation, leftRotation, new Vector3f(scale, scale, scale), new Quaternionf());
        }
    }

    /** How much bigger than authored a tier's body is; the base mob's hitbox grows by the same factor. */
    public static double tierSize(Tier tier) {
        return switch (tier) {
            case NORMAL -> 1.0;
            case HEROIC -> 1.2;
            case MYTHIC -> 1.4;
        };
    }

    /** Placements of every part {@code tier} spawns, standing exactly in {@code pose}. */
    public static Map<String, Placement> place(RigDefinition def, Tier tier, String pose) {
        return blend(def, tier, pose, pose, 0.0);
    }

    /**
     * Placements {@code f} of the way from pose {@code a} to pose {@code b} (0 is {@code a}, 1 is
     * {@code b}), interpolating each part's rotation, offset and scale linearly before composing.
     */
    public static Map<String, Placement> blend(RigDefinition def, Tier tier, String a, String b, double f) {
        double size = tierSize(tier);
        Map<String, Matrix4d> world = worldMatrices(def, a, b, f);
        Map<String, Placement> out = new LinkedHashMap<>();
        for (Part part : def.partsFor(tier)) {
            Matrix4d w = world.get(part.name());
            Vector3d t = w.getTranslation(new Vector3d());
            Quaterniond q = w.getNormalizedRotation(new Quaterniond());
            PartPose pa = def.poseOf(a, part.name());
            PartPose pb = def.poseOf(b, part.name());
            double poseScale = lerp(pa.scale(), pb.scale(), f);
            out.put(part.name(), new Placement(
                    new Vector3f((float) (-t.x * size), (float) (t.y * size), (float) (-t.z * size)),
                    new Quaternionf((float) -q.x, (float) q.y, (float) -q.z, (float) q.w),
                    (float) (part.scale() * poseScale * def.modelScale() * size)));
        }
        return out;
    }

    /**
     * Every part's origin (its pivot after the pose) in the rig frame, tier size applied; includes
     * parts {@code tier} does not spawn, which still carry their children.
     */
    public static Map<String, Vector3d> partOrigins(RigDefinition def, Tier tier, String pose) {
        double size = tierSize(tier);
        Map<String, Vector3d> out = new LinkedHashMap<>();
        worldMatrices(def, pose, pose, 0.0).forEach((name, w) -> out.put(name, w.getTranslation(new Vector3d()).mul(size)));
        return out;
    }

    /** A named anchor in the rig frame (feet origin, -Z front), tier size applied. */
    public static Vector3d anchorInRig(RigDefinition def, Tier tier, String pose, String anchor) {
        RigDefinition.Anchor a = def.anchor(anchor)
                .orElseThrow(() -> new IllegalArgumentException("unknown anchor " + anchor));
        Matrix4d w = worldMatrices(def, pose, pose, 0.0).get(a.part());
        return w.transformPosition(new Vector3d(a.local())).mul(tierSize(tier));
    }

    /**
     * A rig-frame point in the world for a body whose feet are at {@code feet} facing {@code feet}'s
     * yaw: {@code feet + Ry(-yaw) * Ry(pi) * point}, so the rig's -Z front becomes the way the entity
     * looks. Ry(-yaw) * Ry(pi) is one turn of {@code pi - yaw}.
     */
    public static Location toWorld(Vector3d rigPoint, Location feet) {
        Vector3d p = new Matrix3d().rotationY(Math.PI - Math.toRadians(feet.getYaw())).transform(rigPoint, new Vector3d());
        return new Location(feet.getWorld(), feet.getX() + p.x, feet.getY() + p.y, feet.getZ() + p.z);
    }

    /** {@link #anchorInRig} placed in the world around {@code feet}. */
    public static Location anchor(RigDefinition def, Tier tier, String pose, String anchor, Location feet) {
        return toWorld(anchorInRig(def, tier, pose, anchor), feet);
    }

    private static Map<String, Matrix4d> worldMatrices(RigDefinition def, String a, String b, double f) {
        Map<String, Matrix4d> world = new HashMap<>();
        BiFunction<Vector3d, Vector3d, Vector3d> mix = (x, y) -> new Vector3d(lerp(x.x, y.x, f), lerp(x.y, y.y, f), lerp(x.z, y.z, f));
        for (Part part : def.parts()) {
            PartPose pa = def.poseOf(a, part.name());
            PartPose pb = def.poseOf(b, part.name());
            Vector3d rot = mix.apply(pa.rot(), pb.rot());
            Vector3d offset = mix.apply(pa.offset(), pb.offset());
            Matrix4d local = new Matrix4d()
                    .translation(part.pivot().x + offset.x, part.pivot().y + offset.y, part.pivot().z + offset.z)
                    .rotateZYX(Math.toRadians(rot.z), Math.toRadians(rot.y), Math.toRadians(rot.x));
            Matrix4d parent = part.parent() == null ? null : world.get(part.parent());
            world.put(part.name(), parent == null ? local : new Matrix4d(parent).mul(local));
        }
        return world;
    }

    private static double lerp(double from, double to, double f) {
        return from + (to - from) * f;
    }
}

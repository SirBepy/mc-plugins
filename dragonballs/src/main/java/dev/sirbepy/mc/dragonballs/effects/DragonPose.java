package dev.sirbepy.mc.dragonballs.effects;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every item display of a summon dragon sits at a moment in time, as offsets from the summon
 * centre: pure math, no Bukkit types, so it is unit-testable and matches the preview renderer
 * (.for_bepy/dragon-models/scene.py) the looks were approved on (Joe, 2026-10-08).
 *
 * <p>Shenron is a chain of body segments along a centre line: an S-curved neck that sways as it
 * rises out of a slowly turning coil, so the body seems to flow. Porunga is a hulking upper body
 * (torso, head, two arms) over a serpent tail that curls toward the balls, facing a given yaw.
 *
 * <p>Model space is the server pack's (server/resourcepacks/dragon_models.py): every part faces
 * north (-Z) and is drawn {@link #MODEL_SCALE} times smaller than its rig, and an item display
 * turns its model to face the entity's yaw and pitch, so a part's scale is
 * {@code MODEL_SCALE * size}. Angles follow Minecraft: yaw 0 faces +Z, positive pitch looks down.
 */
final class DragonPose {

    /** dragon_models.py's MODEL_SCALE: rigs are drawn this many times smaller than their size. */
    static final double MODEL_SCALE = 1.5;

    static final int SHENRON_SEGMENTS = 38;
    // Segments that carry Shenron's two pairs of clawed legs.
    private static final int FRONT_LEGS = 4;
    private static final int HIND_LEGS = 20;
    // Segments from this one on taper toward the tail.
    private static final int TAPER_FROM = 24;
    // A body segment is 24 rig units long; spacing them at half that keeps bends from opening gaps.
    private static final double SEGMENT_SPACING = 24.0 / 16.0 * 0.5;
    private static final double HEAD_SCALE = 1.15;
    private static final double NECK_GAP = 1.5;
    private static final double HEAD_PITCH = 18.0; // looks down at the summoner

    static final int PORUNGA_TAIL_SEGMENTS = 12;

    /**
     * One display's pose.
     *
     * @param model item model path under {@code claudesmp:}, e.g. {@code dragon/shenron_head}
     */
    record Part(String model, double x, double y, double z, float yaw, float pitch, float scale) {
    }

    private DragonPose() {
    }

    // --- Shenron ---------------------------------------------------------------------------------

    /** Shenron at {@code t} seconds, {@code size} blocks per 16 rig units, head at {@code headHeight}. */
    static List<Part> shenron(double t, double size, double headHeight) {
        Polyline line = shenronLine(t, size, headHeight);
        List<Part> parts = new ArrayList<>(SHENRON_SEGMENTS + 1);

        double[] head = line.at(0);
        double[] behind = line.at(0.01);
        float headYaw = yaw(head[0] - behind[0], head[2] - behind[2]);
        parts.add(new Part("dragon/shenron_head", head[0], head[1], head[2], headYaw, (float) HEAD_PITCH,
                (float) (MODEL_SCALE * size * HEAD_SCALE)));

        double s = NECK_GAP * size;
        double spacing = SEGMENT_SPACING * size;
        for (int i = 0; i < SHENRON_SEGMENTS; i++) {
            double taper = i < TAPER_FROM ? 1.0 : 1.0 - 0.5 * (i - TAPER_FROM) / (SHENRON_SEGMENTS - TAPER_FROM);
            double[] p = line.at(s);
            double[] q = line.at(s + 0.3);
            String model = i == SHENRON_SEGMENTS - 1 ? "dragon/shenron_tail"
                    : (i == FRONT_LEGS || i == HIND_LEGS) ? "dragon/shenron_body_legs" : "dragon/shenron_body";
            parts.add(new Part(model, p[0], p[1], p[2], yaw(p[0] - q[0], p[2] - q[2]),
                    pitch(p[0] - q[0], p[1] - q[1], p[2] - q[2]), (float) (MODEL_SCALE * size * taper)));
            s += spacing * (i < TAPER_FROM ? 1.0 : Math.max(0.6, taper));
        }
        return parts;
    }

    /** The head-to-tail centre line, sampled densely and indexed by arc length. */
    static Polyline shenronLine(double t, double size, double headHeight) {
        double radius = 4.0 * size;
        double rise = 1.3 * size;      // blocks of height the coil drops per radian
        double turns = 1.6;
        double neckHeight = 9.0 * size;
        double swingAmp = 2.2 * size;  // sideways swing of the neck's S
        double phase = t * 2 * Math.PI / 24;  // the coil turns once every 24 s
        double wave = t * 2 * Math.PI / 5;    // the S sways every 5 s

        double cx = radius * Math.cos(phase);
        double cz = radius * Math.sin(phase);
        double topY = headHeight - neckHeight;
        double radialX = Math.cos(phase);
        double radialZ = Math.sin(phase);
        double tangentX = -Math.sin(phase);
        double tangentZ = Math.cos(phase);

        int total = 600;
        int neck = total / 3;
        double[][] pts = new double[total][];
        for (int i = 0; i < neck; i++) {
            double v = 1.0 - (double) i / neck;   // 1 at the head, 0 where the neck meets the coil
            double swing = Math.sin(Math.PI * 1.6 * v + 0.3 * Math.sin(wave)) * swingAmp
                    * Math.sin(Math.PI * v * 0.95 + 0.05);
            double lean = v * v * 2.5 * size;      // the head leans out over the summoner
            double side = Math.sin(Math.PI * v + wave) * 0.6 * swingAmp * v;
            double out = lean + side * 0.3;
            pts[i] = new double[] {cx + tangentX * swing + radialX * out, topY + neckHeight * v,
                    cz + tangentZ * swing + radialZ * out};
        }
        int coil = total - neck;
        for (int i = 0; i < coil; i++) {
            double down = turns * 2 * Math.PI * i / coil;
            double a = phase - down;
            double r = radius + 0.4 * size * Math.sin(down * 3 + wave);
            pts[neck + i] = new double[] {r * Math.cos(a), topY - down * rise, r * Math.sin(a)};
        }
        return new Polyline(pts);
    }

    // --- Porunga ---------------------------------------------------------------------------------

    // Part pivots in rig units, from dragon_models.py's PARTS.
    private static final double[] TORSO = {0, 22, 2};
    private static final double[] HEAD = {0, 30, -12};
    private static final double[] ARM_LEFT = {-21, 10, -4};
    private static final double[] ARM_RIGHT = {21, 10, -4};
    /** Rig units from Porunga's waist up to his horn tips and crest. */
    static final double PORUNGA_HEIGHT_ABOVE_WAIST = 50;

    /** Porunga at {@code t} seconds, {@code size} blocks per 16 rig units, waist at {@code waistHeight}, facing {@code facingYaw}. */
    static List<Part> porunga(double t, double size, double waistHeight, float facingYaw) {
        List<Part> parts = new ArrayList<>(4 + PORUNGA_TAIL_SEGMENTS);
        double bob = Math.sin(t * 2 * Math.PI / 4) * 0.3 * size;
        double sway = Math.sin(t * 2 * Math.PI / 5);
        float scale = (float) (MODEL_SCALE * size);
        double yawRad = Math.toRadians(facingYaw);

        parts.add(rigPart("dragon/porunga_torso", TORSO, size, waistHeight + bob, yawRad, facingYaw, 0f, scale));
        parts.add(rigPart("dragon/porunga_head", HEAD, size, waistHeight + bob, yawRad, facingYaw,
                (float) (4 * Math.sin(t * 2 * Math.PI / 6)), scale));
        parts.add(rigPart("dragon/porunga_arm_left", ARM_LEFT, size, waistHeight + bob, yawRad, facingYaw,
                (float) (-6 * sway), scale));
        parts.add(rigPart("dragon/porunga_arm_right", ARM_RIGHT, size, waistHeight + bob, yawRad, facingYaw,
                (float) (6 * sway), scale));

        double length = 7.0 * size;
        double step = length / PORUNGA_TAIL_SEGMENTS;
        for (int i = 0; i < PORUNGA_TAIL_SEGMENTS; i++) {
            double s = (i + 0.5) * step;
            double taper = 1.0 - 0.6 * i / PORUNGA_TAIL_SEGMENTS;
            double[] p = rotate(tailPoint(s, t, size, length), yawRad);
            double[] q = rotate(tailPoint(s + 0.05, t, size, length), yawRad);
            double dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2];
            boolean vertical = Math.abs(dy) / Math.sqrt(dx * dx + dy * dy + dz * dz) > 0.97;
            String model = i == PORUNGA_TAIL_SEGMENTS - 1 ? "dragon/porunga_tail_tip" : "dragon/porunga_tail";
            parts.add(new Part(model, p[0], waistHeight + bob + p[1], p[2], vertical ? facingYaw : yaw(dx, dz),
                    pitch(dx, dy, dz), (float) (MODEL_SCALE * size * 0.8 * taper)));
        }
        return parts;
    }

    /**
     * A tail point {@code s} blocks down from the waist in Porunga's own frame (+Z his front): it
     * hangs down with a slow sideways sway, then curls into a loop near the bottom.
     */
    static double[] tailPoint(double s, double t, double size, double length) {
        double straight = length * 0.55;
        double sway = Math.sin(t * 2 * Math.PI / 6) * 0.25;
        if (s <= straight) {
            double k = s / straight;
            return new double[] {Math.sin(k * 1.2 + sway) * 0.6 * size, -s, 0.4 * size * k};
        }
        double ex = Math.sin(1.2 + sway) * 0.6 * size;
        double r = 1.4 * size;
        double a = (s - straight) / r;
        return new double[] {ex + r - r * Math.cos(a), -straight - r * Math.sin(a), 0.4 * size};
    }

    /** A rig pivot turned into a display position: the item display's own half-turn puts the
     * rig's -Z front at +Z, then the whole body turns to {@code yaw}. */
    private static Part rigPart(String model, double[] pivot, double size, double baseY, double yawRad,
                                float yaw, float pitch, float scale) {
        double[] local = {-pivot[0] * size / 16, pivot[1] * size / 16, -pivot[2] * size / 16};
        double[] p = rotate(local, yawRad);
        return new Part(model, p[0], baseY + p[1], p[2], yaw, pitch, scale);
    }

    /** Turns a local offset (+Z = front) to face Minecraft yaw {@code yawRad}. */
    static double[] rotate(double[] v, double yawRad) {
        double c = Math.cos(yawRad), s = Math.sin(yawRad);
        return new double[] {v[0] * c - v[2] * s, v[1], v[0] * s + v[2] * c};
    }

    /** Minecraft yaw of a direction: 0 faces +Z, 90 faces -X. */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Minecraft pitch of a direction: positive looks down. */
    static float pitch(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return 0f;
        }
        return (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dy / len))));
    }

    /** Points along a line, looked up by arc length from the first point. */
    static final class Polyline {
        private final double[][] pts;
        private final double[] cumulative;

        Polyline(double[][] pts) {
            this.pts = pts;
            this.cumulative = new double[pts.length];
            for (int i = 1; i < pts.length; i++) {
                double dx = pts[i][0] - pts[i - 1][0];
                double dy = pts[i][1] - pts[i - 1][1];
                double dz = pts[i][2] - pts[i - 1][2];
                cumulative[i] = cumulative[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }

        double length() {
            return cumulative[cumulative.length - 1];
        }

        double[] at(double s) {
            double clamped = Math.max(0, Math.min(s, length()));
            int lo = 0, hi = cumulative.length - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] <= clamped) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            double span = cumulative[hi] - cumulative[lo];
            double f = span < 1e-12 ? 0 : (clamped - cumulative[lo]) / span;
            return new double[] {
                pts[lo][0] + (pts[hi][0] - pts[lo][0]) * f,
                pts[lo][1] + (pts[hi][1] - pts[lo][1]) * f,
                pts[lo][2] + (pts[hi][2] - pts[lo][2]) * f,
            };
        }
    }
}

package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * An area attack's footprint: pure geometry on the ground plane (X and Z), plus a vertical band of
 * {@link #VERTICAL_REACH} blocks around the shape's own height so a player on a ledge well above or
 * below is not hit. No Bukkit calls beyond reading a {@link Location}, so shapes are testable bare.
 * Yaw follows Minecraft: 0 faces +Z (south), 90 faces -X (west).
 */
public sealed interface Shape permits Shape.Circle, Shape.Ring, Shape.Sector, Shape.Line {

    /** How far above or below a shape's own height a player still counts as inside it. */
    double VERTICAL_REACH = 3.0;

    /** Spacing floor so a huge shape does not turn into thousands of particles per frame. */
    int MAX_POINTS_PER_EDGE = 160;

    boolean contains(Location at);

    /** Where the start and resolve sounds play. */
    Location anchor();

    /** Points tracing the edge about {@code spacing} blocks apart, at the shape's height. */
    List<Vector> outline(double spacing);

    /**
     * The shape grown to {@code t} (0 to 1) of its full reach; {@code grown(1)} is the shape itself.
     * Drawn as a second, brighter outline while the warning runs, so players can read how long is
     * left before it lands.
     */
    Shape grown(double t);

    record Circle(Location center, double radius) implements Shape {
        @Override
        public boolean contains(Location at) {
            return Geometry.sameWorld(center, at) && Geometry.withinHeight(center.getY(), at)
                    && Geometry.distSq(center, at) <= radius * radius;
        }

        @Override
        public Location anchor() {
            return center;
        }

        @Override
        public List<Vector> outline(double spacing) {
            return Geometry.arc(center, radius, 0, Math.PI * 2, spacing);
        }

        @Override
        public Shape grown(double t) {
            return new Circle(center, radius * t);
        }
    }

    /** A donut: safe inside {@code innerRadius}, hit between the two radii. */
    record Ring(Location center, double innerRadius, double outerRadius) implements Shape {
        @Override
        public boolean contains(Location at) {
            if (!Geometry.sameWorld(center, at) || !Geometry.withinHeight(center.getY(), at)) {
                return false;
            }
            double d = Geometry.distSq(center, at);
            return d >= innerRadius * innerRadius && d <= outerRadius * outerRadius;
        }

        @Override
        public Location anchor() {
            return center;
        }

        @Override
        public List<Vector> outline(double spacing) {
            List<Vector> points = new ArrayList<>(Geometry.arc(center, outerRadius, 0, Math.PI * 2, spacing));
            if (innerRadius > 0) {
                points.addAll(Geometry.arc(center, innerRadius, 0, Math.PI * 2, spacing));
            }
            return points;
        }

        @Override
        public Shape grown(double t) {
            // The hole stays put; the band fills outward from it, reaching the outer radius at t = 1.
            return new Ring(center, innerRadius, innerRadius + (outerRadius - innerRadius) * t);
        }
    }

    /** A cone from {@code origin}: {@code halfAngleDegrees} either side of {@code yawDegrees}, out to {@code radius}. */
    record Sector(Location origin, double yawDegrees, double halfAngleDegrees, double radius) implements Shape {
        @Override
        public boolean contains(Location at) {
            if (!Geometry.sameWorld(origin, at) || !Geometry.withinHeight(origin.getY(), at)) {
                return false;
            }
            double dx = at.getX() - origin.getX();
            double dz = at.getZ() - origin.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > radius * radius) {
                return false;
            }
            if (distSq < 1.0e-9) {
                return true; // standing on the origin has no direction to be outside of
            }
            double yaw = Math.toRadians(yawDegrees);
            double facingX = -Math.sin(yaw);
            double facingZ = Math.cos(yaw);
            double cos = (dx * facingX + dz * facingZ) / Math.sqrt(distSq);
            return cos >= Math.cos(Math.toRadians(halfAngleDegrees));
        }

        @Override
        public Location anchor() {
            return origin;
        }

        @Override
        public List<Vector> outline(double spacing) {
            if (halfAngleDegrees >= 180) {
                return Geometry.arc(origin, radius, 0, Math.PI * 2, spacing);
            }
            double yaw = Math.toRadians(yawDegrees);
            double half = Math.toRadians(halfAngleDegrees);
            // Arc angles are measured from +X toward +Z; the facing direction is yaw + 90 degrees in that frame.
            double facing = yaw + Math.PI / 2;
            List<Vector> points = new ArrayList<>(Geometry.arc(origin, radius, facing - half, facing + half, spacing));
            for (double sign : new double[] {-1, 1}) {
                double angle = facing + sign * half;
                points.addAll(Geometry.segment(origin.getX(), origin.getZ(),
                        origin.getX() + Math.cos(angle) * radius, origin.getZ() + Math.sin(angle) * radius,
                        origin.getY(), spacing));
            }
            return points;
        }

        @Override
        public Shape grown(double t) {
            return new Sector(origin, yawDegrees, halfAngleDegrees, radius * t);
        }
    }

    /** A beam: everything within {@code halfWidth} of the segment {@code from} to {@code to}. */
    record Line(Location from, Location to, double halfWidth) implements Shape {
        @Override
        public boolean contains(Location at) {
            if (!Geometry.sameWorld(from, at)) {
                return false;
            }
            double ax = from.getX();
            double az = from.getZ();
            double bx = to.getX() - ax;
            double bz = to.getZ() - az;
            double px = at.getX() - ax;
            double pz = at.getZ() - az;
            double lengthSq = bx * bx + bz * bz;
            double along = lengthSq < 1.0e-9 ? 0 : Math.max(0, Math.min(1, (px * bx + pz * bz) / lengthSq));
            double dx = px - bx * along;
            double dz = pz - bz * along;
            double y = from.getY() + (to.getY() - from.getY()) * along;
            return Geometry.withinHeight(y, at) && dx * dx + dz * dz <= halfWidth * halfWidth;
        }

        @Override
        public Location anchor() {
            return from.clone().add(to.clone().subtract(from).multiply(0.5));
        }

        @Override
        public List<Vector> outline(double spacing) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0e-9) {
                return Geometry.arc(from, halfWidth, 0, Math.PI * 2, spacing);
            }
            double nx = -dz / length * halfWidth; // perpendicular, scaled to the half width
            double nz = dx / length * halfWidth;
            double y = from.getY();
            List<Vector> points = new ArrayList<>();
            points.addAll(Geometry.segment(from.getX() + nx, from.getZ() + nz, to.getX() + nx, to.getZ() + nz, y, spacing));
            points.addAll(Geometry.segment(from.getX() - nx, from.getZ() - nz, to.getX() - nx, to.getZ() - nz, y, spacing));
            points.addAll(Geometry.segment(from.getX() + nx, from.getZ() + nz, from.getX() - nx, from.getZ() - nz, y, spacing));
            points.addAll(Geometry.segment(to.getX() + nx, to.getZ() + nz, to.getX() - nx, to.getZ() - nz, y, spacing));
            return points;
        }

        @Override
        public Shape grown(double t) {
            return new Line(from, from.clone().add(to.clone().subtract(from).multiply(t)), halfWidth);
        }
    }

    /** Ground-plane helpers shared by the shapes; the helpers themselves are package-private. */
    final class Geometry {
        private Geometry() {
        }

        static boolean sameWorld(Location a, Location b) {
            return a.getWorld() != null && a.getWorld().equals(b.getWorld());
        }

        static boolean withinHeight(double y, Location at) {
            return Math.abs(at.getY() - y) <= VERTICAL_REACH;
        }

        static double distSq(Location a, Location b) {
            double dx = a.getX() - b.getX();
            double dz = a.getZ() - b.getZ();
            return dx * dx + dz * dz;
        }

        static List<Vector> arc(Location center, double radius, double fromAngle, double toAngle, double spacing) {
            double span = toAngle - fromAngle;
            int count = (int) Math.max(8, Math.min(MAX_POINTS_PER_EDGE, Math.ceil(Math.abs(span) * radius / spacing)));
            boolean full = Math.abs(span) >= Math.PI * 2 - 1.0e-9;
            int steps = full ? count : count + 1; // a closed circle must not draw its first point twice
            List<Vector> points = new ArrayList<>(steps);
            for (int i = 0; i < steps; i++) {
                double angle = fromAngle + span * i / count;
                points.add(new Vector(center.getX() + Math.cos(angle) * radius, center.getY(),
                        center.getZ() + Math.sin(angle) * radius));
            }
            return points;
        }

        static List<Vector> segment(double x1, double z1, double x2, double z2, double y, double spacing) {
            double length = Math.hypot(x2 - x1, z2 - z1);
            int count = (int) Math.max(1, Math.min(MAX_POINTS_PER_EDGE, Math.ceil(length / spacing)));
            List<Vector> points = new ArrayList<>(count + 1);
            for (int i = 0; i <= count; i++) {
                double f = (double) i / count;
                points.add(new Vector(x1 + (x2 - x1) * f, y, z1 + (z2 - z1) * f));
            }
            return points;
        }
    }
}

package dev.sirbepy.mc.merfolk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* over the blocks a merfolk's 2-tall body fits through, since vanilla's villager pathing can't
 * route underwater. Water counts like air: a merfolk may swim up, down and across through any water,
 * as long as its head stays under the surface (at the surface vanilla's jump bobs it up and down).
 * Out of water it only walks: standing on solid ground, one block up or down per step, so it can
 * still climb out onto a shore over a 1-block ledge. Off-floor swimming costs a little more than
 * walking the floor and land more again, so a merfolk keeps to the sea floor unless swimming over
 * something is clearly shorter.
 */
public final class WaterPath {

    public enum Cell { SOLID, WATER, OPEN }

    /** What occupies one block, for the search (SOLID for anything a merfolk must not enter). */
    @FunctionalInterface
    public interface Terrain {
        Cell at(int x, int y, int z);
    }

    /** One block a merfolk's feet pass through; {@code floor} when it stands on solid ground there. */
    public record Step(int x, int y, int z, boolean floor) {}

    static final double SWIM_COST = 1.5;
    static final double LAND_COST = 2.0;
    /** A step counts as there when its middle is less than this far above or below the target. */
    static final double REACH_Y = 1.5;

    private final Map<Long, Cell> cells = new HashMap<>();
    private final Terrain terrain;

    WaterPath(Terrain terrain) {
        this.terrain = terrain;
    }

    /**
     * Steps from the block after {@code (sx, sy, sz)} to a block within {@code reach} (horizontally)
     * of the target point, expanding at most {@code budget} blocks. When the target can't be reached
     * within the budget, the steps lead to the block that got closest to it instead, so a merfolk
     * still makes progress; empty when no block is closer than where it stands.
     */
    public static List<Step> find(Terrain terrain, int sx, int sy, int sz, double tx, double ty, double tz,
                                  double reach, int budget) {
        return new WaterPath(terrain).search(sx, sy, sz, tx, ty, tz, reach, budget);
    }

    private record Open(Node node, double f) {}

    private static final class Node {
        final int x;
        final int y;
        final int z;
        final boolean floor;
        double g;
        Node parent;
        boolean closed;

        Node(int x, int y, int z, boolean floor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.floor = floor;
        }
    }

    private List<Step> search(int sx, int sy, int sz, double tx, double ty, double tz, double reach, int budget) {
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Node start = new Node(sx, sy, sz, cell(sx, sy - 1, sz) == Cell.SOLID);
        nodes.put(key(sx, sy, sz), start);
        open.add(new Open(start, distance(start, tx, ty, tz)));
        Node best = start;
        double bestH = distance(start, tx, ty, tz);
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            Node node = open.poll().node;
            if (node.closed) continue;
            node.closed = true;
            expanded++;
            if (arrived(node, tx, ty, tz, reach)) return path(node);
            double h = distance(node, tx, ty, tz);
            if (h < bestH) {
                best = node;
                bestH = h;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx == 0 && dy == 0 && dz == 0) || !canMove(node, dx, dy, dz)) continue;
                        int x = node.x + dx;
                        int y = node.y + dy;
                        int z = node.z + dz;
                        boolean floor = cell(x, y - 1, z) == Cell.SOLID;
                        double cost = Math.sqrt(dx * dx + dy * dy + dz * dz)
                                * (!floor ? SWIM_COST : cell(x, y, z) == Cell.OPEN ? LAND_COST : 1);
                        double g = node.g + cost;
                        long k = key(x, y, z);
                        Node next = nodes.get(k);
                        if (next == null) {
                            next = new Node(x, y, z, floor);
                            nodes.put(k, next);
                        } else if (next.closed || g >= next.g) {
                            continue;
                        }
                        next.g = g;
                        next.parent = node;
                        open.add(new Open(next, g + distance(next, tx, ty, tz)));
                    }
                }
            }
        }
        return best == start ? List.of() : path(best);
    }

    /**
     * Whether the body fits with its feet in this block: neither block solid, and either standing on
     * solid ground or (in water) fully under the surface. Feet in air need ground: no falling, no
     * floating on top of the water.
     */
    boolean fits(int x, int y, int z) {
        Cell feet = cell(x, y, z);
        Cell head = cell(x, y + 1, z);
        if (feet == Cell.SOLID || head == Cell.SOLID) return false;
        boolean floor = cell(x, y - 1, z) == Cell.SOLID;
        return feet == Cell.OPEN ? floor : floor || head == Cell.WATER;
    }

    /**
     * One step: up or down by at most one block. Diagonals only on the level, with both side columns
     * clear so it can't cut a corner. Going up needs headroom above where it stands; straight up only
     * through water (it can't jump in mid-air). Going down sideways needs the body's way clear at its
     * current level first.
     */
    private boolean canMove(Node from, int dx, int dy, int dz) {
        int x = from.x + dx;
        int y = from.y + dy;
        int z = from.z + dz;
        if (dx != 0 && dz != 0) {
            if (dy != 0) return false;
            if (!clear(from.x + dx, from.y, from.z) || !clear(from.x, from.y, from.z + dz)) return false;
        }
        if (!fits(x, y, z)) return false;
        if (dy == 1) {
            if (cell(from.x, from.y + 2, from.z) == Cell.SOLID) return false;
            if (dx == 0 && dz == 0 && cell(x, y, z) != Cell.WATER) return false;
        } else if (dy == -1 && (dx != 0 || dz != 0)) {
            if (!clear(x, from.y, z)) return false;
        }
        return true;
    }

    /** Both blocks of a 2-tall body non-solid. */
    private boolean clear(int x, int y, int z) {
        return cell(x, y, z) != Cell.SOLID && cell(x, y + 1, z) != Cell.SOLID;
    }

    private static boolean arrived(Node node, double tx, double ty, double tz, double reach) {
        return Math.hypot(node.x + 0.5 - tx, node.z + 0.5 - tz) <= reach && Math.abs(node.y + 0.5 - ty) < REACH_Y;
    }

    private static double distance(Node node, double tx, double ty, double tz) {
        double dx = node.x + 0.5 - tx;
        double dy = node.y + 0.5 - ty;
        double dz = node.z + 0.5 - tz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<Step> path(Node end) {
        List<Step> steps = new ArrayList<>();
        for (Node node = end; node.parent != null; node = node.parent) {
            steps.add(new Step(node.x, node.y, node.z, node.floor));
        }
        Collections.reverse(steps);
        return steps;
    }

    private Cell cell(int x, int y, int z) {
        return cells.computeIfAbsent(key(x, y, z), k -> terrain.at(x, y, z));
    }

    /** x and z in 26 bits each (the world border is at 30 million), y in 12. */
    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}

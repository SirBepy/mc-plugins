package dev.sirbepy.mc.dragonballs.scatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Whether a planned {@link UndergroundShaftPlanner.ShaftShape} is safe to actually carve. Pure
 * logic over a caller-supplied {@code blocked} predicate (relative {x, y, z} offset -&gt; unsafe) so
 * it's testable without a world; {@code ScatterService} backs that predicate with real block/liquid/
 * container/bedrock checks.
 */
public final class UndergroundSafety {

    // Encodes a signed int in [-maxMagnitude, maxMagnitude] into a non-negative bit field; offsets
    // here never approach this range (a shaft is at most a few dozen blocks deep/wide).
    private static final int OFFSET_BIAS = 1 << 15;

    private UndergroundSafety() {
    }

    /**
     * Rejects a shape whose chamber floor would land within {@code minClearance} blocks of
     * {@code minHeight} (the world's min-height abort condition), or where {@code blocked} flags any
     * block to be carved, or any block directly touching one (checked by dilating every clear offset
     * by its 6 face-neighbours). {@code minHeight} is relative to the ground block the shaft starts
     * from (i.e. the caller passes {@code world.getMinHeight() - groundY}), matching every offset
     * in {@code shape}, which is itself relative to that same ground block.
     */
    public static boolean isSafe(UndergroundShaftPlanner.ShaftShape shape, int minHeight, int minClearance,
                                  Predicate<int[]> blocked) {
        int floorY = shape.floorOffset()[1];
        if (floorY - minHeight < minClearance) {
            return false;
        }
        for (int[] offset : dilate(shape.clearOffsets())) {
            if (blocked.test(offset)) {
                return false;
            }
        }
        return true;
    }

    private static final int[][] FACE_NEIGHBOURS = {
            {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static List<int[]> dilate(List<int[]> offsets) {
        Set<Long> seen = new HashSet<>();
        List<int[]> result = new ArrayList<>();
        for (int[] offset : offsets) {
            for (int[] delta : FACE_NEIGHBOURS) {
                int[] point = {offset[0] + delta[0], offset[1] + delta[1], offset[2] + delta[2]};
                if (seen.add(encode(point))) {
                    result.add(point);
                }
            }
        }
        return result;
    }

    private static long encode(int[] point) {
        return ((long) (point[0] + OFFSET_BIAS) << 42)
                | ((long) (point[1] + OFFSET_BIAS) << 21)
                | (long) (point[2] + OFFSET_BIAS);
    }
}

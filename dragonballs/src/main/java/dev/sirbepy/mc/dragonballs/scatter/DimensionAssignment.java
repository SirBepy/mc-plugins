package dev.sirbepy.mc.dragonballs.scatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Which of the 7 stars go to the overworld, Nether, and End on one scatter. Pure, so the random
 * split is unit-testable without a server. */
public record DimensionAssignment(List<Integer> overworld, List<Integer> nether, List<Integer> end) {

    /** Shuffles stars 1..7, then slices off {@code netherCount} and {@code endCount} of them (both
     * clamped so they never exceed what's left); whatever remains stays in the overworld. */
    public static DimensionAssignment assign(Random random, int netherCount, int endCount) {
        List<Integer> stars = new ArrayList<>(7);
        for (int star = 1; star <= 7; star++) {
            stars.add(star);
        }
        Collections.shuffle(stars, random);

        int nether = Math.max(0, Math.min(netherCount, stars.size()));
        List<Integer> netherStars = List.copyOf(stars.subList(0, nether));

        int end = Math.max(0, Math.min(endCount, stars.size() - nether));
        List<Integer> endStars = List.copyOf(stars.subList(nether, nether + end));

        List<Integer> overworldStars = List.copyOf(stars.subList(nether + end, stars.size()));
        return new DimensionAssignment(overworldStars, netherStars, endStars);
    }
}

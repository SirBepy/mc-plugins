package dev.sirbepy.mc.dragonballs.scatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Which of the 7 stars in one {@code scatterAll} get the underground/point-of-interest treatment
 * instead of a plain surface placement. Pure (no Bukkit) so the "how many, which ones, never both"
 * rule is testable without a server: a shuffle plus two disjoint slices guarantees no star is ever
 * picked for both, without an explicit overlap check.
 */
public final class StarSelection {

    private StarSelection() {
    }

    public record Plan(Set<Integer> underground, Set<Integer> poi) {
    }

    /** Pass 0/0 for a set that gets no underground stars (nether) or no special stars at all (end). */
    public static Plan choose(Random random, int undergroundMin, int undergroundMax, int poiMin, int poiMax) {
        List<Integer> stars = new ArrayList<>(7);
        for (int star = 1; star <= 7; star++) {
            stars.add(star);
        }
        Collections.shuffle(stars, random);

        int undergroundCount = Math.min(between(random, undergroundMin, undergroundMax), stars.size());
        int poiCount = Math.min(between(random, poiMin, poiMax), stars.size() - undergroundCount);

        Set<Integer> underground = new LinkedHashSet<>(stars.subList(0, undergroundCount));
        Set<Integer> poi = new LinkedHashSet<>(stars.subList(undergroundCount, undergroundCount + poiCount));
        return new Plan(underground, poi);
    }

    private static int between(Random random, int min, int max) {
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + random.nextInt(max - min + 1);
    }
}

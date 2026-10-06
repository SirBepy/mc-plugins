package dev.sirbepy.mc.dragonballs.scatter;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarSelectionTest {

    @Test
    void overworldPicksOneOrTwoUndergroundAndOneOrTwoDifferentPoiStars() {
        for (long seed = 0; seed < 200; seed++) {
            StarSelection.Plan plan = StarSelection.choose(new Random(seed), 1, 2, 1, 2);

            assertTrue(plan.underground().size() >= 1 && plan.underground().size() <= 2,
                    "underground count out of range: " + plan.underground());
            assertTrue(plan.poi().size() >= 1 && plan.poi().size() <= 2,
                    "poi count out of range: " + plan.poi());
            for (int star : plan.underground()) {
                assertTrue(star >= 1 && star <= 7);
                assertTrue(!plan.poi().contains(star), "star " + star + " picked for both underground and poi");
            }
        }
    }

    @Test
    void netherGetsPoiStarsAndNoUndergroundStars() {
        StarSelection.Plan plan = StarSelection.choose(new Random(7), 0, 0, 1, 2);

        assertEquals(0, plan.underground().size());
        assertTrue(plan.poi().size() >= 1 && plan.poi().size() <= 2);
    }

    @Test
    void endGetsNeitherUndergroundNorPoiStars() {
        StarSelection.Plan plan = StarSelection.choose(new Random(7), 0, 0, 0, 0);

        assertEquals(0, plan.underground().size());
        assertEquals(0, plan.poi().size());
    }

    @Test
    void neverPicksMoreThanAllSevenStarsBetweenBothGroups() {
        StarSelection.Plan plan = StarSelection.choose(new Random(3), 5, 7, 5, 7);

        assertTrue(plan.underground().size() + plan.poi().size() <= 7);
    }
}

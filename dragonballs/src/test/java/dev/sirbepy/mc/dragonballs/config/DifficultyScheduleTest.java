package dev.sirbepy.mc.dragonballs.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DifficultyScheduleTest {

    private static final List<DifficultyTier> SCHEDULE = List.of(
            new DifficultyTier(1000, 3000),
            new DifficultyTier(1500, 3500),
            new DifficultyTier(1500, 4000),
            new DifficultyTier(2000, 4500)
    );

    @Test
    void looksUpTheTierForEachExactWishCount() {
        assertSame(SCHEDULE.get(0), DifficultySchedule.tierFor(SCHEDULE, 0));
        assertSame(SCHEDULE.get(2), DifficultySchedule.tierFor(SCHEDULE, 2));
        assertSame(SCHEDULE.get(3), DifficultySchedule.tierFor(SCHEDULE, 3));
    }

    @Test
    void clampsPastTheEndOfTheListToTheHardestTier() {
        assertSame(SCHEDULE.get(3), DifficultySchedule.tierFor(SCHEDULE, 4));
        assertSame(SCHEDULE.get(3), DifficultySchedule.tierFor(SCHEDULE, 1000));
    }

    @Test
    void clampsNegativeWishCountsToTheFirstTier() {
        assertSame(SCHEDULE.get(0), DifficultySchedule.tierFor(SCHEDULE, -5));
    }

    @Test
    void anEmptyScheduleIsRejectedRatherThanIndexingOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> DifficultySchedule.tierFor(List.of(), 0));
    }

    @Test
    void tierFieldsComeThroughUnchanged() {
        DifficultyTier tier = DifficultySchedule.tierFor(SCHEDULE, 3);
        assertEquals(2000, tier.minRadius());
        assertEquals(4500, tier.maxRadius());
    }
}

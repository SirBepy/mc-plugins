package dev.sirbepy.mc.endfight.tier;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TierTest {

    // The health per tier is Dragonkind Evolved's and the reward table is keyed on it.
    @Test
    void tiersRunFromFledgelingToAscendedWithTheDatapacksHealth() {
        assertArrayEquals(new int[] {250, 350, 500, 750, 850, 1000, 1250},
                Arrays.stream(Tier.values()).mapToInt(Tier::health).toArray());
    }
}

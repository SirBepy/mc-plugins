package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Talent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Arcanist's tree shape: 33 nodes, 2/3/3/2/1 per tier per branch, unique ids. */
class ArcanistTreeTest {

    private final List<Talent> talents = ArcanistTalents.build();

    @Test
    void hasThirtyThreeNodes() {
        assertEquals(33, talents.size());
    }

    @Test
    void everyIdIsUnique() {
        Set<String> ids = new HashSet<>();
        for (Talent t : talents) assertTrue(ids.add(t.id()), "duplicate id: " + t.id());
    }

    @Test
    void everyBranchHasTheTwoThreeThreeTwoOneShape() {
        for (int branch = 0; branch < 3; branch++) {
            Map<Integer, Integer> perTier = new HashMap<>();
            for (Talent t : talents) {
                if (t.branch() == branch) perTier.merge(t.tier(), 1, Integer::sum);
            }
            assertEquals(2, perTier.get(1), "branch " + branch + " tier 1");
            assertEquals(3, perTier.get(2), "branch " + branch + " tier 2");
            assertEquals(3, perTier.get(3), "branch " + branch + " tier 3");
            assertEquals(2, perTier.get(4), "branch " + branch + " tier 4");
            assertEquals(1, perTier.get(5), "branch " + branch + " tier 5");
        }
    }

    @Test
    void everyKeystoneReplacesAbilityOne() {
        for (Talent t : talents) {
            if (t.keystone()) {
                assertEquals(dev.sirbepy.mc.classes.talent.Slot.ONE, t.replaces(), t.id() + " should replace Mend");
            }
        }
    }

    @Test
    void threeBranchNamesMatchTheSpec() {
        assertEquals(List.of("Pyromancer", "Frost", "Chronomancer"), new ArcanistKit(null).branchNames());
    }
}

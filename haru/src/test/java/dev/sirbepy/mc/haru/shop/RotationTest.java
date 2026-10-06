package dev.sirbepy.mc.haru.shop;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationTest {

    private final List<Integer> pool = IntStream.range(0, 20).boxed().toList();

    @Test
    void samePeriodSamePickDistinctEntries() {
        List<Integer> a = Rotation.pick(pool, "x", 7, 6);
        assertEquals(a, Rotation.pick(pool, "x", 7, 6));
        assertEquals(6, new HashSet<>(a).size());
    }

    @Test
    void smallPoolsGiveWhatTheyHave() {
        assertEquals(2, Rotation.pick(List.of(1, 2), "x", 1, 6).size());
    }

    @Test
    void legendaryFollowsTheChance() {
        assertTrue(IntStream.range(0, 50).noneMatch(i -> Rotation.legendary(pool, i, 0.0).isPresent()));
        assertTrue(IntStream.range(0, 50).allMatch(i -> Rotation.legendary(pool, i, 1.0).isPresent()));
        long hits = IntStream.range(0, 2000).filter(i -> Rotation.legendary(pool, i, 0.15).isPresent()).count();
        assertTrue(hits > 200 && hits < 400, "about 15% of rotations, got " + hits);
    }
}

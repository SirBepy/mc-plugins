package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagbackRuleTest {

    private final UUID tagger = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    @Test
    void blocksTheTaggerBeforeExpiry() {
        assertTrue(TagbackRule.isProtected(tagger, tagger, 1_000L, 500L));
    }

    @Test
    void allowsTheTaggerOnceExpired() {
        assertFalse(TagbackRule.isProtected(tagger, tagger, 1_000L, 1_000L));
        assertFalse(TagbackRule.isProtected(tagger, tagger, 1_000L, 1_500L));
    }

    @Test
    void neverBlocksAnyoneElse() {
        assertFalse(TagbackRule.isProtected(someoneElse, tagger, 1_000L, 500L));
    }

    @Test
    void secondsLeftRoundsUpAndNeverSaysZero() {
        assertEquals(60, TagbackRule.secondsLeft(60_000L, 0L));
        assertEquals(42, TagbackRule.secondsLeft(60_000L, 18_001L));
        assertEquals(1, TagbackRule.secondsLeft(60_000L, 59_999L));
        assertEquals(1, TagbackRule.secondsLeft(60_000L, 60_000L));
    }

    @Test
    void noOpWhenThereWasNoLastTagger() {
        assertFalse(TagbackRule.isProtected(tagger, null, 1_000L, 500L));
    }
}

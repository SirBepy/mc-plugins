package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

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
    void noOpWhenThereWasNoLastTagger() {
        assertFalse(TagbackRule.isProtected(tagger, null, 1_000L, 500L));
    }
}

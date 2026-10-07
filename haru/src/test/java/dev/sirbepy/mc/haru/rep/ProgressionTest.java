package dev.sirbepy.mc.haru.rep;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.item.Heads;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTest extends HaruTestBase {

    @Test
    void tierUpCelebratesAndHaruTellsMoreOfHisStory() {
        OwnerState state = state();
        ctx.progression().addReputation(owner, state, 90);
        assertTrue(celebrated.isEmpty());
        ctx.progression().addReputation(owner, state, 20);
        assertEquals(1, celebrated.size());
        assertEquals(Tier.ACQUAINTANCE.ordinal(), state.announcedTier());
        String said = nextMessageContaining("keep coming back");
        assertTrue(said != null);
    }

    @Test
    void maxTrustGivesTheFinalPetalOnce() {
        OwnerState state = state();
        ctx.progression().addReputation(owner, state, Tier.SAKURA_BOUND.threshold());
        assertTrue(state.has(Progression.MILESTONE_TROPHY));
        assertEquals(50, state.petals());
        assertEquals(1, count(owner, Material.PLAYER_HEAD));
        ctx.progression().addReputation(owner, state, 10);
        assertEquals(1, count(owner, Material.PLAYER_HEAD));
    }

    @Test
    void completingTheCollectionUnlocksTheHiddenAura() {
        OwnerState state = state();
        Heads.COLLECTION.forEach(c -> state.unlocks().add("head:" + c.id()));
        ctx.progression().checkMilestones(owner, state);
        assertTrue(state.has(Progression.AURA_PETAL_STORM));
        assertEquals(100, state.petals());
        assertEquals(40, state.reputation());
    }

    @Test
    void fiftyRequestsEarnTheJournal() {
        OwnerState state = state();
        state.setQuestsCompleted(49);
        ctx.progression().checkMilestones(owner, state);
        assertFalse(state.has(Progression.MILESTONE_JOURNAL));
        state.setQuestsCompleted(50);
        ctx.progression().checkMilestones(owner, state);
        assertTrue(state.has(Progression.MILESTONE_JOURNAL));
        assertEquals(1, count(owner, Material.WRITTEN_BOOK));
    }

    private String nextMessageContaining(String text) {
        String message;
        while ((message = owner.nextMessage()) != null) {
            if (message.contains(text)) {
                return message;
            }
        }
        return null;
    }
}

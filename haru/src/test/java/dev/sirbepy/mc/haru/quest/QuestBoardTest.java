package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.rep.Tier;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBoardTest extends HaruTestBase {

    private void setBoard(OwnerState state, String... ids) {
        state.board().clear();
        for (String id : ids) {
            state.board().add(new ActiveQuest(id, 0, false));
        }
    }

    @Test
    void eachSetDrawsOneRequestPerDifficultyAndKeepsItUntilTheNextSet() {
        OwnerState state = state();
        List<String> ids = state.board().stream().map(ActiveQuest::questId).toList();
        assertEquals(3, ids.size());
        assertEquals(QuestDef.Difficulty.EASY, QuestPool.byId(ids.get(0)).difficulty());
        assertEquals(QuestDef.Difficulty.MEDIUM, QuestPool.byId(ids.get(1)).difficulty());
        assertEquals(QuestDef.Difficulty.HARD, QuestPool.byId(ids.get(2)).difficulty());
        assertFalse(ctx.board().ensureCurrentSet(state, clock.requestSet()));
        clock.hour = 11;
        assertEquals(ids, state().board().stream().map(ActiveQuest::questId).toList());

        Set<String> seen = new HashSet<>(ids);
        for (int set = 1; set <= 10; set++) {
            clock.hour = clock.hour == 11 ? 12 : 11;
            if (clock.hour == 11) {
                clock.today = clock.today.plusDays(1);
            }
            seen.addAll(state().board().stream().map(ActiveQuest::questId).toList());
        }
        assertTrue(seen.size() > 6, "new requests in new sets");
    }

    @Test
    void setsStartAtMidnightAndNoon() {
        clock.hour = 0;
        long morning = clock.requestSet();
        clock.hour = 11;
        assertEquals(morning, clock.requestSet());
        clock.hour = 12;
        assertEquals(morning + 1, clock.requestSet());
        assertEquals("2026-10-07 12:00", QuestBoard.setKey(clock.requestSet()));
        clock.hour = 23;
        assertEquals(morning + 1, clock.requestSet());
        clock.today = clock.today.plusDays(1);
        clock.hour = 0;
        assertEquals(morning + 2, clock.requestSet());
        assertEquals("2026-10-08 00:00", QuestBoard.setKey(clock.requestSet()));
    }

    @Test
    void theNextSetReplacesUnfinishedRequestsAndRestoresRerolls() {
        OwnerState state = state();
        state.board().get(0).setProgress(1);
        state.setPetals(10);
        ctx.board().reroll(state, 1, clock.requestSet());
        ctx.board().reroll(state, 1, clock.requestSet());
        assertEquals(2, state.rerollsUsed());

        clock.hour = 12;
        OwnerState next = state();
        assertEquals("2026-10-07 12:00", next.boardDate());
        assertEquals(0, next.rerollsUsed());
        assertTrue(next.board().stream().allMatch(q -> q.progress() == 0 && !q.claimed()));
    }

    @Test
    void strangersNeverDrawHigherTierRequests() {
        for (QuestDef.Difficulty difficulty : QuestDef.Difficulty.values()) {
            assertTrue(QuestBoard.options(Tier.STRANGER, difficulty, Set.of()).stream()
                    .allMatch(q -> q.minTier() == Tier.STRANGER));
        }
    }

    @Test
    void progressCountsOnlyMatchingTargetsAndStopsAtTheGoal() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        assertTrue(ctx.board().progress(state, QuestDef.Type.KILL, "SKELETON", 1).isEmpty());
        assertEquals(0, state.board().get(0).progress());
        for (int i = 0; i < 11; i++) {
            assertTrue(ctx.board().progress(state, QuestDef.Type.KILL, "HUSK", 1).isEmpty());
        }
        assertEquals(List.of(QuestPool.byId("q_zombies")), ctx.board().progress(state, QuestDef.Type.KILL, "ZOMBIE", 1));
        ctx.board().progress(state, QuestDef.Type.KILL, "ZOMBIE", 1);
        assertEquals(12, state.board().get(0).progress());
    }

    @Test
    void claimingPaysPetalsAndTrustOnce() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().get(0).setProgress(12);
        List<String> given = new ArrayList<>();

        assertEquals(QuestBoard.ClaimResult.CLAIMED, ctx.board().claim(owner, state, 0, given));
        assertEquals(4, state.petals());
        assertEquals(6, state.reputation());
        assertEquals(1, state.questsCompleted());
        assertEquals(QuestBoard.ClaimResult.ALREADY_CLAIMED, ctx.board().claim(owner, state, 0, given));
        assertEquals(QuestBoard.ClaimResult.NOT_DONE, ctx.board().claim(owner, state, 1, given));
    }

    @Test
    void deliveryTakesTheItemsWhenClaimed() {
        OwnerState state = state();
        setBoard(state, "q_cherry_logs", "q_iron", "q_diamonds");
        give(owner, Material.CHERRY_LOG, 20);
        assertEquals(QuestBoard.ClaimResult.MISSING_ITEMS, ctx.board().claim(owner, state, 0, new ArrayList<>()));
        assertEquals(20, count(owner, Material.CHERRY_LOG));
        give(owner, Material.CHERRY_LOG, 20);
        assertEquals(QuestBoard.ClaimResult.CLAIMED, ctx.board().claim(owner, state, 0, new ArrayList<>()));
        assertEquals(8, count(owner, Material.CHERRY_LOG));
    }

    @Test
    void finishingAllThreeEarnsTheSetBonus() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().forEach(q -> q.setProgress(100));
        ctx.board().claim(owner, state, 0, new ArrayList<>());
        ctx.board().claim(owner, state, 1, new ArrayList<>());
        List<String> given = new ArrayList<>();
        ctx.board().claim(owner, state, 2, given);
        assertEquals(4 + 8 + 15 + 5, state.petals());
        assertEquals(6 + 12 + 23 + 5, state.reputation());
        assertEquals(2, given.size());
    }

    @Test
    void twoFullSetsPayWhatOneDailySetUsedTo() {
        // The old daily set paid 8/16/30 petals (+2 per tier), 12/25/45 trust and a 10 + 10 bonus.
        for (Tier tier : Tier.values()) {
            int petals = 0;
            int trust = 0;
            for (QuestDef.Difficulty difficulty : QuestDef.Difficulty.values()) {
                petals += difficulty.petals(tier);
                trust += difficulty.reputation();
            }
            assertEquals(8 + 16 + 30 + 6 * tier.ordinal() + 10, 2 * (petals + QuestBoard.SET_BONUS_PETALS), tier.name());
            assertEquals(12 + 25 + 45 + 10, 2 * (trust + QuestBoard.SET_BONUS_REPUTATION));
        }
    }

    @Test
    void firstRerollIsFreeThenCostsPetalsUpToTheSetLimit() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.setPetals(100);
        assertEquals(QuestBoard.RerollResult.REROLLED, ctx.board().reroll(state, 0, clock.requestSet()));
        assertEquals(100, state.petals());
        assertFalse(state.board().get(0).questId().equals("q_zombies"));
        assertEquals(QuestDef.Difficulty.EASY, QuestPool.byId(state.board().get(0).questId()).difficulty());
        assertEquals(QuestBoard.RerollResult.REROLLED, ctx.board().reroll(state, 0, clock.requestSet()));
        assertEquals(97, state.petals());
        assertEquals(QuestBoard.RerollResult.NO_REROLLS_LEFT, ctx.board().reroll(state, 1, clock.requestSet()));
    }

    @Test
    void finishedRequestsCannotBeRerolled() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().get(0).setProgress(12);
        assertEquals(QuestBoard.RerollResult.NOT_REROLLABLE, ctx.board().reroll(state, 0, clock.requestSet()));
    }

    @Test
    void everyQuestIsWellFormed() {
        Set<String> ids = new HashSet<>();
        for (QuestDef quest : QuestPool.ALL) {
            assertTrue(ids.add(quest.id()), quest.id());
            assertTrue(quest.amount() > 0, quest.id());
            if (quest.type() == QuestDef.Type.DELIVER) {
                assertTrue(quest.deliverMaterial().isItem(), quest.id());
            }
        }
        for (QuestDef.Difficulty difficulty : QuestDef.Difficulty.values()) {
            assertTrue(QuestBoard.options(Tier.STRANGER, difficulty, Set.of()).size() >= 5, difficulty.name());
        }
    }
}

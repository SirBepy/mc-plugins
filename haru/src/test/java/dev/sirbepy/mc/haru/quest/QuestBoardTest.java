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
    void eachDayDrawsOneRequestPerDifficultyAndKeepsItAllDay() {
        OwnerState state = state();
        List<String> ids = state.board().stream().map(ActiveQuest::questId).toList();
        assertEquals(3, ids.size());
        assertEquals(QuestDef.Difficulty.EASY, QuestPool.byId(ids.get(0)).difficulty());
        assertEquals(QuestDef.Difficulty.MEDIUM, QuestPool.byId(ids.get(1)).difficulty());
        assertEquals(QuestDef.Difficulty.HARD, QuestPool.byId(ids.get(2)).difficulty());
        assertFalse(ctx.board().ensureToday(state, clock.today));
        assertEquals(ids, state().board().stream().map(ActiveQuest::questId).toList());

        Set<String> seen = new HashSet<>(ids);
        for (int day = 1; day <= 10; day++) {
            clock.today = clock.today.plusDays(1);
            seen.addAll(state().board().stream().map(ActiveQuest::questId).toList());
        }
        assertTrue(seen.size() > 6, "new requests on new days");
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
        assertEquals(8, state.petals());
        assertEquals(12, state.reputation());
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
    void finishingAllThreeEarnsTheDailyBonus() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().forEach(q -> q.setProgress(100));
        ctx.board().claim(owner, state, 0, new ArrayList<>());
        ctx.board().claim(owner, state, 1, new ArrayList<>());
        List<String> given = new ArrayList<>();
        ctx.board().claim(owner, state, 2, given);
        assertEquals(8 + 16 + 30 + QuestBoard.DAILY_BONUS_PETALS, state.petals());
        assertEquals(12 + 25 + 45 + QuestBoard.DAILY_BONUS_REPUTATION, state.reputation());
        assertEquals(2, given.size());
    }

    @Test
    void firstRerollIsFreeThenCostsPetalsUpToTheDailyLimit() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.setPetals(100);
        assertEquals(QuestBoard.RerollResult.REROLLED, ctx.board().reroll(state, 0, clock.today));
        assertEquals(100, state.petals());
        assertFalse(state.board().get(0).questId().equals("q_zombies"));
        assertEquals(QuestDef.Difficulty.EASY, QuestPool.byId(state.board().get(0).questId()).difficulty());
        assertEquals(QuestBoard.RerollResult.REROLLED, ctx.board().reroll(state, 0, clock.today));
        assertEquals(97, state.petals());
        assertEquals(QuestBoard.RerollResult.REROLLED, ctx.board().reroll(state, 1, clock.today));
        assertEquals(QuestBoard.RerollResult.NO_REROLLS_LEFT, ctx.board().reroll(state, 2, clock.today));
    }

    @Test
    void finishedRequestsCannotBeRerolled() {
        OwnerState state = state();
        setBoard(state, "q_zombies", "q_creepers", "q_diamond_ore");
        state.board().get(0).setProgress(12);
        assertEquals(QuestBoard.RerollResult.NOT_REROLLABLE, ctx.board().reroll(state, 0, clock.today));
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

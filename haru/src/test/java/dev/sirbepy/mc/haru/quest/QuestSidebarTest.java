package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link QuestSidebar}'s line text, kept as a pure function so it's testable without a live Bukkit
 * {@code Scoreboard}: {@code Score#customName} is unimplemented in MockBukkit 4.116.1 (confirmed by
 * {@code QuestHudTest}'s render-path cases turning SKIPPED, and by the same gap already skipping
 * {@code tag}'s own {@code LeaderboardEntityServiceTest} today), so anything exercised only through
 * {@code QuestSidebar#show} would never actually run under test.
 */
class QuestSidebarTest {

    private OwnerState boardOf(ActiveQuest... quests) {
        OwnerState state = new OwnerState();
        for (ActiveQuest quest : quests) {
            state.board().add(quest);
        }
        return state;
    }

    @Test
    void showsProgressForAnUnfinishedRequest() {
        OwnerState state = boardOf(new ActiveQuest("q_zombies", 5, false));
        assertEquals(List.of("Defeat 12 Zombies (5/12)"), QuestSidebar.lineTexts(state));
    }

    @Test
    void showsReadyOnceTheGoalIsReachedButNotYetClaimed() {
        OwnerState state = boardOf(new ActiveQuest("q_zombies", 12, false));
        assertEquals(List.of("Defeat 12 Zombies (Ready!)"), QuestSidebar.lineTexts(state));
    }

    @Test
    void showsDoneOnceClaimed() {
        OwnerState state = boardOf(new ActiveQuest("q_zombies", 12, true));
        assertEquals(List.of("Defeat 12 Zombies (Done)"), QuestSidebar.lineTexts(state));
    }

    @Test
    void oneLinePerActiveRequestInBoardOrder() {
        OwnerState state = boardOf(new ActiveQuest("q_zombies", 0, false), new ActiveQuest("q_creepers", 2, false));
        assertEquals(List.of("Defeat 12 Zombies (0/12)", "Defeat 6 Creepers (2/6)"), QuestSidebar.lineTexts(state));
    }

    @Test
    void anUnknownQuestIdIsSkippedRatherThanCrashing() {
        OwnerState state = boardOf(new ActiveQuest("no_such_quest", 0, false));
        assertEquals(List.of(), QuestSidebar.lineTexts(state));
    }
}

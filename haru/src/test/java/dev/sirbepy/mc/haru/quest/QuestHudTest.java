package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.OwnerState;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QuestHud}'s toggle and conflict guard. The sidebar's actual on-screen write (one line per
 * request through {@code Scoreboard.Score#customName}) can't run under MockBukkit 4.116.1 ({@code
 * ScoreMock#customName} is unimplemented, same gap already hitting {@code tag}'s
 * {@code LeaderboardEntityServiceTest}), which is exactly why {@link QuestHud#refresh} catches and
 * swallows any view failure instead of letting it undo the quest action that triggered it
 * ({@link QuestBoardTest}'s claim/progress/reroll cases would otherwise turn SKIPPED too). That line
 * text itself is covered without any Bukkit mock in {@link QuestSidebarTest}.
 */
class QuestHudTest extends HaruTestBase {

    @Test
    void togglingFlipsThePersistedFieldAndTellsThePlayer() {
        assertTrue(state().questHudEnabled(), "on by default");
        ctx.questHud().toggle(owner);
        assertFalse(state().questHudEnabled());
        ctx.questHud().toggle(owner);
        assertTrue(state().questHudEnabled());
    }

    @Test
    void aViewFailureDuringRefreshNeverPropagatesOutOfABoardAction() {
        // Drives refresh() through QuestBoard.claim()'s onChange hook; this must not throw even though
        // the scoreboard write underneath is unimplemented in MockBukkit, exactly like the production
        // guarantee it exists for (a broken client payload must never undo a completed claim).
        OwnerState state = state();
        state.board().get(0).setProgress(999);
        var given = new ArrayList<String>();
        var result = ctx.board().claim(owner, state, 0, given);
        assertTrue(result == QuestBoard.ClaimResult.CLAIMED, "the claim itself still succeeded");
        assertFalse(given.isEmpty());
    }

    @Test
    void aForeignSidebarIsNeverClobbered() {
        var foreign = org.bukkit.Bukkit.getScoreboardManager().getNewScoreboard();
        foreign.registerNewObjective("tag-board", Criteria.DUMMY, net.kyori.adventure.text.Component.text("Tag"))
                .setDisplaySlot(DisplaySlot.SIDEBAR);
        owner.setScoreboard(foreign);

        ctx.questHud().refresh(owner);
        assertTrue(owner.getScoreboard() == foreign, "Haru never takes over another plugin's sidebar");
    }
}

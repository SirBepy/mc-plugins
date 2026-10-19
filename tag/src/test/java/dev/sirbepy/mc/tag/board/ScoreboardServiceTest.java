package dev.sirbepy.mc.tag.board;

import dev.sirbepy.mc.tag.NoopNamesApi;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `/tag board` used to replace a player's whole {@link Scoreboard} unconditionally, so it would silently
 * clobber Haru's quest sidebar (which owns its own foreign objective on the slot) if that was already
 * showing. {@link ScoreboardService#enable} now shares {@code SidebarSlot#freeToUse} with Haru's own
 * guard: back off from a scoreboard it doesn't recognize instead of stealing the slot.
 */
class ScoreboardServiceTest {

    private ServerMock server;
    private ScoreboardService boards;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        boards = new ScoreboardService(new NoopNamesApi());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enableTakesOverAPlayerStillOnTheMainScoreboard() {
        boolean result = boards.enable(player);

        assertTrue(result, "no other sidebar is showing, so Tag's board must be allowed to take the slot");
        assertTrue(boards.isActive(player.getUniqueId()));
        assertEquals("tag-board", player.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getName());
    }

    @Test
    void enableBacksOffWhenAnotherPluginsSidebarAlreadyOwnsTheSlot() {
        // Stand-in for Haru's QuestSidebar: a foreign scoreboard with its own, differently-named objective.
        Scoreboard foreign = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective questObjective = foreign.registerNewObjective("haru-quest", Criteria.DUMMY, "Spirit Requests");
        questObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(foreign);

        boolean result = boards.enable(player);

        assertFalse(result, "a sidebar Tag didn't hand out must never be clobbered by /tag board");
        assertFalse(boards.isActive(player.getUniqueId()));
        assertEquals(foreign, player.getScoreboard(), "the foreign sidebar must still be showing, untouched");
    }

    @Test
    void toggleReportsBlockedWithoutChangingAnythingWhenTheSlotIsTaken() {
        Scoreboard foreign = Bukkit.getScoreboardManager().getNewScoreboard();
        foreign.registerNewObjective("haru-quest", Criteria.DUMMY, "Spirit Requests")
                .setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(foreign);

        ScoreboardService.ToggleResult result = boards.toggle(player);

        assertEquals(ScoreboardService.ToggleResult.BLOCKED, result);
        assertEquals(foreign, player.getScoreboard());
    }
}

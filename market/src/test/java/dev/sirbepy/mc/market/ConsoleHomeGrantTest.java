package dev.sirbepy.mc.market;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real {@link HomeShop.HomeGrant}: the exact LuckPerms console commands it dispatches to raise
 * (and tidy up after) a player's {@code huskhomes.max_homes} node. A fake {@code lp} command stands
 * in for LuckPerms itself, since the Java API has no test-friendly seam here (Joe, 2026-10-09: no
 * new dependency for this - dispatch the same commands Joe would type by hand).
 */
class ConsoleHomeGrantTest {

    private ServerMock server;
    private final List<String> dispatched = new ArrayList<>();

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void withLuckPerms() {
        server = MockBukkit.mock();
        server.getCommandMap().register("test", new Command("lp") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                dispatched.add(label + " " + String.join(" ", args));
                return true;
            }
        });
    }

    @Test
    void grantingTheFirstTierSetsItAlone() {
        withLuckPerms();
        UUID id = UUID.randomUUID();
        assertTrue(new ConsoleHomeGrant(server).grant(id, 4));
        assertEquals(List.of("lp user " + id + " permission set huskhomes.max_homes.4 true"), dispatched);
    }

    @Test
    void grantingTheNextTierAlsoUnsetsTheOneItReplaces() {
        withLuckPerms();
        UUID id = UUID.randomUUID();
        assertTrue(new ConsoleHomeGrant(server).grant(id, 5));
        assertEquals(List.of(
                "lp user " + id + " permission set huskhomes.max_homes.5 true",
                "lp user " + id + " permission unset huskhomes.max_homes.4"), dispatched);
    }

    @Test
    void neverUnsetsTheDefaultGroupsOwnBaseNode() {
        withLuckPerms();
        // next=4 means next-1=3, the default group's own floor: never touched by this shop.
        new ConsoleHomeGrant(server).grant(UUID.randomUUID(), 4);
        assertTrue(dispatched.stream().noneMatch(c -> c.contains("unset")));
    }

    @Test
    void noLuckPermsMeansTheDispatchFails() {
        server = MockBukkit.mock();
        assertFalse(new ConsoleHomeGrant(server).grant(UUID.randomUUID(), 4));
    }
}

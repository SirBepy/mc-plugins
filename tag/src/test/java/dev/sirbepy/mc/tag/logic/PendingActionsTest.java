package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingActionsTest {

    @Test
    void takeReturnsAndRemovesThePendingAction() {
        UUID player = UUID.randomUUID();
        Map<UUID, PendingAction> pending = new HashMap<>();
        pending.put(player, PendingAction.BECOME_IT);

        Optional<PendingAction> result = PendingActions.take(pending, player);

        assertEquals(Optional.of(PendingAction.BECOME_IT), result);
        assertFalse(pending.containsKey(player));
    }

    @Test
    void takeIsEmptyWhenNothingIsPending() {
        Map<UUID, PendingAction> pending = new HashMap<>();
        assertTrue(PendingActions.take(pending, UUID.randomUUID()).isEmpty());
    }

    @Test
    void takeDoesNotDisturbOtherPlayersEntries() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, PendingAction> pending = new HashMap<>();
        pending.put(a, PendingAction.BECOME_IT);
        pending.put(b, PendingAction.CLEAR_IT);

        PendingActions.take(pending, a);

        assertEquals(PendingAction.CLEAR_IT, pending.get(b));
        assertEquals(1, pending.size());
    }
}

package dev.sirbepy.mc.safechests.request;

import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestManagerTest {

    private final AtomicReference<Instant> clock = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final RequestManager requests = new RequestManager(clock::get);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void validateSucceedsOnlyForTheRealOwner() {
        UUID requester = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID impostor = UUID.randomUUID();
        AccessRequest request = requests.create(requester, owner, "world", 1, 2, 3, Duration.ofMinutes(2));

        assertTrue(requests.validate(request.id(), owner).isPresent(), "the real owner must be able to respond");
        assertFalse(requests.validate(request.id(), impostor).isPresent(), "an impostor must never be able to respond");
        assertFalse(requests.validate("not-a-real-id", owner).isPresent(), "an unknown id must never validate");
    }

    @Test
    void idsAreUniqueAndUnguessable() {
        AccessRequest first = requests.create(UUID.randomUUID(), UUID.randomUUID(), "world", 0, 0, 0, Duration.ofMinutes(2));
        AccessRequest second = requests.create(UUID.randomUUID(), UUID.randomUUID(), "world", 0, 0, 0, Duration.ofMinutes(2));

        assertFalse(first.id().equals(second.id()), "two requests must never share an id");
        // A random UUID (122 bits of entropy) is not something a player could plausibly type or brute force.
        assertTrue(first.id().length() >= 32);
    }

    @Test
    void requestExpiresAndIsSweptExactlyOnce() {
        UUID owner = UUID.randomUUID();
        requests.create(UUID.randomUUID(), owner, "world", 0, 0, 0, Duration.ofMinutes(2));

        clock.set(clock.get().plus(Duration.ofMinutes(1)));
        assertEquals(0, requests.sweepExpired().size(), "must not be swept before expiry");

        clock.set(clock.get().plus(Duration.ofMinutes(2)));
        assertEquals(1, requests.sweepExpired().size(), "must be swept once past its deadline");
        assertEquals(0, requests.sweepExpired().size(), "a swept request must not be reported twice");
    }

    @Test
    void validateAlsoEvictsAnExpiredRequestSoItCanNeverBeAnsweredLate() {
        UUID owner = UUID.randomUUID();
        AccessRequest request = requests.create(UUID.randomUUID(), owner, "world", 0, 0, 0, Duration.ofMinutes(2));

        clock.set(clock.get().plus(Duration.ofMinutes(1)));
        assertTrue(requests.validate(request.id(), owner).isPresent(), "must still be valid before its expiry");

        clock.set(clock.get().plus(Duration.ofMinutes(2)));
        assertFalse(requests.validate(request.id(), owner).isPresent(), "must be expired past its deadline");
        assertEquals(0, requests.sweepExpired().size(), "validate() must have already evicted it, nothing left to sweep");
    }

    @Test
    void denialDebounceBlocksRepeatNotificationsWithinTheWindow() {
        UUID player = UUID.randomUUID();
        Duration debounce = Duration.ofSeconds(3);

        assertTrue(requests.shouldNotifyDenial(player, debounce), "the first denial must always notify");
        assertFalse(requests.shouldNotifyDenial(player, debounce), "a second denial inside the window must be suppressed");

        clock.set(clock.get().plus(Duration.ofSeconds(4)));
        assertTrue(requests.shouldNotifyDenial(player, debounce), "a denial after the window must notify again");
    }

    @Test
    void pendingForOwnerFiltersByOwnerExcludesExpiredAndOrdersOldestFirst() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        AccessRequest first = requests.create(UUID.randomUUID(), owner, "world", 0, 0, 0, Duration.ofMinutes(2));
        requests.create(UUID.randomUUID(), otherOwner, "world", 1, 0, 0, Duration.ofMinutes(2));
        AccessRequest second = requests.create(UUID.randomUUID(), owner, "world", 2, 0, 0, Duration.ofMinutes(2));
        requests.create(UUID.randomUUID(), owner, "world", 3, 0, 0, Duration.ofSeconds(30));

        clock.set(clock.get().plus(Duration.ofSeconds(31)));

        assertEquals(List.of(first, second), requests.pendingForOwner(owner),
                "must exclude the other owner's request and the expired one, oldest first");
    }

    @Test
    void openFormFlagIsPerOwnerAndClearedByForgetPlayer() {
        UUID owner = UUID.randomUUID();
        assertFalse(requests.hasOpenForm(owner), "no form is open until one is marked open");

        requests.markFormOpen(owner);
        assertTrue(requests.hasOpenForm(owner));

        requests.forgetPlayer(owner);
        assertFalse(requests.hasOpenForm(owner), "quitting must clear a stale open-form flag too");
    }

    @Test
    void forgetPlayerClearsBothTheDenialDebounceAndTheLastDeniedBlock() {
        ServerMock server = MockBukkit.getMock();
        Block block = server.addSimpleWorld("world").getBlockAt(0, 64, 0);
        UUID player = UUID.randomUUID();
        requests.shouldNotifyDenial(player, Duration.ofSeconds(3));
        requests.rememberDeniedBlock(player, block);
        assertTrue(requests.lastDeniedBlock(player).isPresent(), "sanity check: the block must be remembered first");

        requests.forgetPlayer(player);

        assertTrue(requests.shouldNotifyDenial(player, Duration.ofSeconds(3)),
                "forgetting the player must clear the debounce, or a rejoin would stay silently suppressed");
        assertFalse(requests.lastDeniedBlock(player).isPresent(),
                "forgetting the player must drop the last denied block too, or memory never shrinks");
    }
}

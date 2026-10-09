package dev.sirbepy.mc.core.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PDC round trip the market's Wanted board and the cartel's trophies both rely on: one shared
 * key, so a head stamped by either plugin reads back the same kill time (Joe, 2026-10-09). */
class KillStampTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack head() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(UUID.randomUUID(), "Victim")));
        return head;
    }

    @Test
    void aStampedHeadRoundTripsItsKillTime() {
        ItemStack head = head();
        Instant at = Instant.parse("2026-10-09T12:00:00Z");

        KillStamp.stamp(head, at);

        assertEquals(at, KillStamp.killedAt(head).orElseThrow());
    }

    @Test
    void anUnstampedHeadHasNoKillTime() {
        assertTrue(KillStamp.killedAt(head()).isEmpty());
    }
}

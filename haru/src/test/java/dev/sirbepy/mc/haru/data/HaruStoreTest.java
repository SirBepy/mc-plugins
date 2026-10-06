package dev.sirbepy.mc.haru.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaruStoreTest {

    @TempDir
    Path dir;

    @Test
    void everythingSurvivesASaveAndReload() {
        File file = new File(dir.toFile(), "data.yml");
        UUID owner = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        HaruStore store = new HaruStore(file, Logger.getLogger("test"));
        store.setAnchor(new NpcAnchor("world", 10.5, 70, -3.5, 90f));
        store.setNpcId(npc);
        OwnerState state = store.state(owner);
        state.setPetals(42);
        state.setReputation(160);
        state.setQuestsCompleted(7);
        state.setBoardDate("2026-10-07");
        state.board().add(new ActiveQuest("q_zombies", 5, false));
        state.board().add(new ActiveQuest("q_iron", 24, true));
        state.recordPurchase("ev_torches", "mcday:4");
        state.recordPurchase("ev_torches", "mcday:4");
        state.unlocks().add("aura:sakura");
        state.setActiveAura("aura:sakura");
        state.blessings().put("haste", new OwnerState.Blessing("haste", 1, 123456789L));
        state.charms().put("ward", 987654321L);
        state.setClosedUntil(555L);
        store.save();

        HaruStore reloaded = new HaruStore(file, Logger.getLogger("test"));
        OwnerState back = reloaded.state(owner);
        assertEquals(new NpcAnchor("world", 10.5, 70, -3.5, 90f), reloaded.anchor());
        assertEquals(npc, reloaded.npcId());
        assertNull(reloaded.titleId());
        assertEquals(42, back.petals());
        assertEquals(160, back.reputation());
        assertEquals(7, back.questsCompleted());
        assertEquals("2026-10-07", back.boardDate());
        assertEquals(2, back.board().size());
        assertEquals(5, back.board().get(0).progress());
        assertTrue(back.board().get(1).claimed());
        assertEquals(2, back.bought("ev_torches", "mcday:4"));
        assertEquals(0, back.bought("ev_torches", "mcday:5"));
        assertTrue(back.has("aura:sakura"));
        assertEquals("aura:sakura", back.activeAura());
        assertEquals(123456789L, back.blessings().get("haste").expiryMillis());
        assertEquals(987654321L, back.charms().get("ward"));
        assertEquals(555L, back.closedUntil());
    }

    @Test
    void flushOnlyWritesWhenSomethingChanged() {
        File file = new File(dir.toFile(), "data.yml");
        HaruStore store = new HaruStore(file, Logger.getLogger("test"));
        store.flush();
        assertTrue(!file.exists());
        store.state(UUID.randomUUID()).setPetals(3);
        store.markDirty();
        store.flush();
        assertTrue(file.exists());
    }
}

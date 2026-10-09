package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelStore;
import dev.sirbepy.mc.cartel.CartelTestBase;
import dev.sirbepy.mc.core.item.KillStamp;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophiesTest extends CartelTestBase {

    private static ItemStack headItem(PlayerMock player, String name) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(item -> item != null && item.getType() == Material.PLAYER_HEAD
                        && name.equals(((SkullMeta) item.getItemMeta()).getPlayerProfile().getName()))
                .findFirst().orElseThrow();
    }

    @Test
    void anOnlineOwnerGetsTheHeadRightAway() {
        trophies.deliver(owner.getUniqueId(), new CartelStore.PendingHead(victim.getUniqueId(), "Victim"));

        assertEquals(1, heads(owner, "Victim"));
        ItemStack head = Arrays.stream(owner.getInventory().getContents())
                .filter(item -> item != null && item.getType() == Material.PLAYER_HEAD).findFirst().orElseThrow();
        assertEquals(victim.getUniqueId(), ((SkullMeta) head.getItemMeta()).getPlayerProfile().getId());
        assertTrue(chat(owner).contains("Leonel and Marco bring you Victim's head."));
    }

    @Test
    void anOfflineOwnerGetsTheHeadOnTheirNextJoin() {
        owner.disconnect();

        trophies.deliver(owner.getUniqueId(), new CartelStore.PendingHead(victim.getUniqueId(), "Victim"));
        assertEquals(1, store.takePendingHeads(owner.getUniqueId()).size(), "kept for later");
        store.addPendingHead(owner.getUniqueId(), new CartelStore.PendingHead(victim.getUniqueId(), "Victim"));

        owner.reconnect();
        trophies.deliverPending(owner);

        assertEquals(1, heads(owner, "Victim"));
        assertTrue(store.takePendingHeads(owner.getUniqueId()).isEmpty(), "delivered once");
    }

    @Test
    void anOnlineOwnerGetsTheHeadStampedWithTheHitsKillTime() {
        Instant killedAt = Instant.parse("2026-10-09T12:00:00Z");

        trophies.deliver(owner.getUniqueId(), new CartelStore.PendingHead(victim.getUniqueId(), "Victim", killedAt));

        assertEquals(killedAt, KillStamp.killedAt(headItem(owner, "Victim")).orElseThrow());
    }

    @Test
    void aDelayedDeliveryStampsTheOriginalKillTimeNotTheMomentItIsHandedOver() {
        owner.disconnect();
        Instant killedAt = Instant.parse("2026-10-09T12:00:00Z");
        trophies.deliver(owner.getUniqueId(), new CartelStore.PendingHead(victim.getUniqueId(), "Victim", killedAt));

        owner.reconnect();
        trophies.deliverPending(owner);

        assertEquals(killedAt, KillStamp.killedAt(headItem(owner, "Victim")).orElseThrow());
    }

    @Test
    void aPendingEntrySavedWithNoKillTimeArrivesUnstamped() {
        owner.disconnect();
        // The pre-stamp 2-arg PendingHead, same as an entry saved by a jar before this feature.
        store.addPendingHead(owner.getUniqueId(), new CartelStore.PendingHead(victim.getUniqueId(), "Victim"));

        owner.reconnect();
        trophies.deliverPending(owner);

        assertTrue(KillStamp.killedAt(headItem(owner, "Victim")).isEmpty());
    }
}

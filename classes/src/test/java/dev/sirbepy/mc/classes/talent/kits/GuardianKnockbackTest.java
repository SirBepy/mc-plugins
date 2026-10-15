package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steady, Stone Skin, Immovable and Stalwart Aura: knockback from mobs only, never from players. */
class GuardianKnockbackTest extends KitTestBase {

    private EntityPushedByEntityAttackEvent push(PlayerMock victim, Entity by) {
        EntityPushedByEntityAttackEvent event = new EntityPushedByEntityAttackEvent(victim,
                EntityKnockbackEvent.Cause.ENTITY_ATTACK, by, new Vector(1, 0.4, 0));
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void immovableCancelsAMobsKnockbackWhileSneakingButNeverAPlayers() {
        PlayerMock g = player(RpgClass.GUARDIAN, 25);
        rank(g, "immovable", 1);
        g.setSneaking(true);
        Zombie z = zombie(2);
        assertTrue(push(g, z).isCancelled());
        PlayerMock other = server.addPlayer();
        assertFalse(push(g, other).isCancelled(), "PvP knockback is untouched");
        g.setSneaking(false);
        assertFalse(push(g, z).isCancelled());
    }

    @Test
    void steadyHalvesAMobsKnockbackWhileBlockingAtRankTwo() {
        PlayerMock g = player(RpgClass.GUARDIAN, 25);
        rank(g, "steady", 2);
        g.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        g.setBlocking(true);
        EntityPushedByEntityAttackEvent event = push(g, zombie(2));
        assertFalse(event.isCancelled());
        assertEquals(0.5, event.getKnockback().getX(), 1e-9);
    }

    @Test
    void stoneSkinStopsKnockbackDuringLastStandWhileBlocking() {
        PlayerMock g = player(RpgClass.GUARDIAN, 25);
        rank(g, "stone_skin", 1);
        g.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        g.setBlocking(true);
        assertFalse(push(g, zombie(2)).isCancelled(), "no Last Stand yet");
        assertTrue(cast(g, Slot.ONE));
        assertTrue(push(g, zombie(2)).isCancelled());
    }

    @Test
    void stalwartAuraCutsAnAllysKnockbackByAQuarterAtRankOne() {
        PlayerMock g = player(RpgClass.GUARDIAN, 25);
        rank(g, "stalwart_aura", 1);
        g.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        EntityPushedByEntityAttackEvent event = push(ally, zombie(3));
        assertEquals(0.75, event.getKnockback().getX(), 1e-9);
    }
}

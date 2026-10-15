package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Engineer branch's turret: placement, one-at-a-time, arrow safety and kill-credited Charge. */
class ArtificerSentryTest extends KitTestBase {

    @Test
    void castingSentryPlacesOneTurretAndStartsItsCooldown() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.TWO));
        assertTrue(engine.cooldowns().leftMillis(p.getUniqueId(), Slot.TWO) > 0);
    }

    @Test
    void castingSentryTwiceReplacesTheFirstOne() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.TWO));
        long before = world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).size();
        assertTrue(cast(p, Slot.TWO));
        long after = world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).size();
        assertEquals(before, after, "the old sentry should be gone, not stacked with the new one");
    }

    @Test
    void sentryFiresAtAHostileMobWithinRange() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        p.teleport(at(0));
        Zombie z = zombie(3);
        assertTrue(cast(p, Slot.TWO));
        int before = world.getEntitiesByClass(Arrow.class).size();
        advance(21); // the sentry's first tick, one second in
        int after = world.getEntitiesByClass(Arrow.class).size();
        assertTrue(after > before, "the sentry should have fired an arrow at the zombie");
    }

    @Test
    void multishotFiresExtraArrowsPerShot() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "multishot", 2);
        p.teleport(at(0));
        zombie(3);
        assertTrue(cast(p, Slot.TWO));
        advance(21);
        assertEquals(3, world.getEntitiesByClass(Arrow.class).size());
    }

    @Test
    void sentryArrowNeverHurtsAPlayer() {
        PlayerMock owner = player(RpgClass.ARTIFICER, 10);
        PlayerMock victim = server.addPlayer();
        owner.teleport(at(0));
        zombie(3);
        assertTrue(cast(owner, Slot.TWO));
        advance(21);
        Arrow arrow = world.getEntitiesByClass(Arrow.class).stream().findFirst().orElseThrow();
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(arrow, victim, DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).build(), 4);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
    }

    @Test
    void sentryKillGivesFiveCharge() {
        PlayerMock owner = player(RpgClass.ARTIFICER, 10);
        owner.teleport(at(0));
        assertTrue(cast(owner, Slot.TWO));
        Zombie z = zombie(3);
        Arrow arrow = world.spawn(z.getLocation(), Arrow.class, a -> a.setShooter(owner));
        // Tag it the way the sentry does, without waiting a full real tick cycle for the fire itself.
        var key = new org.bukkit.NamespacedKey(plugin, "artificer_sentry_arrow");
        arrow.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, owner.getUniqueId().toString());
        EntityDamageByEntityEvent fatal = new EntityDamageByEntityEvent(arrow, z, DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).build(), 100);
        z.setLastDamageCause(fatal);
        z.setHealth(0); // MockBukkit fires EntityDeathEvent itself once health reaches 0.
        assertEquals(5, resource(owner));
    }

    @Test
    void calibratedIncreasesSentryArrowDamage() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "calibrated", 3);
        p.teleport(at(0));
        zombie(3);
        assertTrue(cast(p, Slot.TWO));
        advance(21);
        Arrow arrow = world.getEntitiesByClass(Arrow.class).stream().findFirst().orElseThrow();
        // Base 3 (1.5 hearts) +30% at rank 3.
        assertEquals(3.9, arrow.getDamage(), 0.001);
    }

    @Test
    void offClassPlayerCannotCastSentry() {
        PlayerMock p = player(RpgClass.WARRIOR, 10);
        p.teleport(at(0));
        // A Warrior's slot 2 is Charge: whatever it does, no sentry appears.
        engine.slots().cast(p, Slot.TWO);
        assertTrue(engine.summons().of(p.getUniqueId()).isEmpty());
    }
}

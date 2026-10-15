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
    void rangeFinderExtendsTheSentrysFiringRange() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "range_finder", 2); // base 10 + 6 = 16 blocks
        p.teleport(at(0));
        zombie(14); // outside the base range, inside rank 2's extended range
        assertTrue(cast(p, Slot.TWO));
        int before = world.getEntitiesByClass(Arrow.class).size();
        advance(21);
        int after = world.getEntitiesByClass(Arrow.class).size();
        assertTrue(after > before, "rank 2's +6 blocks should reach a mob 14 blocks out");
    }

    @Test
    void reinforcedExtendsHowLongTheSentryLasts() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "reinforced", 2); // +10 s on top of the base 20 s
        p.teleport(at(0));
        assertTrue(cast(p, Slot.TWO));
        advance(500); // past the base 400-tick duration
        assertFalse(world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).isEmpty(),
                "rank 2 extends the sentry to 600 ticks, so it should still be up at 500");
        advance(110); // past the extended 600-tick duration
        assertTrue(world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class).isEmpty(),
                "gone after the extended duration");
    }

    @Test
    void teslaCoilChainsAZapToNearbyMobs() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "tesla_coil", 2); // 1 heart, chaining to 2 more mobs
        p.teleport(at(0));
        Zombie primary = zombie(3);
        org.bukkit.Location chainSpot = new org.bukkit.Location(world, 4, 64, 0);
        Zombie chainA = world.spawn(chainSpot, Zombie.class);
        Zombie chainB = world.spawn(new org.bukkit.Location(world, 5, 64, 0), Zombie.class);
        assertTrue(cast(p, Slot.TWO));
        advance(61); // the third 1-second tick (second=3) fires Tesla Coil
        // The chain is sorted by distance from the primary and includes the primary itself (distance
        // 0), so rank 2's 2 extra zaps land on the primary again, then the closest other mob.
        assertEquals(16, primary.getHealth(), 0.01, "1 heart from the zap, 1 more from the chain hitting itself first");
        assertEquals(18, chainA.getHealth(), 0.01, "the second chain slot, the closest other mob");
        assertEquals(20, chainB.getHealth(), 0.01, "rank 2 only chains to 2 more mobs");
    }

    @Test
    void frostRoundsSlowsAMobTheArrowLands() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "frost_rounds", 2); // 2 s
        p.teleport(at(0));
        // Close enough to the sentry's own spot that the (stationary, in MockBukkit) arrow is
        // within 2 blocks of it when the delayed slow check runs.
        Zombie z = zombie(1.5);
        assertTrue(cast(p, Slot.TWO));
        advance(30); // the first shot at tick 20, then 10 more ticks for the delayed slow check
        assertEquals(2 * 20, z.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS).getDuration(), "rank 2 is 2 s");
    }

    @Test
    void repairDroneHealsTheOwnerWhileNearby() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "repair_drone", 2); // 1 heart every 5 s
        p.teleport(at(0));
        p.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        advance(101); // the fifth 1-second tick (second=5)
        assertEquals(12, p.getHealth(), 0.01, "rank 2 heals 1 heart (2 hp) every 5 s while within 6 blocks");
    }

    @Test
    void mortarLobsAShellThatSplashesToNearbyMobs() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "mortar", 2); // 3 hearts
        p.teleport(at(0));
        Zombie target = zombie(3);
        Zombie splash = world.spawn(new org.bukkit.Location(world, 4, 64, 0), Zombie.class);
        assertTrue(cast(p, Slot.TWO));
        advance(101); // the fifth 1-second tick (second=5)
        assertEquals(14, target.getHealth(), 0.01, "rank 2 is 3 hearts (6 hp)");
        assertEquals(14, splash.getHealth(), 0.01, "splashes to mobs within 2 blocks of the target too");
    }

    @Test
    void rapidDeployShortensTheSentrysCooldown() {
        PlayerMock p = player(RpgClass.ARTIFICER, 10);
        rank(p, "rapid_deploy", 1);
        assertEquals(45, engine.cooldowns().adjust(p, Slot.TWO, 60), "15 s shorter");
        assertEquals(30, engine.cooldowns().adjust(p, Slot.TWO, 40), "never under the 30 s floor");
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

package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One case per Venom node (docs/classes-revamp.md, Rogue). */
class RogueVenomTest extends KitTestBase {

    private void kill(Zombie dead, PlayerMock killer) {
        dead.setKiller(killer);
        server.getPluginManager().callEvent(new EntityDeathEvent(dead,
                DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>(), 5));
    }

    @Test
    void poisonBladeProcsAtItsRankedChance() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "poison_blade", 1);
        Zombie z = zombie(1);
        roll = 0.05; // under rank 1's 10% chance.
        melee(p, z);
        assertTrue(z.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void poisonBladeMissesAboveItsChance() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "poison_blade", 1);
        Zombie z = zombie(1);
        roll = 0.5; // above rank 1's 10% chance.
        melee(p, z);
        assertFalse(z.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void toxicologistShortensThenBlocksPoisonOnTheRogue() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "toxicologist", 1);
        var event1 = new org.bukkit.event.entity.EntityPotionEffectEvent(p, null,
                new PotionEffect(PotionEffectType.POISON, 100, 0), null,
                org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN,
                org.bukkit.event.entity.EntityPotionEffectEvent.Action.ADDED, true);
        server.getPluginManager().callEvent(event1);
        assertTrue(p.hasPotionEffect(PotionEffectType.POISON));
        assertEquals(50, p.getPotionEffect(PotionEffectType.POISON).getDuration(), "rank 1: half the duration");

        p.removePotionEffect(PotionEffectType.POISON);
        rank(p, "toxicologist", 2);
        var event2 = new org.bukkit.event.entity.EntityPotionEffectEvent(p, null,
                new PotionEffect(PotionEffectType.POISON, 100, 0), null,
                org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN,
                org.bukkit.event.entity.EntityPotionEffectEvent.Action.ADDED, true);
        server.getPluginManager().callEvent(event2);
        assertFalse(p.hasPotionEffect(PotionEffectType.POISON), "rank 2: no poison at all");
    }

    @Test
    void envenomBoostsDamageToPoisonedMobs() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "envenom", 2);
        Zombie z = zombie(1);
        z.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        assertEquals(10 * 1.20, melee(p, z), 1e-9);
    }

    @Test
    void weakeningToxinCutsAPoisonedMobsDamage() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "weakening_toxin", 2);
        Zombie z = zombie(1);
        z.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        assertEquals(10 * 0.80, hit(z, p, DamageCause.ENTITY_ATTACK, 10), 1e-9);
    }

    @Test
    void witherEdgeReplacesPoisonWithWitherOnUndead() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "poison_blade", 3);
        rank(p, "wither_edge", 1);
        Zombie z = zombie(1); // undead: immune to poison, gets Wither instead.
        roll = 0;
        melee(p, z);
        assertTrue(z.hasPotionEffect(PotionEffectType.WITHER));
        assertFalse(z.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void potentRaisesPoisonToLevelTwoAndRankTwoExtendsIt() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "poison_blade", 1);
        rank(p, "potent", 2);
        Zombie z = zombie(1);
        roll = 0;
        melee(p, z);
        PotionEffect poison = z.getPotionEffect(PotionEffectType.POISON);
        assertEquals(1, poison.getAmplifier(), "Potent: level II");
        assertEquals(100, poison.getDuration(), "rank 2: base 60 ticks + 40 more");
    }

    @Test
    void contagionSpreadsAPoisonedMobsPoisonOnDeath() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "contagion", 2);
        Zombie dead = zombie(0);
        dead.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        Zombie near1 = world.spawn(dead.getLocation().clone().add(1, 0, 0), Zombie.class);
        Zombie near2 = world.spawn(dead.getLocation().clone().add(0, 0, 1), Zombie.class);
        kill(dead, p);
        assertTrue(near1.hasPotionEffect(PotionEffectType.POISON));
        assertTrue(near2.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void antidoteHealsOnKillingAPoisonedMob() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "antidote", 1);
        p.setHealth(10);
        Zombie z = zombie(1);
        z.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        kill(z, p);
        assertEquals(12, p.getHealth());
    }

    @Test
    void toxicCloudPoisonsMobsInsideSmokeBombEachSecond() {
        PlayerMock p = player(RpgClass.ROGUE, 10);
        rank(p, "toxic_cloud", 2);
        p.teleport(at(0));
        Zombie z = zombie(3);
        assertTrue(cast(p, Slot.TWO));
        advance(20);
        assertTrue(z.hasPotionEffect(PotionEffectType.POISON), "the first second of the cloud poisons mobs inside it");
    }

    @Test
    void plagueSpreadsDeathMarkToNearbyMobsAtHalfStrength() {
        PlayerMock p = player(RpgClass.ROGUE, 25);
        rank(p, "plague", 1);
        Location loc = at(0);
        loc.setDirection(new Vector(1, 0, 0));
        p.teleport(loc);
        Zombie main = zombie(2);
        Zombie near = world.spawn(main.getLocation().clone().add(1, 0, 0), Zombie.class);
        assertTrue(cast(p, Slot.ULTIMATE));
        advance(80);
        assertEquals(20 - 0.30 * 20, main.getHealth(), 0.001);
        assertEquals(20 - 0.15 * 20, near.getHealth(), 0.001, "Plague: half strength on the nearby mob");
    }
}

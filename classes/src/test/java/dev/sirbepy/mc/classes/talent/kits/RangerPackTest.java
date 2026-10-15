package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffectType;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Beastmaster branch's 11 nodes and the Spirit Bear keystone. */
class RangerPackTest extends KitTestBase {

    private PlayerMock ranger(int level) {
        return player(RpgClass.RANGER, level);
    }

    private Wolf tamedWolf(PlayerMock owner, double x) {
        Wolf wolf = world.spawn(at(x), Wolf.class);
        wolf.setTamed(true);
        wolf.setOwner(owner);
        return wolf;
    }

    @Test
    void packBondBoostsAWolfsDamageToMobsOnly() {
        PlayerMock p = ranger(25);
        rank(p, "pack_bond", 2); // +20%
        Wolf wolf = tamedWolf(p, 0);
        Zombie z = zombie(0);
        double dealt = hit(wolf, z, DamageCause.ENTITY_ATTACK, 10);
        assertEquals(12, dealt, 0.001);
    }

    @Test
    void thickFurReducesMobDamageToAPlayersWolf() {
        PlayerMock p = ranger(25);
        rank(p, "thick_fur", 2); // -30%
        Wolf wolf = tamedWolf(p, 0);
        Zombie z = zombie(0);
        double taken = hit(z, wolf, DamageCause.ENTITY_ATTACK, 10);
        assertEquals(7, taken, 0.001);
    }

    @Test
    void callOfTheWildSummonsTemporaryWolvesFromFleetFoot() {
        PlayerMock p = ranger(1);
        rank(p, "call_of_the_wild", 2);
        p.teleport(at(0));
        int before = world.getEntitiesByClass(Wolf.class).size();
        assertTrue(cast(p, Slot.ONE));
        assertEquals(before + 2, world.getEntitiesByClass(Wolf.class).size());
    }

    @Test
    void wildGrowthMakesTemporaryWolvesLastLonger() {
        // The "20% bigger" half is untestable here: MockBukkit's Wolf has no Attribute.SCALE
        // instance (getAttribute returns null), which is exactly why the production code guards
        // that call; see the kit's report for this under Untestable in MockBukkit.
        PlayerMock p = ranger(1);
        rank(p, "call_of_the_wild", 1);
        rank(p, "wild_growth", 2); // 25 s instead of the base 15 s
        p.teleport(at(0));
        assertTrue(cast(p, Slot.ONE));
        Wolf wolf = world.getEntitiesByClass(Wolf.class).iterator().next();
        advance(24 * 20);
        assertTrue(wolf.isValid(), "still alive at 24 s of 25");
        advance(2 * 20);
        assertFalse(wolf.isValid());
    }

    @Test
    void fetchGivesFocusForAWolfsKill() {
        PlayerMock p = ranger(25);
        rank(p, "fetch", 2); // 5 Focus
        Wolf wolf = tamedWolf(p, 0);
        Zombie z = zombie(0);
        EntityDamageByEntityEvent cause = new EntityDamageByEntityEvent(wolf, z, DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).build(), 20);
        z.setLastDamageCause(cause);
        double before = resource(p);
        server.getPluginManager().callEvent(new EntityDeathEvent(z, cause.getDamageSource(), new ArrayList<>()));
        assertEquals(before + 5, resource(p), 0.01);
    }

    @Test
    void mendPetHealsYourNearestWolfWhenYouHeal() {
        PlayerMock p = ranger(25);
        rank(p, "mend_pet", 2); // 2 hearts = 4 health
        p.teleport(at(0));
        Wolf wolf = tamedWolf(p, 1);
        wolf.setHealth(2); // a wolf's max health is 8; stay well under it so the heal isn't clamped
        server.getPluginManager().callEvent(new EntityRegainHealthEvent(p, 2, EntityRegainHealthEvent.RegainReason.REGEN));
        assertEquals(6, wolf.getHealth(), 0.01);
    }

    @Test
    void alphaHowlGivesWolvesStrengthWhenVolleyFires() {
        PlayerMock p = ranger(10);
        rank(p, "alpha_howl", 1);
        p.teleport(at(0));
        Wolf wolf = tamedWolf(p, 1);
        assertTrue(cast(p, Slot.TWO));
        assertTrue(wolf.hasPotionEffect(PotionEffectType.STRENGTH));
    }

    @Test
    void feralMakesWolfBitesSlowMobs() {
        PlayerMock p = ranger(25);
        rank(p, "feral", 2); // 3 s
        Wolf wolf = tamedWolf(p, 0);
        Zombie z = zombie(0);
        hit(wolf, z, DamageCause.ENTITY_ATTACK, 10);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(60, z.getPotionEffect(PotionEffectType.SLOWNESS).getDuration());
    }

    @Test
    void packTacticsBonusesArrowDamageOnAMobYourWolfIsAttacking() {
        PlayerMock p = ranger(25);
        rank(p, "pack_tactics", 2); // +10%
        Wolf wolf = tamedWolf(p, 0);
        Zombie target = zombie(0);
        wolf.setTarget(target);
        org.bukkit.entity.Arrow arrow = world.spawn(at(5), org.bukkit.entity.Arrow.class);
        arrow.setShooter(p);
        double dealt = hit(arrow, target, DamageCause.PROJECTILE, 10);
        assertEquals(10 * 1.10 * 1.10, dealt, 0.001, "Steady Aim's +10% and Pack Tactics' +10%");
    }

    @Test
    void loyalSavesAWolfFromALethalHitOncePerSixtySeconds() {
        PlayerMock p = ranger(25);
        rank(p, "loyal", 1);
        p.teleport(at(0));
        Wolf wolf = tamedWolf(p, 10);
        wolf.setHealth(1);
        double taken = hit(zombie(10), wolf, DamageCause.ENTITY_ATTACK, 10);
        assertEquals(0, taken, "the hit was cancelled outright");
        assertEquals(4, wolf.getHealth(), 0.01);
        assertEquals(p.getLocation().getBlockX(), wolf.getLocation().getBlockX());
    }

    @Test
    void spiritBearSummonsAPolarBearThatHuntsTheNearestHostileMob() {
        PlayerMock p = ranger(25);
        engine.talents().force(p.getUniqueId(), RpgClass.RANGER, "spirit_bear", 1);
        p.teleport(at(0));
        Zombie z = zombie(3);
        assertTrue(cast(p, Slot.TWO));
        PolarBear bear = world.getEntitiesByClass(PolarBear.class).iterator().next();
        advance(21);
        assertEquals(z, bear.getTarget());
    }

    @Test
    void nothingTriggersForAPlayerWhoIsNotAnActiveRanger() {
        PlayerMock p = ranger(25);
        rank(p, "pack_bond", 2);
        classes.leave(p.getUniqueId());
        Wolf wolf = tamedWolf(p, 0);
        Zombie z = zombie(0);
        assertEquals(10, hit(wolf, z, DamageCause.ENTITY_ATTACK, 10), 0.001);
    }
}

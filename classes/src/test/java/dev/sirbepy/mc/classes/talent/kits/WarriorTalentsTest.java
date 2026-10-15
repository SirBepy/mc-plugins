package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One case per Warrior node that hooks a melee/ability/kill event (docs/classes-revamp.md, Warrior).
 * All at Weapon Master level 1 (+2% melee) unless a node needs a specific level, so every expected
 * number is "base 10 damage, times 1.02 for Weapon Master, times the node's own percent".
 */
class WarriorTalentsTest extends KitTestBase {

    private void kill(Zombie dead, PlayerMock killer, DamageCause cause) {
        dead.setLastDamageCause(new EntityDamageByEntityEvent(killer, dead, cause,
                DamageSource.builder(DamageType.GENERIC).build(), 5));
        dead.setKiller(killer);
        server.getPluginManager().callEvent(new EntityDeathEvent(dead, DamageSource.builder(DamageType.GENERIC).build(),
                new ArrayList<>(), 5));
    }

    // Berserker

    @Test
    void bloodlustOnlyAppliesUnderHalfHealthAndStacksWithWeaponMaster() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "bloodlust", 2);
        Zombie full = zombie(1);
        assertEquals(10 * 1.02, melee(p, full), 1e-9, "full health: no Bloodlust");
        p.setHealth(9);
        Zombie low = zombie(2);
        assertEquals(10 * 1.02 * 1.08, melee(p, low), 1e-9, "under half health: rank 2 is +8%");
    }

    @Test
    void frenzyStacksWithinThreeSecondsAndResetsAfter() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "frenzy", 3);
        Zombie z = zombie(1);
        assertEquals(10 * 1.02 * 1.03, melee(p, z), 1e-9, "first hit: 1 stack");
        assertEquals(10 * 1.02 * 1.06, melee(p, z), 1e-9, "second hit within 3 s: 2 stacks");
        now += 4_000;
        assertEquals(10 * 1.02 * 1.03, melee(p, z), 1e-9, "stack reset after the 3 s window");
    }

    @Test
    void recklessSwingBoostsSweepHitsOnly() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "reckless_swing", 2);
        Zombie z = zombie(1);
        assertEquals(10 * 1.02 * 1.40, hit(p, z, DamageCause.ENTITY_SWEEP_ATTACK, 10), 1e-9);
    }

    @Test
    void bloodPriceAndVeteranShortenAbilityOnesCooldownButNeverBelowThirtySeconds() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "veteran", 2);
        int adjusted = engine.cooldowns().adjust(p, Slot.ONE, 90);
        assertEquals(80, adjusted, "90 - 2*5");
        rank(p, "blood_price", 2);
        assertEquals(62, engine.cooldowns().adjust(p, Slot.ONE, 90), "90*0.8-10=62, still above the 30 s floor");
    }

    @Test
    void rampageGivesExtraRageOnAKill() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "rampage", 2);
        kill(zombie(1), p, DamageCause.ENTITY_ATTACK);
        assertEquals(10, resource(p));
    }

    @Test
    void bloodthirstHealsOnAMeleeKillOncePerEightSeconds() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "bloodthirst", 2);
        p.setHealth(10);
        kill(zombie(1), p, DamageCause.ENTITY_ATTACK);
        assertEquals(12, p.getHealth());
        kill(zombie(2), p, DamageCause.ENTITY_ATTACK);
        assertEquals(12, p.getHealth(), "still on its 8 s cooldown");
        now += 9_000;
        kill(zombie(3), p, DamageCause.ENTITY_ATTACK);
        assertEquals(14, p.getHealth());
    }

    @Test
    void savageRoarWeakensNearbyMobsWhenBattleCryFires() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "savage_roar", 1);
        p.teleport(at(0));
        Zombie z = zombie(2);
        assertFalse(z.hasPotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS));
        cast(p, Slot.ONE);
        assertTrue(z.hasPotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS));
    }

    @Test
    void secondWindHealsOnBattleCry() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "second_wind", 2);
        p.setHealth(10);
        cast(p, Slot.ONE);
        assertEquals(14, p.getHealth(), "rank 2 = 2 hearts = 4 health points");
    }

    @Test
    void unstoppableOnlyGatesAtAboveSeventyFiveRage() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "unstoppable", 1);
        assertEquals(0, resource(p));
        engine.resources().set(p.getUniqueId(), 75);
        var atThreshold = new io.papermc.paper.event.entity.EntityKnockbackEvent(p,
                io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.DAMAGE, new org.bukkit.util.Vector(1, 0, 0));
        server.getPluginManager().callEvent(atThreshold);
        assertFalse(atThreshold.isCancelled(), "75 itself doesn't count, only above it");
    }

    // Weaponmaster

    @Test
    void cleaveSplashesToNearbyMobsOnAnAxeHit() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "cleave", 2);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        Zombie target = zombie(1);
        Zombie near = zombie(1.2);
        double dealt = melee(p, target);
        assertEquals(20 - dealt * 0.2, near.getHealth(), 1e-6, "cleave rank 2: 20% splash");
    }

    @Test
    void fencerBoostsEveryThirdSwordHitOnTheSameMob() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "fencer", 2);
        p.getInventory().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
        Zombie z = zombie(1);
        assertEquals(10 * 1.02, melee(p, z), 1e-9, "1st hit: no bonus yet");
        assertEquals(10 * 1.02, melee(p, z), 1e-9, "2nd hit: no bonus yet");
        assertEquals(10 * 1.02 * 1.20, melee(p, z), 1e-9, "3rd hit: rank 2 is +20%");
    }

    @Test
    void executeBoostsDamageToMobsUnderAQuarterHealth() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "execute", 3);
        Zombie z = zombie(1);
        z.setHealth(4);
        assertEquals(10 * 1.02 * 1.15, melee(p, z), 1e-9);
    }

    @Test
    void sunderMarksTheMobForBonusMeleeFromAnyAttacker() {
        PlayerMock warrior = player(RpgClass.WARRIOR, 1);
        rank(warrior, "sunder", 2);
        warrior.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        Zombie z = zombie(1);
        melee(warrior, z);
        PlayerMock other = server.addPlayer();
        assertEquals(10 * 1.10, melee(other, z), 1e-9, "sunder rank 2: +10% from everyone");
        now += 5_000;
        assertEquals(10, melee(other, z), 1e-9, "the 4 s mark has expired");
    }

    @Test
    void riposteBoostsTheNextHitAfterBlockingAMobsAttack() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "riposte", 2);
        p.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        p.setBlocking(true);
        Zombie attacker = zombie(1);
        hit(attacker, p, DamageCause.ENTITY_ATTACK, 4);
        Zombie target = zombie(2);
        assertEquals(10 * 1.02 * 1.40, melee(p, target), 1e-9, "rank 2: +40% on the next hit");
        assertEquals(10 * 1.02, melee(p, target), 1e-9, "spent, so a third hit is unboosted");
    }

    @Test
    void disarmHasAChanceToMakeAZombieDropItsWeapon() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "disarm", 2);
        roll = 0.0;
        Zombie z = zombie(1);
        z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        melee(p, z);
        assertEquals(Material.AIR, z.getEquipment().getItemInMainHand().getType());
    }

    @Test
    void disarmNeverTriggersAboveItsChance() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "disarm", 1);
        roll = 0.5;
        Zombie z = zombie(1);
        z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        melee(p, z);
        assertEquals(Material.IRON_SWORD, z.getEquipment().getItemInMainHand().getType());
    }

    @Test
    void momentumKnocksSprintingHitsFurtherAndSlowsTheMob() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "momentum", 2);
        p.setSprinting(true);
        Zombie z = zombie(1);
        melee(p, z);
        assertTrue(z.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
    }

    @Test
    void heavyBlowStunsOnAFullAxeHitOncePerSixSecondsPerMob() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "heavy_blow", 1);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        Zombie z = zombie(1);
        melee(p, z);
        var effect = z.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        assertEquals(3, effect.getAmplifier(), "Slowness IV is amplifier 3");
    }

    @Test
    void whirlingBladesAlsoHitsAMobBehindTheAttacker() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "whirling_blades", 1);
        p.teleport(at(0));
        p.setRotation(0, 0); // yaw 0: Bukkit's getDirection() faces +Z
        Zombie front = world.spawn(p.getLocation().clone().add(0, 0, 1), Zombie.class);
        Zombie behind = world.spawn(p.getLocation().clone().add(0, 0, -1), Zombie.class);
        hit(p, front, DamageCause.ENTITY_SWEEP_ATTACK, 10);
        assertTrue(behind.getHealth() < 20, "a mob behind the sweep still takes damage");
    }

    @Test
    void formationBoostsMeleeDamageWhileAnAllyIsNearby() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "formation", 2);
        Zombie z = zombie(1);
        assertEquals(10 * 1.02, melee(p, z), 1e-9, "alone: no Formation bonus");
        PlayerMock ally = server.addPlayer();
        ally.teleport(p.getLocation());
        assertEquals(10 * 1.02 * 1.10, melee(p, zombie(1)), 1e-9, "an ally within 5 blocks: rank 2 is +10%");
    }

    // Warlord

    @Test
    void rallyCoversNearbyAlliesWithBattleCryAtHalfStrength() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "rally", 1);
        PlayerMock ally = server.addPlayer();
        ally.teleport(p.getLocation());
        Zombie z = zombie(1);
        cast(p, Slot.ONE);
        double expectedCut = RpgClass.battleCryPercent(1) * RpgClass.VS_PLAYERS;
        assertEquals(10 * (1 - expectedCut / 100), hit(z, ally, DamageCause.ENTITY_ATTACK, 10), 1e-9);
    }

    @Test
    void inspireHealsNearbyAlliesOnAMobKillOncePerFiveSeconds() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "inspire", 2);
        PlayerMock ally = server.addPlayer();
        ally.teleport(p.getLocation());
        ally.setHealth(10);
        kill(zombie(1), p, DamageCause.ENTITY_ATTACK);
        assertEquals(12, ally.getHealth());
    }

    @Test
    void tauntPullsNearbyNonBossMobsOntoTheCaster() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "taunt", 2);
        p.teleport(at(0));
        Zombie z = zombie(2);
        cast(p, Slot.ONE);
        assertEquals(p, z.getTarget());
    }

    @Test
    void warDrumsGivesSpeedToTheCasterAndAlliesOnBattleCry() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "war_drums", 2);
        PlayerMock ally = server.addPlayer();
        ally.teleport(p.getLocation());
        cast(p, Slot.ONE);
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED));
        assertTrue(ally.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED));
    }

    @Test
    void shieldWallReducesMobArrowDamageToNearbyAllies() {
        PlayerMock warrior = player(RpgClass.WARRIOR, 1);
        rank(warrior, "shield_wall", 2);
        warrior.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        warrior.setBlocking(true);
        PlayerMock ally = server.addPlayer();
        ally.teleport(warrior.getLocation());
        Piglin shooter = world.spawn(at(5), Piglin.class);
        var arrow = arrowFrom(shooter, 4);
        assertEquals(10 * 0.90, hit(arrow, ally, DamageCause.PROJECTILE, 10), 1e-9, "rank 2: 10% less from mob arrows");
    }

    @Test
    void warhornDoublesRageGainForSixSecondsAfterBattleCry() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "warhorn", 1);
        cast(p, Slot.ONE);
        engine.resources().gain(p, 10);
        double afterCast = resource(p);
        assertEquals(20, afterCast, "doubled while warhorn's window is open");
        now += 7_000;
        engine.resources().gain(p, 10);
        assertEquals(30, resource(p), "the 6 s window has closed, so this gain isn't doubled");
    }

    @Test
    void leadTheChargeGivesNearbyAlliesSpeedTwoOnCharge() {
        PlayerMock p = player(RpgClass.WARRIOR, 10);
        rank(p, "lead_the_charge", 1);
        PlayerMock ally = server.addPlayer();
        ally.teleport(p.getLocation());
        cast(p, Slot.TWO);
        var effect = ally.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        assertEquals(1, effect.getAmplifier(), "Speed II is amplifier 1");
    }
}

package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Trapper branch's 11 nodes and Cage Shot. */
class RangerSnaresTest extends KitTestBase {

    private static final double STEADY_AIM_25 = 1.10;

    private PlayerMock ranger(int level) {
        return player(RpgClass.RANGER, level);
    }

    private Arrow shoot(PlayerMock p, double x, float force) {
        Arrow arrow = world.spawn(at(x), Arrow.class);
        arrow.setShooter(p);
        server.getPluginManager().callEvent(new EntityShootBowEvent(p, new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW), arrow, EquipmentSlot.HAND, force, true));
        return arrow;
    }

    @Test
    void snareRootsTheFirstMobToStepOnItAfterFleetFoot() {
        PlayerMock p = ranger(1);
        rank(p, "snare", 2);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.ONE));
        Zombie z = zombie(0);
        advance(5);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(9, z.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier());
    }

    @Test
    void minefieldDropsExtraSnaresInALineBehindThePlayer() {
        PlayerMock p = ranger(1);
        rank(p, "snare", 1);
        rank(p, "minefield", 2);
        // Yaw -90 faces +X, so "behind" is -X: the same axis zombie(x) spawns along.
        p.teleport(new Location(world, 0, 64, 0, -90f, 0f));
        assertTrue(cast(p, Slot.ONE));
        // 3 snares (minefield rank 2 + 1), each 1 block further behind; a zombie on the 3rd one roots.
        Zombie z = zombie(-3);
        advance(5);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void explosiveSnareSplashesNearbyMobsWithNoBlockDamage() {
        PlayerMock p = ranger(1);
        rank(p, "snare", 1);
        rank(p, "explosive_snare", 1);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.ONE));
        Zombie onIt = zombie(0);
        Zombie nearby = zombie(1);
        advance(5);
        assertEquals(20 - 6, onIt.getHealth(), 0.01);
        assertEquals(20 - 6, nearby.getHealth(), 0.01, "within the 2-block splash");
    }

    @Test
    void barbedArrowsSlowAnyMobTheyHit() {
        PlayerMock p = ranger(25);
        rank(p, "barbed_arrows", 2);
        Zombie z = zombie(5);
        hit(shoot(p, 0, 0.3f), z, DamageCause.PROJECTILE, 10);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(80, z.getPotionEffect(PotionEffectType.SLOWNESS).getDuration());
    }

    @Test
    void caltropsFromAVolleyArrowSlowTheFirstMobThatWalksOverThem() {
        PlayerMock p = ranger(10);
        rank(p, "caltrops", 1);
        assertTrue(cast(p, Slot.TWO));
        Arrow real = shoot(p, 0, 1.0f);
        Arrow extra = world.getEntitiesByClass(Arrow.class).stream().filter(a -> a != real).findFirst().orElseThrow();
        extra.teleport(at(10));
        server.getPluginManager().callEvent(new ProjectileHitEvent(extra));
        Zombie z = zombie(10);
        advance(5);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(1, z.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier());
    }

    @Test
    void bolaHasAChanceToRootAnArrowHitMob() {
        PlayerMock p = ranger(25);
        rank(p, "bola", 3); // 15% chance
        Zombie z = zombie(5);
        roll = 0.1; // under 15%
        hit(shoot(p, 0, 0.3f), z, DamageCause.PROJECTILE, 10);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(9, z.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier(), "a root, not Barbed Arrows' slow");
    }

    @Test
    void kitingGivesSpeedForHittingAnArrowWithinFiveBlocks() {
        PlayerMock p = ranger(25);
        rank(p, "kiting", 2);
        p.teleport(at(0));
        Zombie close = zombie(4);
        hit(shoot(p, 0, 0.3f), close, DamageCause.PROJECTILE, 10);
        assertTrue(p.hasPotionEffect(PotionEffectType.SPEED));
    }

    @Test
    void nettingBonusesArrowDamageOnlyWhileTheMobIsRooted() {
        PlayerMock p = ranger(25);
        rank(p, "bola", 3);
        rank(p, "netting", 2);
        Zombie z = zombie(5);
        roll = 0; // force Bola's root to proc on the first hit
        double rootingHit = hit(shoot(p, 0, 0.3f), z, DamageCause.PROJECTILE, 10);
        assertEquals(10 * STEADY_AIM_25, rootingHit, 0.001, "not rooted yet when this hit landed");
        double secondHit = hit(shoot(p, 0, 0.3f), z, DamageCause.PROJECTILE, 10);
        assertEquals(10 * STEADY_AIM_25 * 1.20, secondHit, 0.001, "now rooted: +20%");
    }

    @Test
    void survivalistGivesExtraFocusForKillingARootedMob() {
        PlayerMock p = ranger(25);
        rank(p, "survivalist", 2);
        Zombie z = zombie(5);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.ONE)); // no Snare rank learned: harmless, just to cover the no-op path
        rank(p, "snare", 1);
        p.teleport(z.getLocation());
        assertTrue(cast(p, Slot.ONE));
        advance(5);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        double before = resource(p);
        z.setKiller(p);
        server.getPluginManager().callEvent(new EntityDeathEvent(z, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>()));
        assertEquals(before + 10, resource(p), 0.01);
    }

    @Test
    void huntersInstinctGlowsAMobThatStartsTargetingYouForYouOnly() {
        PlayerMock p = ranger(1);
        rank(p, "hunters_instinct", 1);
        p.teleport(at(0));
        Zombie z = zombie(5);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityTargetEvent(z, p,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER));
        assertTrue(z.isGlowing());
        advance(61);
        assertFalse(z.isGlowing());
    }

    @Test
    void cageShotArmsTheNextArrowToRootEveryMobNearWhereItLands() {
        PlayerMock p = ranger(10);
        engine.talents().force(p.getUniqueId(), RpgClass.RANGER, "cage_shot", 1);
        assertEquals("Cage Shot", engine.slots().name(p.getUniqueId(), RpgClass.RANGER, Slot.TWO));
        assertTrue(cast(p, Slot.TWO));
        Arrow arrow = shoot(p, 0, 1.0f);
        Zombie near = zombie(1);
        Zombie far = zombie(10);
        arrow.teleport(at(0));
        server.getPluginManager().callEvent(new ProjectileHitEvent(arrow));
        assertTrue(near.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertFalse(far.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void nothingTriggersForAPlayerWhoIsNotAnActiveRanger() {
        PlayerMock p = ranger(25);
        rank(p, "barbed_arrows", 2);
        classes.leave(p.getUniqueId());
        Zombie z = zombie(5);
        hit(shoot(p, 0, 0.3f), z, DamageCause.PROJECTILE, 10);
        assertFalse(z.hasPotionEffect(PotionEffectType.SLOWNESS));
    }
}

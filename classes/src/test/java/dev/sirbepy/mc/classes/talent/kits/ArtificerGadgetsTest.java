package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Tinker branch, Jump Pad (Engineer) and the Golem/Grav Gun keystones. */
class ArtificerGadgetsTest extends KitTestBase {

    @Test
    void armourKitRepairsEveryArmourPiece() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "armour_kit", 1);
        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        var meta = (Damageable) helmet.getItemMeta();
        meta.setDamage(100);
        helmet.setItemMeta((ItemMeta) meta);
        p.getInventory().setHelmet(helmet);
        assertTrue(cast(p, Slot.ONE));
        var after = (Damageable) p.getInventory().getHelmet().getItemMeta();
        // Rank 1: 2% of 165 max durability, rounded, = 3 less damage.
        assertEquals(97, after.getDamage());
    }

    @Test
    void shockAbsorberCutsFallDamageWhileHoldingATool() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "shock_absorber", 2);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        EntityDamageEvent event = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 10);
        server.getPluginManager().callEvent(event);
        assertEquals(5, event.getDamage());
    }

    @Test
    void offClassPlayerKeepsFullFallDamage() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        EntityDamageEvent event = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 10);
        server.getPluginManager().callEvent(event);
        assertEquals(10, event.getDamage());
    }

    @Test
    void rocketJumpGrantsFallImmunityForTheNextFall() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "rocket_jump", 1);
        assertTrue(cast(p, Slot.ONE));
        EntityDamageEvent event = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 10);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void lastChanceSavesAToolAtOneDurabilityOncePerCooldown() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "last_chance", 2);
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        var meta = (Damageable) pick.getItemMeta();
        meta.setDamage(pick.getType().getMaxDurability() - 1);
        pick.setItemMeta((ItemMeta) meta);
        PlayerItemDamageEvent event = new PlayerItemDamageEvent(p, pick, 1);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        var after = (Damageable) pick.getItemMeta();
        assertEquals(pick.getType().getMaxDurability() - 1, after.getDamage());
    }

    @Test
    void salvageCanDropAMaterialWhenAToolBreaks() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "salvage", 2);
        roll = 0;
        PlayerItemBreakEvent event = new PlayerItemBreakEvent(p, new ItemStack(Material.IRON_PICKAXE));
        int before = world.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        server.getPluginManager().callEvent(event);
        int after = world.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        assertTrue(after > before);
    }

    @Test
    void flashbangBlindsNearbyHostileMobsOnOverclock() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "flashbang", 1);
        p.teleport(at(0));
        Zombie z = zombie(2);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(z.hasPotionEffect(PotionEffectType.BLINDNESS));
    }

    @Test
    void jumpPadLaunchesAPlayerWhoWalksOntoIt() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "jump_pad", 1);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.ONE));
        PlayerMoveEvent move = new PlayerMoveEvent(p, at(0), at(0));
        server.getPluginManager().callEvent(move);
        assertEquals(1.3, p.getVelocity().getY(), 0.001);
    }

    @Test
    void powerCellsIncreasesChargeGain() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "power_cells", 2);
        engine.resources().gain(p, 10);
        assertEquals(15, resource(p));
    }

    @Test
    void grapplePullsThePlayerTowardTheHookWithinRange() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "grapple", 2);
        p.teleport(at(0));
        var hook = world.spawn(at(10), org.bukkit.entity.FishHook.class);
        var event = new org.bukkit.event.player.PlayerFishEvent(p, hook, hook,
                org.bukkit.event.player.PlayerFishEvent.State.IN_GROUND);
        server.getPluginManager().callEvent(event);
        assertTrue(p.getVelocity().getX() > 0, "the player should be pulled toward the hook");
    }

    @Test
    void golemCastSpawnsAnIronGolem() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        p.teleport(at(0));
        int before = world.getEntitiesByClass(org.bukkit.entity.IronGolem.class).size();
        var golemTalent = kit(RpgClass.ARTIFICER).talents().stream().filter(t -> t.id().equals("golem")).findFirst().orElseThrow();
        assertTrue(kit(RpgClass.ARTIFICER).keystoneAbility(golemTalent).cast(p));
        int after = world.getEntitiesByClass(org.bukkit.entity.IronGolem.class).size();
        assertEquals(before + 1, after);
    }

    @Test
    void gravGunThrowsTheMobLookedAt() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        Location facingEast = at(0);
        facingEast.setDirection(new org.bukkit.util.Vector(1, 0, 0));
        p.teleport(facingEast);
        Zombie z = zombie(3);
        var gravGunTalent = kit(RpgClass.ARTIFICER).talents().stream().filter(t -> t.id().equals("grav_gun")).findFirst().orElseThrow();
        boolean cast = kit(RpgClass.ARTIFICER).keystoneAbility(gravGunTalent).cast(p);
        assertTrue(cast);
        assertTrue(z.getVelocity().getZ() > 0 || z.getVelocity().getY() > 0, "the zombie should have been thrown");
    }
}

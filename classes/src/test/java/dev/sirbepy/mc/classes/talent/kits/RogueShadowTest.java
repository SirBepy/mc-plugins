package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One case per Shadow node that isn't Double Jump or Wall Climb (those are {@link RogueMovementTest}). */
class RogueShadowTest extends KitTestBase {

    @Test
    void lightFeetCancelsFallDamageBelowItsThreshold() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "light_feet", 3);
        p.setFallDistance(7);
        EntityDamageEvent under = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, 5);
        server.getPluginManager().callEvent(under);
        assertTrue(under.isCancelled(), "rank 3: falls under 8 blocks are free");

        p.setFallDistance(8);
        EntityDamageEvent over = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, 5);
        server.getPluginManager().callEvent(over);
        assertFalse(over.isCancelled());
    }

    @Test
    void quietStepsStopsDistantMobsFromNoticingASneakingRogue() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "quiet_steps", 2);
        p.setSneaking(true);
        p.teleport(at(0));
        Zombie z = zombie(6); // 6 blocks away: rank 2's threshold is 5.
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(z, p, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled(), "6 blocks is further than rank 2's 5-block notice range");
    }

    @Test
    void quietStepsAllowsNoticingWithinItsRange() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "quiet_steps", 1);
        p.setSneaking(true);
        p.teleport(at(0));
        Zombie z = zombie(3); // within rank 1's 8-block range.
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(z, p, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void smokeScreenExtendsSmokeBombsReach() {
        PlayerMock p = player(RpgClass.ROGUE, 10);
        rank(p, "smoke_screen", 3);
        p.teleport(at(0));
        Zombie z = zombie(10); // within rank 3's 12-block reach, outside the base 6.
        z.setTarget(p);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(null, z.getTarget(), "rank 3 reaches 12 blocks");
    }

    @Test
    void evasionDodgesAMobsMeleeHitAtItsChance() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "evasion", 2);
        Zombie z = zombie(1);
        roll = 0.05; // under rank 2's 10% chance.
        assertEquals(0, hit(z, p, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5), "dodged completely");
        roll = 0.5;
        assertEquals(5, hit(z, p, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5), "chance missed: hit lands");
    }

    @Test
    void decoyDrawsNearbyMobsAwayFromTheRogue() {
        PlayerMock p = player(RpgClass.ROGUE, 10);
        rank(p, "decoy", 2);
        p.teleport(at(0));
        Zombie z = zombie(2);
        long before = world.getEntitiesByClass(ArmorStand.class).size();
        assertTrue(cast(p, Slot.TWO));
        long after = world.getEntitiesByClass(ArmorStand.class).size();
        assertTrue(after > before, "Decoy spawns a copy of the Rogue");
        assertTrue(((Mob) z).getTarget() instanceof ArmorStand, "the nearby zombie targets the decoy");
    }

    @Test
    void nightStalkerBoostsDamageInDarkness() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "night_stalker", 2);
        Zombie z = zombie(1);
        BlockMock block = (BlockMock) z.getLocation().getBlock();
        block.setLightFromSky((byte) 0);
        block.setLightFromBlocks((byte) 0);
        assertEquals(10 * 1.10, melee(p, z), 1e-9, "rank 2: +10% at light level 0");
        block.setLightFromSky((byte) 15);
        assertEquals(10, melee(p, z), 1e-9, "no bonus in full light");
    }

    @Test
    void shadowDanceSkipsTheCooldownOnceThenGatesTheSecondCast() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        rank(p, "shadow_dance", 1);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(0, classes.cooldownLeftMillis(p.getUniqueId()), "the spare charge skips the cooldown");

        assertTrue(cast(p, Slot.ONE));
        assertTrue(classes.cooldownLeftMillis(p.getUniqueId()) > 0, "the charge is spent until it recharges");
    }

    @Test
    void ghostHidesEquipmentDuringSmokeBombWithoutThrowing() {
        PlayerMock p = player(RpgClass.ROGUE, 10);
        rank(p, "ghost", 1);
        p.teleport(at(0));
        server.addPlayer().teleport(at(1));
        assertTrue(cast(p, Slot.TWO));
        advance(80);
    }
}

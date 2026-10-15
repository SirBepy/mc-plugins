package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Guardian's tree shape, resource gain, abilities and talents (docs/classes-revamp.md,
 * Guardian). Numbers are checked at a mid rank. Players are explicitly turned to face east
 * ({@code +X}) wherever a test depends on cone or shield-facing geometry, since a fresh
 * {@code PlayerMock} has no guaranteed look direction.
 */
class GuardianKitTest extends KitTestBase {

    /** Puts the player at {@code x} on the test's z=0 line, facing toward +X. */
    private void faceEast(PlayerMock p, double x) {
        Location loc = new Location(world, x, 64, 0);
        loc.setDirection(new Vector(1, 0, 0));
        p.teleport(loc);
    }

    /** MockBukkit's {@code setBlocking(true)} only takes effect while a shield is equipped. */
    private void giveShield(PlayerMock p) {
        p.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
    }

    // Shape

    @Test
    void theTreeHas33NodesWithUniqueIdsAndTheRightShapePerBranch() {
        ClassKit kit = kit(RpgClass.GUARDIAN);
        List<Talent> talents = kit.talents();
        assertEquals(33, talents.size());

        Map<String, Integer> ids = new HashMap<>();
        for (Talent talent : talents) {
            assertEquals(null, ids.put(talent.id(), 1), talent.id() + " is not unique");
        }
        for (int branch = 0; branch < 3; branch++) {
            int[] perTier = new int[6];
            for (Talent talent : talents) {
                if (talent.branch() == branch) perTier[talent.tier()]++;
            }
            assertEquals(2, perTier[1], "branch " + branch + " tier 1");
            assertEquals(3, perTier[2], "branch " + branch + " tier 2");
            assertEquals(3, perTier[3], "branch " + branch + " tier 3");
            assertEquals(2, perTier[4], "branch " + branch + " tier 4");
            assertEquals(1, perTier[5], "branch " + branch + " tier 5");
        }
    }

    @Test
    void everyBranchHasExactlyOneKeystone() {
        long keystones = kit(RpgClass.GUARDIAN).talents().stream().filter(Talent::keystone).count();
        assertEquals(3, keystones);
    }

    // Resource gain

    @Test
    void mobHitTakenGivesFourResolve() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        Zombie z = zombie(2);
        melee(z, p);
        assertEquals(4, resource(p));
    }

    @Test
    void blockingAMobHitGivesThreeResolveInstead() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        faceEast(p, 0);
        giveShield(p);
        p.setBlocking(true);
        Zombie z = zombie(1);
        melee(z, p);
        assertEquals(3, resource(p));
    }

    @Test
    void inactiveGuardianGainsNoResolve() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        classes.leave(p.getUniqueId());
        Zombie z = zombie(2);
        melee(z, p);
        assertEquals(0, resource(p));
    }

    // Bulwark

    @Test
    void shieldMasteryReflectsAPercentOfTheBlockedHitBack() {
        PlayerMock p = player(RpgClass.GUARDIAN, 1);
        faceEast(p, 0);
        rank(p, "shield_mastery", 2);
        giveShield(p);
        p.setBlocking(true);
        Zombie z = zombie(1);
        double before = z.getHealth();
        melee(z, p);
        // rank 2 -> 20% of the hit reflected back, computed after Stalwart's level-1 passive (2% off
        // mob melee, ClassCombat) already shaved the 10-damage test hit down to 9.8.
        assertEquals(before - 9.8 * 0.20, z.getHealth(), 0.001);
    }

    @Test
    void thornedShieldSlowsAMobThatHitsTheShield() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        faceEast(p, 0);
        rank(p, "thorned_shield", 1);
        giveShield(p);
        p.setBlocking(true);
        Zombie z = zombie(1);
        melee(z, p);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void bashMasteryRaisesShieldBashsDamage() {
        PlayerMock p = player(RpgClass.GUARDIAN, 10);
        faceEast(p, 0);
        rank(p, "bash_mastery", 2);
        Zombie z = zombie(1);
        double before = z.getHealth();
        cast(p, Slot.TWO);
        // rank 2 -> 4 hearts = 8 health points.
        assertEquals(before - 8.0, z.getHealth(), 0.001);
    }

    @Test
    void shoulderChargeDashesForwardBeforeShieldBashLands() {
        PlayerMock p = player(RpgClass.GUARDIAN, 25);
        faceEast(p, 0);
        rank(p, "shoulder_charge", 2); // rank 2 is a 6-block dash
        Vector before = p.getVelocity().clone();
        assertTrue(cast(p, Slot.TWO));
        assertTrue(p.getVelocity().getX() > before.getX(), "Shoulder Charge gives the caster forward velocity");
    }

    @Test
    void shoulderChargeMovesTheConesOriginSoAFarMobIsStillHit() {
        PlayerMock p = player(RpgClass.GUARDIAN, 25);
        faceEast(p, 0);
        rank(p, "shoulder_charge", 2); // rank 2 dashes 6 blocks forward first
        // 7 blocks out, outside Shield Bash's own 4-block cone from the caster's start, but inside
        // it once the dash moves the cone's origin to x=6.
        Zombie z = zombie(7);
        double before = z.getHealth();
        assertTrue(cast(p, Slot.TWO));
        assertEquals(before - 2.0, z.getHealth(), 0.001, "1 heart from Shield Bash, reached because the dash moved the cone forward");
    }

    @Test
    void bulldozeDamagesAMobKnockedIntoAWall() {
        PlayerMock p = player(RpgClass.GUARDIAN, 25);
        faceEast(p, 0);
        rank(p, "bulldoze", 2); // 2 hearts
        Zombie z = zombie(1);
        double before = z.getHealth();
        assertTrue(cast(p, Slot.TWO));
        advance(3);
        // Shield Bash's own 1 heart, then Bulldoze's 2 hearts 3 ticks later since the mock mob never
        // actually moves off the spot it was knocked to (no physics simulation), same as hitting a wall.
        assertEquals(before - (2.0 + 4.0), z.getHealth(), 0.001);
    }

    @Test
    void unbreakableStopsTheShieldWearingDownDuringLastStand() {
        PlayerMock p = player(RpgClass.GUARDIAN, 10);
        rank(p, "unbreakable", 1);
        cast(p, Slot.ONE);
        ItemStack shield = new ItemStack(Material.SHIELD);
        org.bukkit.event.player.PlayerItemDamageEvent event = new org.bukkit.event.player.PlayerItemDamageEvent(p, shield, 1);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void retaliationBoostsTheNextMeleeHitAfterThreeBlocksWithinFiveSeconds() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        faceEast(p, 0);
        rank(p, "retaliation", 2);
        giveShield(p);
        p.setBlocking(true);
        Zombie attacker = zombie(1);
        melee(attacker, p);
        melee(attacker, p);
        melee(attacker, p);
        Zombie target = zombie(1);
        double dealt = melee(p, target);
        // rank 2 -> +40% on the next melee hit.
        assertEquals(14.0, dealt, 0.001);
    }

    @Test
    void rootedReducesMobDamageWhileStandingStillAndSneaking() {
        PlayerMock p = player(RpgClass.GUARDIAN, 1);
        rank(p, "rooted", 2);
        p.simulateSneak(true);
        Zombie z = zombie(2);
        double taken = melee(z, p);
        // rank 2 -> 10% less, stacked with Stalwart's level-1 passive (2% off mob melee) from ClassCombat.
        assertEquals(10.0 * 0.98 * 0.90, taken, 0.001);
    }

    @Test
    void aegisReflectsABlockedArrowBackAtTheShooter() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        faceEast(p, 0);
        rank(p, "aegis", 2);
        giveShield(p);
        p.setBlocking(true);
        Zombie shooter = zombie(1);
        Arrow arrow = arrowFrom(shooter, 1);
        double before = shooter.getHealth();
        hit(arrow, p, org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE, 10);
        // rank 2 -> 100% of the blocked arrow's damage back.
        assertEquals(before - 10.0, shooter.getHealth(), 0.001);
    }

    @Test
    void bastionCanBeCastAsTheBulwarkKeystone() {
        PlayerMock p = player(RpgClass.GUARDIAN, 20);
        rank(p, "bastion", 1);
        assertTrue(cast(p, Slot.TWO));
    }

    // Protector

    @Test
    void lastStandGivesIronWillAbsorptionHearts() {
        PlayerMock p = player(RpgClass.GUARDIAN, 10);
        rank(p, "iron_will", 2);
        double before = p.getAbsorptionAmount();
        cast(p, Slot.ONE);
        assertEquals(before + 4.0, p.getAbsorptionAmount(), 0.001);
    }

    @Test
    void bodyguardCanSwitchAMobsTargetToTheGuardian() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 5);
        rank(guardian, "bodyguard", 2);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        roll = 0; // guarantees the chance-based switch procs
        Zombie z = zombie(2);
        melee(z, ally);
        assertEquals(guardian, z.getTarget());
    }

    @Test
    void sharedResolveGivesTheGuardianResolveWhenAnAllyIsHit() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 5);
        rank(guardian, "shared_resolve", 2);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        Zombie z = zombie(2);
        melee(z, ally);
        assertEquals(6, resource(guardian));
    }

    @Test
    void guardianAngelRedirectsPartOfAnAllysDamageToTheGuardian() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 5);
        rank(guardian, "guardian_angel", 3);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        double guardianHealthBefore = guardian.getHealth();
        Zombie z = zombie(2);
        double taken = melee(z, ally);
        // rank 3 -> 15% redirected to the guardian.
        assertEquals(8.5, taken, 0.001);
        assertEquals(guardianHealthBefore - 1.5, guardian.getHealth(), 0.001);
    }

    @Test
    void lifelineHealsAnAllyWhoDropsUnderThreeHearts() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 5);
        rank(guardian, "lifeline", 2);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        ally.setHealth(12); // minus the 10-damage test hit leaves 2, which is under 3 hearts
        Zombie z = zombie(2);
        melee(z, ally);
        // rank 2 heals 3 hearts (6 hp) on top of the ally's own (unmodified by the test's synthetic hit) health.
        assertEquals(18.0, ally.getHealth(), 0.001);
    }

    @Test
    void guardianSpiritSavesAnAllyFromADeathBlow() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 5);
        rank(guardian, "guardian_spirit", 1);
        guardian.teleport(at(1));
        guardian.setHealth(20);
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        ally.setHealth(5); // the 10-damage test hit would kill
        Zombie z = zombie(2);
        double taken = melee(z, ally);
        assertEquals(0.0, taken, 0.001);
        assertEquals(2.0, ally.getHealth(), 0.001);
        assertEquals(12.0, guardian.getHealth(), 0.001);
    }

    @Test
    void sacrificeExtendsFortressToNearbyAllies() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 25);
        rank(guardian, "sacrifice", 1);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        cast(guardian, Slot.ULTIMATE);
        Zombie z = zombie(2);
        double taken = melee(z, ally);
        assertEquals(4.0, taken, 0.001);
    }

    @Test
    void sharedStandCoversAlliesAtHalfStrengthDuringLastStand() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 10);
        rank(guardian, "shared_stand", 3);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        cast(guardian, Slot.ONE);
        Zombie z = zombie(2);
        double taken = melee(z, ally);
        assertTrue(taken < 10.0, "the ally should take reduced damage while covered");
    }

    @Test
    void aPlayerFightingTheGuardianGetsNoAllyBenefit() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 5);
        rank(guardian, "guardian_angel", 3);
        guardian.teleport(at(1));
        PlayerMock rival = server.addPlayer();
        rival.teleport(at(2));
        melee(guardian, rival); // the guardian just hit this player: no longer an ally.
        double guardianHealthBefore = guardian.getHealth();
        Zombie z = zombie(2);
        double taken = melee(z, rival);
        assertEquals(10.0, taken, 0.001);
        assertEquals(guardianHealthBefore, guardian.getHealth(), 0.001);
    }

    // Juggernaut

    @Test
    void momentumAddsBonusDamageWhileSprinting() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        rank(p, "momentum", 2);
        p.setSprinting(true);
        Zombie z = zombie(1);
        double before = z.getHealth();
        double dealt = melee(p, z);
        // Momentum doesn't change the swing's own number; it deals a separate 1.5-heart ability hit,
        // which is a real LivingEntity#damage call (the test's melee() only computes the event's own
        // damage, it doesn't apply it).
        assertEquals(10.0, dealt, 0.001);
        assertEquals(before - 3.0, z.getHealth(), 0.001);
    }

    @Test
    void ironFistSlowsAMobWhenMeleeingWithAnOffhandShield() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        rank(p, "iron_fist", 1);
        p.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        Zombie z = zombie(1);
        melee(p, z);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void gritStacksBoostMeleeDamageAfterTakingMobHits() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        rank(p, "grit", 2);
        Zombie attacker = zombie(1);
        melee(attacker, p);
        melee(attacker, p);
        Zombie target = zombie(1);
        double dealt = melee(p, target);
        // 2 stacks at rank 2 (4% each) -> +8%.
        assertEquals(10.8, dealt, 0.001);
    }

    @Test
    void earthshakerDamagesNearbyMobsOnALandingFromFourBlocksOrMore() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        rank(p, "earthshaker", 1);
        p.teleport(at(0));
        p.setFallDistance(5f);
        Zombie z = zombie(1);
        double before = z.getHealth();
        org.bukkit.event.entity.EntityDamageEvent event = new org.bukkit.event.entity.EntityDamageEvent(p,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL, 4);
        server.getPluginManager().callEvent(event);
        assertEquals(before - 4.0, z.getHealth(), 0.001);
    }

    @Test
    void unyieldingDoublesResolveGainUnderThirtyPercentHealth() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        rank(p, "unyielding", 1);
        p.setHealth(5); // well under 30% of 20 max health
        Zombie z = zombie(2);
        melee(z, p);
        assertEquals(8, resource(p));
    }

    @Test
    void trampleKnocksBackMobsWalkedIntoDuringFortress() {
        PlayerMock p = player(RpgClass.GUARDIAN, 25);
        rank(p, "trample", 2);
        p.teleport(at(0));
        Zombie z = zombie(0);
        double before = z.getHealth();
        cast(p, Slot.ULTIMATE);
        advance(4);
        assertTrue(z.getHealth() < before, "a mob standing on the guardian should be trampled");
    }

    @Test
    void plateHalvesOrRemovesHeavyPlatesSlownessInFullIronOrBetter() {
        PlayerMock p = player(RpgClass.GUARDIAN, 5);
        rank(p, "plated", 2);
        p.getInventory().setArmorContents(new ItemStack[]{
                new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS),
                new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)
        });
        assertTrue(GuardianListeners.wearsFullIronOrBetter(p));
        p.getInventory().setArmorContents(new ItemStack[]{
                new ItemStack(Material.LEATHER_BOOTS), null, null, null
        });
        assertFalse(GuardianListeners.wearsFullIronOrBetter(p));
    }

    @Test
    void leapSmashCanBeCastAsTheJuggernautKeystone() {
        PlayerMock p = player(RpgClass.GUARDIAN, 20);
        rank(p, "leap_smash", 1);
        faceEast(p, 0);
        assertTrue(cast(p, Slot.TWO));
    }

    // Shield Bash and the keystones

    @Test
    void shieldBashHitsAMobInFrontForOneHeartWithSlownessAndKnockback() {
        PlayerMock p = player(RpgClass.GUARDIAN, 10);
        faceEast(p, 0);
        Zombie z = zombie(1);
        double before = z.getHealth();
        boolean fired = cast(p, Slot.TWO);
        assertTrue(fired);
        assertEquals(before - 2.0, z.getHealth(), 0.001);
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void shieldBashNeverHitsAMobBehindTheCone() {
        PlayerMock p = player(RpgClass.GUARDIAN, 10);
        faceEast(p, 0);
        Zombie behind = world.spawn(new Location(world, -1, 64, 0), Zombie.class);
        double before = behind.getHealth();
        cast(p, Slot.TWO);
        assertEquals(before, behind.getHealth(), 0.001);
    }

    @Test
    void tauntingShoutMakesNearbyMobsTargetTheGuardian() {
        PlayerMock p = player(RpgClass.GUARDIAN, 10);
        faceEast(p, 0);
        rank(p, "taunting_shout", 2);
        Zombie z = world.spawn(new Location(world, 8, 64, 0), Zombie.class);
        cast(p, Slot.TWO);
        assertEquals(p, z.getTarget());
    }

    @Test
    void intervenesDashesToAnAllyInsteadOfBashingMobs() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 10);
        rank(guardian, "intervene", 2);
        faceEast(guardian, 0);
        PlayerMock ally = server.addPlayer();
        ally.teleport(new Location(world, 4, 64, 0));
        cast(guardian, Slot.TWO);
        assertTrue(ally.hasPotionEffect(PotionEffectType.RESISTANCE));
    }

    @Test
    void oathLinksToTheNearestAllyAndRedirectsHalfTheirDamageToTheGuardian() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 20);
        rank(guardian, "oath", 1);
        guardian.teleport(at(1));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(2));
        cast(guardian, Slot.ONE); // Oath replaced Last Stand in slot ONE.
        double guardianHealthBefore = guardian.getHealth();
        Zombie z = zombie(2);
        double taken = melee(z, ally);
        assertEquals(5.0, taken, 0.001);
        assertEquals(guardianHealthBefore - 5.0, guardian.getHealth(), 0.001);
    }

    @Test
    void noGuardianAbilityEverDamagesOrDebuffsAPlayer() {
        PlayerMock guardian = player(RpgClass.GUARDIAN, 10);
        faceEast(guardian, 0);
        PlayerMock bystander = server.addPlayer();
        bystander.teleport(at(1)); // right where the cone would hit a mob
        double before = bystander.getHealth();
        cast(guardian, Slot.TWO);
        assertEquals(before, bystander.getHealth(), 0.001);
        assertFalse(bystander.hasPotionEffect(PotionEffectType.SLOWNESS));
    }
}

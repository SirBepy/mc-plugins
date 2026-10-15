package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rogue's tree shape, resource, and its own abilities (Smoke Bomb, Death Mark, and the
 * keystones that replace them). Per-node talents live in {@link RogueAssassinTest},
 * {@link RogueShadowTest}, {@link RogueVenomTest} and {@link RogueMovementTest}.
 */
class RogueKitTest extends KitTestBase {

    /** Teleports {@code p} to the origin, facing straight down +X so {@code zombie(x)} is "looked at". */
    private void faceAlongX(PlayerMock p) {
        Location loc = at(0);
        loc.setDirection(new org.bukkit.util.Vector(1, 0, 0));
        p.teleport(loc);
    }

    @Test
    void hasThirtyThreeTalentsInTheTwoThreeThreeTwoOneShapePerBranch() {
        var talents = kit(RpgClass.ROGUE).talents();
        assertEquals(33, talents.size());
        Set<String> ids = new HashSet<>();
        Map<Integer, Map<Integer, Integer>> perBranchTier = new HashMap<>();
        for (Talent t : talents) {
            assertTrue(ids.add(t.id()), "duplicate id " + t.id());
            perBranchTier.computeIfAbsent(t.branch(), k -> new HashMap<>()).merge(t.tier(), 1, Integer::sum);
        }
        for (int branch = 0; branch < 3; branch++) {
            Map<Integer, Integer> tiers = perBranchTier.get(branch);
            assertEquals(2, tiers.get(1), "branch " + branch + " tier 1");
            assertEquals(3, tiers.get(2), "branch " + branch + " tier 2");
            assertEquals(3, tiers.get(3), "branch " + branch + " tier 3");
            assertEquals(2, tiers.get(4), "branch " + branch + " tier 4");
            assertEquals(1, tiers.get(5), "branch " + branch + " tier 5 (keystone)");
        }
    }

    @Test
    void everyKeystoneReplacesTheDocumentedSlot() {
        var talents = kit(RpgClass.ROGUE).talents();
        assertEquals(Slot.TWO, talents.stream().filter(t -> t.id().equals("phantom_blades")).findFirst().orElseThrow().replaces());
        assertEquals(Slot.ONE, talents.stream().filter(t -> t.id().equals("shadow_form")).findFirst().orElseThrow().replaces());
        assertEquals(Slot.ONE, talents.stream().filter(t -> t.id().equals("viper_lunge")).findFirst().orElseThrow().replaces());
    }

    @Test
    void noTalentAffectsAPlayerWhoIsNotAnActiveRogue() {
        PlayerMock p = player(RpgClass.ROGUE, 25);
        rank(p, "backstab", 3);
        classes.leave(p.getUniqueId());
        Zombie z = zombie(2);
        assertEquals(10, melee(p, z), "no Sneak Attack, no Backstab once the class is left");
        assertEquals(0, resource(p));
    }

    @Test
    void sneakingMeleeHitGivesEightEnergy() {
        PlayerMock p = player(RpgClass.ROGUE, 1);
        p.setSneaking(true);
        Zombie z = zombie(2);
        melee(p, z);
        assertEquals(8, resource(p));
    }

    @Test
    void smokeBombGivesInvisibilityAndClearsNearbyMobTargets() {
        PlayerMock p = player(RpgClass.ROGUE, 10);
        p.teleport(at(0));
        Zombie z = zombie(2);
        z.setTarget(p);
        assertTrue(cast(p, Slot.TWO));
        assertTrue(p.hasPotionEffect(PotionEffectType.INVISIBILITY));
        assertNull(z.getTarget());
        assertTrue(z.hasPotionEffect(PotionEffectType.BLINDNESS));
    }

    @Test
    void deathMarkDealsThirtyPercentOfMaxHealthAfterFourSecondsIfUnhit() {
        PlayerMock p = player(RpgClass.ROGUE, 25);
        Zombie z = zombie(2);
        faceAlongX(p);
        assertTrue(cast(p, Slot.ULTIMATE));
        advance(80);
        assertEquals(20 - 0.30 * 20, z.getHealth(), 0.001);
    }

    @Test
    void deathMarkResolvesEarlyOnTheCastersNextHit() {
        PlayerMock p = player(RpgClass.ROGUE, 25);
        Zombie z = zombie(2);
        faceAlongX(p);
        assertTrue(cast(p, Slot.ULTIMATE));
        advance(10);
        double before = z.getHealth();
        melee(p, z);
        assertTrue(z.getHealth() < before - 0.30 * 20 + 0.01, "the mark's damage landed right away, not after the full 4 s");
    }

    @Test
    void deathMarkIsCappedAtFivePercentOnABoss() {
        PlayerMock p = player(RpgClass.ROGUE, 25);
        Zombie boss = zombie(2);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        boss.setHealth(200);
        faceAlongX(p);
        assertTrue(cast(p, Slot.ULTIMATE));
        advance(80);
        assertEquals(200 - 0.05 * 200, boss.getHealth(), 0.001);
    }

    @Test
    void phantomBladesKeystoneTeleportsBetweenMobsAndHitsEachOnce() {
        PlayerMock p = player(RpgClass.ROGUE, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.ROGUE, "phantom_blades", 1);
        p.teleport(at(0));
        Zombie a = zombie(2);
        Zombie b = zombie(4);
        double weaponDamage = RogueAbilities.weaponDamage(p);
        assertTrue(cast(p, Slot.TWO));
        advance(10);
        assertEquals(20 - weaponDamage, a.getHealth(), 0.01);
        assertEquals(20 - weaponDamage, b.getHealth(), 0.01);
    }

    @Test
    void shadowFormKeystoneBlocksTargetingAndItsEndingHitDealsFiftyPercentMore() {
        PlayerMock p = player(RpgClass.ROGUE, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.ROGUE, "shadow_form", 1);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        var targetEvent = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(z, p,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        server.getPluginManager().callEvent(targetEvent);
        assertTrue(targetEvent.isCancelled(), "Shadow Form cancels any attempt to target the Rogue");
        assertEquals(10 * 1.5, melee(p, z), 0.01, "the ending hit deals +50%");
    }

    @Test
    void viperLungeKeystoneDashesAndPoisonsAndWeakensTheMob() {
        PlayerMock p = player(RpgClass.ROGUE, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.ROGUE, "viper_lunge", 1);
        Zombie z = zombie(5);
        faceAlongX(p);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(z.hasPotionEffect(PotionEffectType.POISON));
        assertTrue(z.hasPotionEffect(PotionEffectType.WEAKNESS));
        assertTrue(p.getLocation().distance(z.getLocation()) < 4, "Viper Lunge dashes the Rogue to the mob");
    }
}

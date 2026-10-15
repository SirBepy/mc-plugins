package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Warrior's tree shape, resource, cooldowns and its three own abilities (Charge, Ground Slam,
 * and the keystones that replace them). Per-node combat talents live in {@link WarriorTalentsTest}.
 */
class WarriorKitTest extends KitTestBase {

    private WarriorKit warriorKit() {
        return (WarriorKit) kit(RpgClass.WARRIOR);
    }

    @Test
    void hasThirtyThreeTalentsInTheTwoThreeThreeTwoOneShapePerBranch() {
        List<Talent> talents = kit(RpgClass.WARRIOR).talents();
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
        assertEquals(Slot.ONE, kit(RpgClass.WARRIOR).talents().stream()
                .filter(t -> t.id().equals("undying_fury")).findFirst().orElseThrow().replaces());
        assertEquals(Slot.ONE, kit(RpgClass.WARRIOR).talents().stream()
                .filter(t -> t.id().equals("whirlwind")).findFirst().orElseThrow().replaces());
        assertEquals(Slot.TWO, kit(RpgClass.WARRIOR).talents().stream()
                .filter(t -> t.id().equals("war_banner")).findFirst().orElseThrow().replaces());
    }

    @Test
    void noTalentAffectsAPlayerWhoIsNotAnActiveWarrior() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        rank(p, "bloodlust", 3);
        rank(p, "frenzy", 3);
        classes.leave(p.getUniqueId());
        Zombie z = zombie(2);
        assertEquals(10, melee(p, z), "no Weapon Master, no Bloodlust once the class is left");
        assertEquals(0, resource(p));
    }

    @Test
    void meleeHitOnAMobGivesFiveRageAndAHitTakenGivesThree() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        Zombie z = zombie(2);
        melee(p, z);
        assertEquals(5, resource(p));
        hit(z, p, DamageCause.ENTITY_ATTACK, 2);
        assertEquals(8, resource(p));
    }

    @Test
    void thickBloodHalvesOrQuartersTheOutOfCombatDrain() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        engine.resources().gain(p, 40);
        rank(p, "thick_blood", 1);
        now += 11_000;
        engine.resources().tick(p);
        assertEquals(40 - 5 * 0.75, engine.resources().get(p.getUniqueId()), 1e-9);
    }

    @Test
    void bloodPriceCostsAHeartAndShortensAbilityOnesCooldown() {
        PlayerMock p = player(RpgClass.WARRIOR, 1);
        rank(p, "blood_price", 2);
        rank(p, "veteran", 1);
        assertEquals(20, p.getHealth());
        cast(p, Slot.ONE);
        assertEquals(18, p.getHealth(), "1 heart = 2 health points, a fixed cost regardless of rank");
        // base cooldown at level 1 is 90 s; blood_price rank 2 cuts 20%, veteran rank 1 cuts 5 s more.
        int expected = (int) Math.round(90 * 0.8 - 5);
        assertEquals(expected * 1000L, classes.cooldownLeftMillis(p.getUniqueId()));
    }

    @Test
    void chargeDashesTheCasterAndAppliesFurySpikeToTheFirstMobHit() {
        PlayerMock p = player(RpgClass.WARRIOR, 10);
        rank(p, "fury_spike", 1);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.TWO));
        assertTrue(p.getVelocity().getZ() != 0 || p.getVelocity().getX() != 0, "Charge gives the caster velocity");
        advance(10);
        assertEquals(20 - 2 * WarriorKit.HEART, z.getHealth(), 0.001, "Fury Spike rank 1: 2 hearts to the first mob");
    }

    @Test
    void bladestormRankTwoHitsEveryMobInChargesPathForFullWeaponDamage() {
        PlayerMock p = player(RpgClass.WARRIOR, 10);
        rank(p, "bladestorm", 2);
        p.teleport(at(0));
        Zombie first = zombie(1);
        Zombie second = zombie(1.5);
        double weaponDamage = WarriorKit.weaponDamage(p);
        assertTrue(cast(p, Slot.TWO));
        advance(10);
        assertEquals(20 - weaponDamage, first.getHealth(), 0.001, "Bladestorm rank 2: 100% weapon damage to every mob hit");
        assertEquals(20 - weaponDamage, second.getHealth(), 0.001, "Bladestorm rank 2: 100% weapon damage to every mob hit");
    }

    @Test
    void groundSlamHitsAndLaunchesEveryMobInRangeAfterTheLeap() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        p.teleport(at(0));
        Zombie z = zombie(2);
        assertTrue(cast(p, Slot.ULTIMATE));
        advance(10);
        assertEquals(20 - 6 * WarriorKit.HEART, z.getHealth(), 0.001);
        assertTrue(z.getVelocity().getY() > 0, "Ground Slam launches mobs it hits");
    }

    @Test
    void whirlwindKeystoneHitsNearbyMobsFourTimesForFortyPercentWeaponDamage() {
        PlayerMock p = player(RpgClass.WARRIOR, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.WARRIOR, "whirlwind", 1);
        p.teleport(at(0));
        Zombie z = zombie(1);
        double weaponDamage = WarriorKit.weaponDamage(p);
        assertTrue(cast(p, Slot.ONE));
        advance(50);
        double expected = 20 - 4 * (weaponDamage * 0.4);
        assertEquals(Math.max(0, expected), z.getHealth(), 0.01);
    }

    @Test
    void warBannerKeystoneHealsNearbyAlliesAndBoostsTheirResourceGain() {
        PlayerMock p = player(RpgClass.WARRIOR, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.WARRIOR, "war_banner", 1);
        PlayerMock ally = server.addPlayer();
        ally.teleport(p.getLocation());
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        advance(20);
        assertTrue(ally.getHealth() > 10, "Regeneration I reaches a nearby ally");
        engine.resources().gain(ally, 10);
        assertEquals(15, engine.resources().get(ally.getUniqueId()), "the banner's +50% gain reaches an ally too");
    }

    @Test
    void undyingFurySavesYouOnceEveryFiveMinutesAndRefillsRage() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        engine.talents().force(p.getUniqueId(), RpgClass.WARRIOR, "undying_fury", 1);
        Zombie z = zombie(1);
        p.setHealth(2);
        hit(z, p, DamageCause.ENTITY_ATTACK, 50);
        assertEquals(1, p.getHealth());
        assertEquals(100, resource(p));

        // On cooldown: a second lethal hit is not saved, so health is left where the test set it.
        p.setHealth(2);
        hit(z, p, DamageCause.ENTITY_ATTACK, 50);
        assertEquals(2, p.getHealth(), "still on its 5-minute cooldown");
    }

    @Test
    void unstoppableCancelsKnockbackAboveSeventyFiveRage() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        rank(p, "unstoppable", 1);
        engine.resources().set(p.getUniqueId(), 80);
        EntityKnockbackEvent event = new EntityKnockbackEvent(p, EntityKnockbackEvent.Cause.DAMAGE, new Vector(1, 0, 0));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());

        engine.resources().set(p.getUniqueId(), 50);
        EntityKnockbackEvent belowThreshold = new EntityKnockbackEvent(p, EntityKnockbackEvent.Cause.DAMAGE, new Vector(1, 0, 0));
        server.getPluginManager().callEvent(belowThreshold);
        assertFalse(belowThreshold.isCancelled());
    }
}

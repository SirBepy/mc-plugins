package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
 * The Paladin's tree shape, resource, its own abilities (Holy Light, Judgement) and the rules every
 * node must respect (mob-only ability damage, no help for a player you're fighting, nothing leaking
 * to a non-active Paladin). Per-node talent behaviour lives in {@link PaladinTalentsTest}.
 */
class PaladinKitTest extends KitTestBase {

    @Test
    void hasThirtyThreeTalentsInTheTwoThreeThreeTwoOneShapePerBranch() {
        List<Talent> talents = kit(RpgClass.PALADIN).talents();
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
        List<Talent> talents = kit(RpgClass.PALADIN).talents();
        assertEquals(Slot.TWO, talents.stream().filter(t -> t.id().equals("lightwell")).findFirst().orElseThrow().replaces());
        assertEquals(Slot.ONE, talents.stream().filter(t -> t.id().equals("divine_storm")).findFirst().orElseThrow().replaces());
        assertEquals(Slot.TWO, talents.stream().filter(t -> t.id().equals("ascension")).findFirst().orElseThrow().replaces());
    }

    @Test
    void noTalentAffectsAPlayerWhoIsNotAnActivePaladin() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "undead_bane", 3);
        rank(p, "radiance", 3);
        classes.leave(p.getUniqueId());
        Zombie z = zombie(2);
        assertEquals(10, melee(p, z), 1e-9, "no Merciful Hand, no Undead Bane once the class is left");
        assertFalse(cast(p, Slot.TWO), "Holy Light is unavailable with no active class");
        assertEquals(0, resource(p));
    }

    @Test
    void holyLightHealsSelfAndAlliesWithinSixBlocksForTwoHeartsAtBaseRank() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        p.teleport(at(0));
        p.setHealth(10);
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(14, p.getHealth(), 1e-9, "2 hearts = 4 health points");
        assertEquals(14, ally.getHealth(), 1e-9, "allies within 6 blocks heal the same base amount");
    }

    @Test
    void judgementDealsEightHeartsToUndeadFourToOtherMobsAndHealsAndCuresAllies() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        p.teleport(at(0));
        Zombie undead = zombie(2);
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        ally.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(20 - 16, undead.getHealth(), 0.001, "8 hearts to an undead mob");
        assertEquals(18, ally.getHealth(), 1e-9, "4 hearts healed to an ally");
        assertFalse(ally.hasPotionEffect(PotionEffectType.POISON), "Judgement cures poison");
    }

    @Test
    void divineStormDealsThreeHeartsToMobsDoubleToUndead() {
        PaladinKit kit = (PaladinKit) kit(RpgClass.PALADIN);
        PlayerMock p = player(RpgClass.PALADIN, 20);
        p.teleport(at(0));
        Zombie undead = zombie(1);
        Spider regular = world.spawn(at(2), Spider.class);
        // Divine Storm's damage is split from the lightning visual: World#strikeLightningEffect is
        // unimplemented in MockBukkit and would abort the test, so this calls the damage half directly.
        kit.dealDivineStormDamage(p, PaladinKit.nearestHostileMobs(at(0), 8, 4));
        assertEquals(20 - 12, undead.getHealth(), 0.001, "double on undead: 6 hearts");
        assertEquals(20 - 6, regular.getHealth(), 0.001, "3 hearts on a regular hostile mob");
    }

    @Test
    void ascensionGivesUpHalfHealthToHealAlliesDoubleWithResistance() {
        PlayerMock p = player(RpgClass.PALADIN, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.PALADIN, "ascension", 1);
        p.teleport(at(0));
        p.setHealth(16);
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(8, p.getHealth(), 1e-9, "gave up half of 16");
        assertEquals(20, ally.getHealth(), 1e-9, "healed twice the 8 given up (16), capped at max health 20");
        assertTrue(ally.hasPotionEffect(PotionEffectType.RESISTANCE));
    }

    @Test
    void lightwellKeystoneHealsNearbyAlliesEveryTwoSeconds() {
        PlayerMock p = player(RpgClass.PALADIN, 20);
        engine.talents().force(p.getUniqueId(), RpgClass.PALADIN, "lightwell", 1);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        advance(40);
        assertEquals(12, ally.getHealth(), 1e-9, "1 heart every 2 s, one tick after 40 ticks");
    }

    @Test
    void noAbilityOrTalentEverDamagesOrStunsAPlayer() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "hammer_of_justice", 2);
        rank(p, "chain_smite", 1);
        PlayerMock bystander = server.addPlayer();
        bystander.teleport(at(0));
        p.teleport(at(0));
        bystander.setHealth(20);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(20, bystander.getHealth(), 1e-9, "Judgement never touches a player");
        assertFalse(bystander.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void aPlayerFightingThePaladinGetsNoHealFromHolyLight() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        p.teleport(at(0));
        PlayerMock foe = server.addPlayer();
        foe.teleport(at(1));
        foe.setHealth(10);
        hit(foe, p, DamageCause.ENTITY_ATTACK, 1);
        hit(p, foe, DamageCause.ENTITY_ATTACK, 1);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(10, foe.getHealth(), 1e-9, "a player you just traded hits with doesn't count as an ally");
    }
}

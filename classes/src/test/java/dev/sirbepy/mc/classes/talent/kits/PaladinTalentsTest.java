package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One case per Paladin node that hooks a melee/heal/ability/kill event (docs/classes-revamp.md,
 * Paladin). Melee hits on a mob are always "base 10, times 0.95 for Merciful Hand, times the node's
 * own percent", since {@code ClassCombat} and this kit's listener run at the same priority in
 * registration order. Smite-related cases use level 1 (a fixed 2-heart, 4-health-point hit) so the
 * extra numbers stay simple.
 */
class PaladinTalentsTest extends KitTestBase {

    private PaladinHeals heals() {
        return ((PaladinKit) kit(RpgClass.PALADIN)).heals();
    }

    private void killByMelee(Zombie dead, PlayerMock killer) {
        dead.setLastDamageCause(new EntityDamageByEntityEvent(killer, dead, DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.GENERIC).build(), 5));
        dead.setKiller(killer);
        server.getPluginManager().callEvent(new EntityDeathEvent(dead, DamageSource.builder(DamageType.GENERIC).build(),
                new ArrayList<>(), 5));
    }

    // Light

    @Test
    void radianceSpillsAPercentOfAnyHealReceivedToNearbyAllies() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "radiance", 2);
        p.teleport(at(0));
        p.setHealth(10);
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        heals().healSelfCapped(p, 4);
        assertEquals(14, p.getHealth(), 1e-9);
        assertEquals(12, ally.getHealth(), 1e-9, "rank 2: 50% of the 4 hp heal spills to the ally");
    }

    @Test
    void radianceBetweenTwoPaladinsDoesNotLoop() {
        PlayerMock p1 = player(RpgClass.PALADIN, 1);
        rank(p1, "radiance", 3);
        PlayerMock p2 = player(RpgClass.PALADIN, 1);
        rank(p2, "radiance", 3);
        p1.teleport(at(0));
        p2.teleport(at(1));
        p1.setHealth(10);
        p2.setHealth(10);
        heals().healSelfCapped(p1, 4);
        assertEquals(14, p1.getHealth(), 1e-9);
        assertEquals(13, p2.getHealth(), 1e-9, "p2 gets one 75% spillover (3 hp); it never chains back to p1");
    }

    @Test
    void gentleTouchAddsHeartsToHolyLightsHealSize() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "gentle_touch", 2);
        p.teleport(at(0));
        p.setHealth(5);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(5 + 4 + 4, p.getHealth(), 1e-9, "base 2 hearts (4 hp) + rank 2 (4 hp)");
    }

    @Test
    void purifyRemovesOneBadEffectAtRankOne() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "purify", 1);
        p.teleport(at(0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));
        assertTrue(cast(p, Slot.TWO));
        assertFalse(p.hasPotionEffect(PotionEffectType.POISON), "removes the first bad effect in the fixed order");
        assertTrue(p.hasPotionEffect(PotionEffectType.WEAKNESS), "rank 1 only removes one");
    }

    @Test
    void purifyRankThreeRemovesEveryBadEffect() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "purify", 3);
        p.teleport(at(0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));
        assertTrue(cast(p, Slot.TWO));
        assertFalse(p.hasPotionEffect(PotionEffectType.POISON));
        assertFalse(p.hasPotionEffect(PotionEffectType.WEAKNESS));
    }

    @Test
    void sanctuaryReducesMobDamageToAHealedAllyForFourSeconds() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "sanctuary", 2);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        Zombie z = zombie(1);
        assertEquals(9, hit(z, ally, DamageCause.ENTITY_ATTACK, 10), 1e-9, "10% less from a mob while active");
        now += 5_000;
        assertEquals(10, hit(z, ally, DamageCause.ENTITY_ATTACK, 10), 1e-9, "window expired after 4 s");
    }

    @Test
    void layOnHandsDoublesHolyLightsHealOnAnAllyUnderFourHearts() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "lay_on_hands", 1);
        p.teleport(at(0));
        PlayerMock lowAlly = server.addPlayer();
        lowAlly.teleport(at(1));
        lowAlly.setHealth(6);
        PlayerMock healthyAlly = server.addPlayer();
        healthyAlly.teleport(at(1));
        healthyAlly.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(6 + 8, lowAlly.getHealth(), 1e-9, "double the base 4 hp heal under 4 hearts");
        assertEquals(10 + 4, healthyAlly.getHealth(), 1e-9, "not doubled above 4 hearts");
    }

    @Test
    void beaconLeavesAZoneThatHealsAlliesEveryTwoSeconds() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "beacon", 1);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        double afterCast = ally.getHealth();
        advance(80);
        assertTrue(ally.getHealth() > afterCast, "the zone healed the ally at least once over 4 s");
    }

    @Test
    void auraOfLightHealsNearbyAlliesEveryFewSeconds() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "aura_of_light", 2);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        advance(140);
        assertTrue(ally.getHealth() > 10, "healed at least once over the rank-2 6 s interval");
    }

    @Test
    void divineFavourIncreasesFaithFromHealingButNotFromUndeadKills() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "divine_favour", 2);
        p.teleport(at(0));
        p.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(4 * 2, resource(p), 1e-9, "100% more Faith from the 4 hp heal");
        engine.resources().set(p.getUniqueId(), 0);
        Zombie z = zombie(1);
        killByMelee(z, p);
        assertEquals(5, resource(p), 1e-9, "undead-kill Faith is untouched by Divine Favour");
    }

    @Test
    void guardianLightSavesANearbyAllyOnceEveryThirtyMinutes() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "guardian_light", 1);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        Zombie z = zombie(1);
        assertEquals(6, hit(z, ally, DamageCause.ENTITY_ATTACK, 50), 1e-9, "reduced to just enough to leave 2 hearts");
        assertEquals(4, ally.getHealth(), 1e-9, "saved at 2 hearts");

        ally.setHealth(10);
        assertEquals(50, hit(z, ally, DamageCause.ENTITY_ATTACK, 50), 1e-9, "on its 30-minute cooldown, no second save");
    }

    @Test
    void haloAddsExtraHealingToJudgement() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "halo", 2);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(5);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(20, ally.getHealth(), 1e-9, "5 + 8 (base) + 8 (rank 2 Halo) = 21, capped at max health 20");
    }

    // Crusader

    @Test
    void undeadBaneBoostsMeleeDamageToUndeadMobsOnly() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "undead_bane", 2);
        Zombie undead = zombie(1);
        assertEquals(10 * 0.95 * 1.10, melee(p, undead), 1e-9);
    }

    @Test
    void holyWeaponAddsFixedHeartsToSmitesTarget() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "holy_weapon", 2);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(20 - 4 - 4, z.getHealth(), 0.001, "Smite's level-1 2 hearts + Holy Weapon's 2 hearts");
    }

    @Test
    void consecrateBurnsUndeadStandingWhereSmiteLanded() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "consecrate", 1);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        double afterSmite = z.getHealth();
        advance(20);
        assertEquals(afterSmite - 2, z.getHealth(), 0.001, "1 heart (2 hp) burn per second");
    }

    @Test
    void zealShortensSmitesCooldownOnAMeleeUndeadKill() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "zeal", 2);
        assertTrue(cast(p, Slot.ONE), "starts the real level-1 90 s cooldown");
        long before = classes.cooldownLeftMillis(p.getUniqueId());
        assertEquals(90_000L, before);
        Zombie z = zombie(1);
        killByMelee(z, p);
        assertEquals(5, resource(p), 1e-9, "undead kill Faith");
        long expected = before - before * 10 / 100;
        assertEquals(expected, classes.cooldownLeftMillis(p.getUniqueId()));
    }

    @Test
    void righteousFuryMarksSmitesTargetForBonusMeleeDamageForFiveSeconds() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "righteous_fury", 2);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(10 * 0.95 * 1.20, melee(p, z), 1e-9, "rank 2: +20% melee for 5 s");
        now += 6_000;
        assertEquals(10 * 0.95, melee(p, z), 1e-9, "mark expired");
    }

    @Test
    void chainSmiteJumpsToTwoMoreNearbyMobsAtHalfDamage() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "chain_smite", 1);
        p.teleport(at(0));
        Zombie primary = zombie(1);
        Zombie extra1 = zombie(2);
        Zombie extra2 = zombie(3);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(20 - 4, primary.getHealth(), 0.001);
        assertEquals(20 - 2, extra1.getHealth(), 0.001, "half of Smite's 4 hp");
        assertEquals(20 - 2, extra2.getHealth(), 0.001);
    }

    @Test
    void exorcistSetsSmitesUndeadTargetOnFire() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "exorcist", 2);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(6 * 20, z.getFireTicks());
    }

    @Test
    void hammerOfJusticeStunsSmitesTarget() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "hammer_of_justice", 2);
        p.teleport(at(0));
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        PotionEffect effect = z.getPotionEffect(PotionEffectType.SLOWNESS);
        assertEquals(3, effect.getAmplifier(), "Slowness IV");
        assertEquals(40, effect.getDuration(), "2 s");
    }

    @Test
    void hammerOfJusticeNeverStunsABoss() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "hammer_of_justice", 1);
        p.teleport(at(0));
        Zombie boss = zombie(1);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        boss.setHealth(200);
        assertTrue(cast(p, Slot.ONE));
        assertFalse(boss.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void sunBladeBoostsMeleeDamageToUndeadOutdoorsInDaylight() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "sun_blade", 2);
        world.setTime(6000);
        Zombie undead = zombie(1);
        assertEquals(10 * 0.95 * 1.10, melee(p, undead), 1e-9, "a freshly generated simple world is outdoors at midday");
    }

    @Test
    void crusadeExtendsJudgementsReachToFourteenBlocks() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "crusade", 1);
        p.teleport(at(0));
        Spider far = world.spawn(at(10), Spider.class);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(20 - 8, far.getHealth(), 0.001, "4 hearts to a non-undead mob 10 blocks away, within the extended reach");
    }

    // Martyr

    @Test
    void selfSacrificeCostsAHeartButHealsOthersMore() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "self_sacrifice", 3);
        p.teleport(at(0));
        p.setHealth(10);
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(10);
        assertTrue(cast(p, Slot.TWO));
        assertEquals(12, p.getHealth(), 1e-9, "10 - 2 (cost) + 4 (base heal)");
        assertEquals(20, ally.getHealth(), 1e-9, "10 + 4 (base) + 6 (rank 3 bonus)");
    }

    @Test
    void enduranceOffsetsMercifulHandOnlyUnderHalfHealth() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "endurance", 2);
        p.teleport(at(0));
        p.setHealth(5);
        Zombie z = zombie(1);
        // Merciful Hand and Endurance stack as independent multipliers, same as every other node
        // stacks with its class passive in this engine (10 * 0.95 Merciful Hand * 1.05 Endurance).
        assertEquals(10 * 0.95 * 1.05, melee(p, z), 1e-9, "under half health: rank 2 nearly cancels Merciful Hand");
        p.setHealth(20);
        assertEquals(9.5, melee(p, z), 1e-9, "at full health, Merciful Hand still applies");
    }

    @Test
    void shieldOfFaithGrantsAbsorptionHeartsFromHolyLight() {
        PlayerMock p = player(RpgClass.PALADIN, 10);
        rank(p, "shield_of_faith", 2);
        p.teleport(at(0));
        assertTrue(cast(p, Slot.TWO));
        assertTrue(p.hasPotionEffect(PotionEffectType.ABSORPTION));
        assertEquals(1, p.getPotionEffect(PotionEffectType.ABSORPTION).getAmplifier(), "rank 2: 4 Absorption hearts");
    }

    @Test
    void penanceGrantsFaithPerHeartOfMobDamageTaken() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "penance", 2);
        p.teleport(at(0));
        p.setHealth(20);
        Zombie z = zombie(1);
        hit(z, p, DamageCause.ENTITY_ATTACK, 10);
        assertEquals(2 * 5, resource(p), 1e-9, "5 hearts of damage taken, 2 Faith each");
    }

    @Test
    void bloodOathHealsTheCasterUnderThirtyPercentHealthOnSmite() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "blood_oath", 2);
        p.teleport(at(0));
        p.setHealth(5);
        Zombie z = zombie(1);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(5 + 6, p.getHealth(), 1e-9, "rank 2: 3 hearts (6 hp)");
    }

    @Test
    void divineBubbleSavesOnceEveryTwentyMinutesAndGrantsAShortInvulnWindow() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "divine_bubble", 1);
        p.teleport(at(0));
        p.setHealth(10);
        Zombie z = zombie(1);
        assertEquals(9, hit(z, p, DamageCause.ENTITY_ATTACK, 50), 1e-9, "reduced to leave exactly 1 health");
        assertEquals(1, p.getHealth(), 1e-9);

        p.setHealth(10);
        assertEquals(0, hit(z, p, DamageCause.ENTITY_ATTACK, 30), 1e-9, "within the 3 s invuln window, fully cancelled");

        now += 4_000;
        p.setHealth(10);
        assertEquals(30, hit(z, p, DamageCause.ENTITY_ATTACK, 30), 1e-9, "window over, still on its 20-minute cooldown");
    }

    @Test
    void martyrdomHealsNearbyAlliesWhenThePaladinDies() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "martyrdom", 2);
        p.teleport(at(0));
        PlayerMock ally = server.addPlayer();
        ally.teleport(at(1));
        ally.setHealth(4);
        PlayerDeathEvent event = new PlayerDeathEvent(p, DamageSource.builder(DamageType.GENERIC).build(),
                new ArrayList<>(), 0, null);
        server.getPluginManager().callEvent(event);
        assertEquals(20, ally.getHealth(), 1e-9, "4 + 16 (rank 2: 8 hearts) capped at max health");
    }

    @Test
    void emberOfHopeHealsYouUnderFourHeartsOverTime() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "ember_of_hope", 2);
        p.teleport(at(0));
        p.setHealth(4);
        advance(40);
        assertEquals(5, p.getHealth(), 1e-9, "0.5 heart after one 2 s interval");
    }

    @Test
    void lastRitesAddsDamageToJudgementUnderHalfHealth() {
        PlayerMock p = player(RpgClass.PALADIN, 25);
        rank(p, "last_rites", 2);
        p.teleport(at(0));
        p.setHealth(5);
        Spider z = world.spawn(at(1), Spider.class);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(20 - 8 - 8, z.getHealth(), 0.001, "base 4 hearts (8 hp) + rank 2 (8 hp)");
    }

    @Test
    void unbrokenCancelsPoisonAndWitherEffectsOnly() {
        PlayerMock p = player(RpgClass.PALADIN, 1);
        rank(p, "unbroken", 1);
        EntityPotionEffectEvent poison = new EntityPotionEffectEvent(p, null,
                new PotionEffect(PotionEffectType.POISON, 200, 0), null,
                EntityPotionEffectEvent.Cause.MILK, EntityPotionEffectEvent.Action.ADDED, true);
        server.getPluginManager().callEvent(poison);
        assertTrue(poison.isCancelled());

        EntityPotionEffectEvent speed = new EntityPotionEffectEvent(p, null,
                new PotionEffect(PotionEffectType.SPEED, 200, 0), null,
                EntityPotionEffectEvent.Cause.MILK, EntityPotionEffectEvent.Action.ADDED, true);
        server.getPluginManager().callEvent(speed);
        assertFalse(speed.isCancelled(), "only poison and wither are blocked");
    }
}

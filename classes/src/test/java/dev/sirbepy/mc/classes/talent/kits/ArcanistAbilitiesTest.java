package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Resources;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Arcanist's abilities, keystones and resource (docs/classes-revamp.md, Arcanist). */
class ArcanistAbilitiesTest extends KitTestBase {

    private ArcanistKit kit() {
        return (ArcanistKit) kit(RpgClass.ARCANIST);
    }

    private void look(PlayerMock p, Location target) {
        Location loc = p.getLocation().clone();
        loc.setDirection(target.toVector().subtract(loc.toVector()));
        p.teleport(loc);
    }

    // Targeting helpers (extracted so they're testable without a travelling particle or a real
    // ray trace, which MockBukkit doesn't implement).

    @Test
    void boltTargetingPicksTheHostileMobYouLookAt() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        Zombie z = zombie(5);
        look(p, z.getLocation());
        assertEquals(z, ArcanistKit.findBoltTarget(p, 20));
    }

    @Test
    void boltTargetingIgnoresMobsOutsideTheLookCone() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        Location facingX = at(0);
        facingX.setDirection(new org.bukkit.util.Vector(1, 0, 0));
        p.teleport(facingX);
        // Facing +X, a zombie 10 blocks to the side is well outside the 20-degree cone.
        world.spawn(new Location(world, 1, 64, 10), Zombie.class);
        assertNull(ArcanistKit.findBoltTarget(p, 20));
    }

    @Test
    void lookAtGroundStopsAtTheFirstSolidBlock() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        Location facingX = at(0);
        facingX.setDirection(new org.bukkit.util.Vector(1, 0, 0));
        p.teleport(facingX);
        // Placed exactly on the ray the method itself will walk, so the test never has to guess at
        // a Location-to-block rounding rule MockBukkit might apply differently than production code.
        org.bukkit.util.Vector direction = p.getEyeLocation().getDirection().normalize();
        Location blockSpot = p.getEyeLocation().clone().add(direction.multiply(5));
        world.getBlockAt(blockSpot).setType(Material.STONE);
        Location point = ArcanistKit.findLookAtGround(p, 20);
        assertTrue(point.getX() < blockSpot.getX(), "should stop short of the block: " + point.getX());
        assertTrue(point.getX() > blockSpot.getX() - 1, "should stop just short: " + point.getX());
    }

    // Arcane Bolt

    @Test
    void arcaneBoltDealsFourHeartsAndFailsWithNoTarget() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        assertFalse(cast(p, Slot.TWO), "no target, no cooldown");
        assertEquals(0, engine.cooldowns().leftMillis(p.getUniqueId(), Slot.TWO));
        Zombie z = zombie(3);
        look(p, z.getLocation());
        assertTrue(cast(p, Slot.TWO));
        advance(20);
        assertEquals(12, z.getHealth(), 0.01, "20 - 8 health points (4 hearts)");
    }

    @Test
    void kindleAndChillApplyFireAndSlowOnImpact() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "kindle", 2);
        rank(p, "chill", 1);
        Zombie z = zombie(3);
        kit().applyBoltHit(p, z);
        assertEquals(4 * 20, z.getFireTicks());
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void fireballSplashesToNearbyMobs() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "fireball", 2);
        Zombie target = zombie(3);
        Zombie nearby = world.spawn(new Location(world, 4, 64, 0), Zombie.class);
        kit().applyBoltHit(p, target);
        assertEquals(12, target.getHealth(), 0.01);
        assertEquals(12, nearby.getHealth(), 0.01, "rank 2 splashes 100% of the bolt's damage");
    }

    @Test
    void icePierceHitsUpToThreeMobsInLine() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        rank(p, "ice_lance", 1);
        Zombie first = world.spawn(new Location(world, 3, 64, 0), Zombie.class);
        // Placed exactly on the caster-to-first line, further out, so it's unambiguously "behind" it.
        org.bukkit.util.Vector direction = first.getLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();
        Zombie second = world.spawn(p.getEyeLocation().clone().add(direction.multiply(6)), Zombie.class);
        var extra = ArcanistKit.findPierceTargets(p, first, 2);
        assertTrue(extra.contains(second));
    }

    @Test
    void coldSnapShortensArcaneBoltsCooldownOnASlowedKill() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "cold_snap", 1);
        // The cooldown is already running by the time the bolt lands, same as a real cast.
        engine.cooldowns().start(p.getUniqueId(), Slot.TWO, 30);
        Zombie z = zombie(3);
        z.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, 100, 0));
        z.setHealth(1);
        kit().applyBoltHit(p, z);
        assertTrue(z.isDead());
        assertEquals(20_000, engine.cooldowns().leftMillis(p.getUniqueId(), Slot.TWO));
    }

    // Meteor

    @Test
    void meteorDealsTenHeartsToMobsWithinFourBlocks() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        p.teleport(at(0));
        Location landing = ArcanistKit.findLookAtGround(p, 30);
        Zombie near = world.spawn(landing.clone(), Zombie.class);
        Zombie far = world.spawn(landing.clone().add(10, 0, 0), Zombie.class);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(0, near.getHealth(), 0.01, "20 - 20 health points (10 hearts)");
        assertEquals(20, far.getHealth(), 0.01, "outside the 4-block radius");
    }

    @Test
    void overchargeRefundsArcanaAfterTheUltimateZeroesIt() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        p.teleport(at(0));
        rank(p, "overcharge", 2);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertEquals(0, resource(p), "zeroed by the engine first");
        advance(2);
        assertEquals(40, resource(p), "then refunded a tick later");
    }

    // Mend add-ons (ability 1)

    @Test
    void iceArmourGivesAbsorptionOnMend() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "ice_armour", 2);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(p.hasPotionEffect(PotionEffectType.ABSORPTION));
        assertEquals(1, p.getPotionEffect(PotionEffectType.ABSORPTION).getAmplifier());
    }

    @Test
    void heatWaveDamagesMobsNearTheCasterOnMend() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        rank(p, "heat_wave", 3);
        Zombie z = world.spawn(at(2), Zombie.class);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(16, z.getHealth(), 0.01, "20 - 4 health points (2 hearts)");
    }

    @Test
    void frostNovaFreezesMobsOnMendButSkipsBosses() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        rank(p, "frost_nova", 2);
        Zombie normal = world.spawn(at(3), Zombie.class);
        Zombie boss = world.spawn(at(4), Zombie.class);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(frozen(normal));
        assertTrue(normal.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertFalse(frozen(boss), "rule 4: roots and stuns skip bosses");
        assertFalse(boss.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void slowTimeSlowsMobsOnMend() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        rank(p, "slow_time", 1);
        Zombie z = world.spawn(at(3), Zombie.class);
        assertTrue(cast(p, Slot.ONE));
        assertTrue(z.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertEquals(1, z.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier(), "Slowness II");
    }

    @Test
    void blinkMovesForwardButNeverIntoABlock() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        Location start = at(0);
        start.setDirection(new org.bukkit.util.Vector(1, 0, 0));
        p.teleport(start);
        rank(p, "blink", 1);
        // Placed exactly on the ray blink() itself walks (rank 1 is 4 blocks), same reasoning as
        // findLookAtGround's test: don't guess at a rounding rule, mirror the production formula.
        org.bukkit.util.Vector direction = p.getLocation().getDirection().setY(0).normalize();
        Location blockSpot = p.getLocation().clone().add(direction.multiply(4));
        world.getBlockAt(blockSpot).setType(Material.STONE);
        double startX = p.getLocation().getX();
        assertTrue(cast(p, Slot.ONE));
        assertTrue(p.getLocation().getX() < blockSpot.getX(), "stopped short of the block: " + p.getLocation().getX());
        assertTrue(p.getLocation().getX() > startX, "still moved most of the way: " + p.getLocation().getX());
    }

    @Test
    void temporalShieldReducesTheNextMobHitWithinFiveSeconds() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "temporal_shield", 2);
        assertTrue(cast(p, Slot.ONE));
        Zombie z = zombie(2);
        assertEquals(4, hit(z, p, DamageCause.ENTITY_ATTACK, 10), 0.01, "60% reduced");
        assertEquals(10, hit(z, p, DamageCause.ENTITY_ATTACK, 10), 0.01, "consumed by the first hit");
    }

    @Test
    void phoenixHealsExtraPerBurningMobNearby() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        p.teleport(at(0));
        p.setHealth(5);
        rank(p, "phoenix", 1);
        for (int i = 1; i <= 4; i++) {
            Zombie z = world.spawn(at(i), Zombie.class);
            z.setFireTicks(40);
        }
        assertTrue(cast(p, Slot.ONE));
        // Mend's own heal at level 10 plus Phoenix's bonus, capped at 3 burning mobs (6 extra hp).
        assertTrue(p.getHealth() > 5 + 6, "Mend healed on top of Phoenix's capped bonus: " + p.getHealth());
    }

    // Keystones

    @Test
    void flamestrikeDamagesAndIgnitesMobsAtTheLookedAtPoint() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        p.teleport(at(0));
        rank(p, "flamestrike", 1);
        Location landing = ArcanistKit.findLookAtGround(p, 20);
        Zombie z = world.spawn(landing.clone(), Zombie.class);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(10, z.getHealth(), 0.01, "20 - 10 health points (5 hearts)");
        assertEquals(6 * 20, z.getFireTicks());
    }

    @Test
    void iceTombCancelsMobDamageHalvesOutgoingDamageThenFrostNovas() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        p.teleport(at(0));
        rank(p, "ice_tomb", 1);
        p.setHealth(5);
        Zombie z = world.spawn(at(2), Zombie.class);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(13, p.getHealth(), 0.01, "healed 4 hearts (8 health points)");
        assertEquals(0, hit(z, p, DamageCause.ENTITY_ATTACK, 5), "mob damage cancelled during the tomb");
        assertEquals(5, hit(p, z, DamageCause.ENTITY_ATTACK, 10), 0.01, "the player's own damage is halved");
        advance(60);
        assertTrue(frozen(z), "then Frost Nova");
        assertEquals(5, hit(z, p, DamageCause.ENTITY_ATTACK, 5), 0.01, "tomb ended, damage no longer cancelled");
    }

    @Test
    void rewindReturnsToThePositionAndHealthFromFiveSecondsAgo() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        rank(p, "rewind", 1);
        Location before = at(0);
        p.teleport(before);
        advance(5);
        p.teleport(at(10));
        p.setHealth(10);
        advance(100);
        assertTrue(cast(p, Slot.ONE));
        assertEquals(before.getBlockX(), p.getLocation().getBlockX());
        assertEquals(20.0, p.getHealth(), 0.01);
    }

    @Test
    void rewindFailsWithNoHistoryYet() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        rank(p, "rewind", 1);
        assertFalse(cast(p, Slot.ONE));
    }

    // Resource

    @Test
    void orbPickupsGiveHalfArcanaAndAcceleratePercentIncreasesIt() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "accelerate", 2);
        server.getPluginManager().callEvent(new PlayerExpChangeEvent(p, 4));
        // 4 orb points at +1 per 2 (= 2 Arcana), doubled by Accelerate's +50%.
        assertEquals(3, resource(p), 0.01);
    }

    @Test
    void enchantingGivesFiveArcanaPerLevelSpent() {
        assertEquals(5, ArcanistKit.enchantArcana(0));
        assertEquals(15, ArcanistKit.enchantArcana(2));
    }

    @Test
    void anAbilityHitGivesFiveArcana() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        Zombie z = zombie(3);
        assertTrue(kit().dealAbility(p, z, 10));
        assertEquals(5, resource(p), 0.01);
    }

    @Test
    void borrowedTimeShortensArcaneBoltsCooldownPerOrbThreshold() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "borrowed_time", 2);
        engine.cooldowns().start(p.getUniqueId(), Slot.TWO, 30);
        server.getPluginManager().callEvent(new PlayerExpChangeEvent(p, 12));
        // Threshold 5 at rank 2: 12 points is 2 reductions of 1 s each.
        assertEquals(28_000, engine.cooldowns().leftMillis(p.getUniqueId(), Slot.TWO));
    }

    @Test
    void quickMindShortensAbilityOneAndTwoNeverBelowThirtySeconds() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "quick_mind", 3);
        // 31 s * 91% = 28.2 s, floored to the 30 s minimum for abilities 1 and 2.
        assertEquals(30, engine.cooldowns().adjust(p, Slot.ONE, 31), "never under the 30 s floor");
        assertEquals(30, engine.cooldowns().adjust(p, Slot.TWO, 31));
        // Comfortably above the floor, the 9% at rank 3 actually applies.
        assertEquals(91, engine.cooldowns().adjust(p, Slot.ONE, 100));
    }

    @Test
    void quickeningGivesSpeedAndHasteAtAFullBar() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "quickening", 1);
        engine.resources().set(p.getUniqueId(), 100);
        advance(5);
        assertNotNull(p.getPotionEffect(PotionEffectType.SPEED));
        assertNotNull(p.getPotionEffect(PotionEffectType.HASTE));
    }

    // Safety rules shared by every node

    @Test
    void emberSkinReducesFireDamageOnly() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "ember_skin", 2);
        var event = new org.bukkit.event.entity.EntityDamageEvent(p, DamageCause.FIRE,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ON_FIRE).build(), 10);
        server.getPluginManager().callEvent(event);
        assertEquals(5, event.getDamage(), 0.01, "50% less fire damage at rank 2");
    }

    @Test
    void brittleReducesDamageFromASlowedMob() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "brittle", 2);
        Zombie z = zombie(2);
        z.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, 100, 0));
        assertEquals(8, hit(z, p, DamageCause.ENTITY_ATTACK, 10), 0.01, "20% less at rank 2");
    }

    @Test
    void glacialWardFreezesAnAttackerButNeverABoss() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "glacial_ward", 3);
        roll = 0;
        Zombie attacker = zombie(2);
        hit(attacker, p, DamageCause.ENTITY_ATTACK, 5);
        assertTrue(frozen(attacker));
        Zombie boss = zombie(3);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150);
        hit(boss, p, DamageCause.ENTITY_ATTACK, 5);
        assertFalse(frozen(boss), "never a boss");
    }

    @Test
    void infernoBurstsOnABurningKill() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "inferno", 2);
        Zombie dying = zombie(3);
        dying.setFireTicks(40);
        Zombie nearby = world.spawn(new Location(world, 4, 64, 0), Zombie.class);
        dying.setHealth(0);
        dying.setKiller(p);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(dying,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), java.util.List.of()));
        assertEquals(17, nearby.getHealth(), 0.01, "20 - 3 health points (1.5 hearts at rank 2)");
    }

    @Test
    void noArcanistEffectEverTouchesAPlayer() {
        PlayerMock p = player(RpgClass.ARCANIST, 25);
        rank(p, "shatter", 3);
        rank(p, "combust", 2);
        PlayerMock otherPlayer = server.addPlayer();
        assertFalse(kit().dealAbility(p, otherPlayer, 10));
        assertEquals(20, otherPlayer.getHealth());
        assertFalse(otherPlayer.hasPotionEffect(PotionEffectType.SLOWNESS));
        assertTrue(otherPlayer.getFireTicks() <= 0, "never set alight: " + otherPlayer.getFireTicks());
    }

    @Test
    void nothingFiresForAPlayerWhoIsNotAnActiveArcanist() {
        PlayerMock p = player(RpgClass.ARCANIST, 10);
        rank(p, "ice_armour", 2);
        classes.leave(p.getUniqueId());
        assertFalse(cast(p, Slot.ONE), "no class means no ability");
        assertFalse(p.hasPotionEffect(PotionEffectType.ABSORPTION));
        assertEquals(0, resource(p));
        server.getPluginManager().callEvent(new PlayerExpChangeEvent(p, 10));
        assertEquals(0, resource(p), "orb pickups give no Arcana once the class is inactive");
    }

    private static boolean frozen(org.bukkit.entity.LivingEntity e) {
        var slow = e.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        return slow != null && slow.getAmplifier() >= 9;
    }
}

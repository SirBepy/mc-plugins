package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each of the Sentinel's attacks, run on its own against the real fight's context. */
class SentinelAttackTest extends SentinelKitTestBase {

    // --- Stonebreaker Slam ---

    @Test
    void aGroundedPlayerInsideTheCircleIsHitAndThrownAwayAtImpactAndNotBefore() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = ctx(boss);
        RunningAttack slam = SLAM.start(ctx);
        Location centre = AttackKit.slamPoint(ctx);
        assertEquals("slam_windup", body(boss).rig().pose(), "the wind-up pose plays as the warning starts");

        for (int tick = 1; tick < 30; tick++) {
            assertFalse(slam.tick());
            assertEquals(20.0, alice.getHealth(), "nothing lands before the warning is over (tick " + tick + ")");
        }
        slam.tick();

        assertEquals(20.0 - BASE * SentinelTuning.SLAM_DAMAGE_MULTIPLE, alice.getHealth(), 1e-6);
        Vector away = alice.getLocation().toVector().subtract(centre.toVector());
        Vector thrown = alice.getVelocity();
        assertTrue(thrown.getX() * away.getX() + thrown.getZ() * away.getZ() > 0, "thrown away from the impact");
        assertEquals(SentinelTuning.SLAM_KNOCKBACK_VERTICAL, thrown.getY(), 1e-6);
        assertEquals(SentinelTuning.SLAM_KNOCKBACK_HORIZONTAL, Math.hypot(thrown.getX(), thrown.getZ()), 1e-6);
        assertEquals("slam_hit", body(boss).rig().pose());
        assertTrue(effects.sounds.contains(Sound.ENTITY_GENERIC_EXPLODE));
        assertFalse(effects.cracks.isEmpty(), "ground debris flies at the impact");
        assertEquals(Material.STONE, effects.cracks.get(0).material());
    }

    @Test
    void theCircleIsDrawnForTheWholeWarningNotJustAtTheStart() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RunningAttack slam = SLAM.start(ctx(boss));

        for (int tick = 1; tick < 30; tick++) {
            int before = effects.dust.size();
            slam.tick();
            if (tick % 2 == 0) {
                assertTrue(effects.dust.size() > before, "a frame is drawn on tick " + tick);
            }
        }
    }

    @Test
    void aPlayerWhoIsAirborneAtImpactIsNotHit() {
        LivingEntity boss = spawn(Tier.NORMAL);
        airborne.add(alice.getUniqueId());
        RunningAttack slam = SLAM.start(ctx(boss));

        run(slam, 60);

        assertEquals(20.0, alice.getHealth(), "jumping the slam avoids it");
    }

    @Test
    void aPlayerOutsideTheCircleIsNotHit() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = ctx(boss);
        RunningAttack slam = SLAM.start(ctx);
        Location centre = AttackKit.slamPoint(ctx);
        alice.teleport(centre.clone().add(SentinelTuning.SLAM_RADIUS + 1.5, 0, 0));

        run(slam, 60);

        assertEquals(20.0, alice.getHealth());
    }

    @Test
    void theSlamLandsOnTheWarningTimeOfItsTier() {
        int[] expected = {30, 26, 22};
        for (Tier tier : Tier.values()) {
            alice.setHealth(20.0);
            LivingEntity boss = spawn(tier);
            RunningAttack slam = SLAM.start(ctx(boss));

            int landed = runUntil(slam, () -> alice.getHealth() < 20.0, 100);

            assertEquals(expected[tier.ordinal()], landed, tier + " warning");
            clearWorld();
        }
    }

    @Test
    void aBiggerTierSlamsAWiderCircle() {
        // 6.6 blocks from the impact: outside Normal's 6.0 radius, inside Heroic's 7.2.
        for (Tier tier : new Tier[] {Tier.NORMAL, Tier.HEROIC}) {
            alice.setHealth(20.0);
            LivingEntity boss = spawn(tier);
            FightContext ctx = ctx(boss);
            RunningAttack slam = SLAM.start(ctx);
            alice.teleport(AttackKit.slamPoint(ctx).add(6.6, 0, 0));

            run(slam, 60);

            assertEquals(tier == Tier.HEROIC, alice.getHealth() < 20.0, tier + " reach");
            clearWorld();
        }
    }

    @Test
    void theMythicRingCatchesAPlayerWhoRanOutOfTheCircleExactlyWhenItsOwnWarningEnds() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = ctx(boss);
        RunningAttack slam = SLAM.start(ctx);
        double radius = SentinelTuning.SLAM_RADIUS * 1.4;
        // Between the circle's edge and the ring's outer edge.
        alice.teleport(AttackKit.slamPoint(ctx).add(radius + 2.0, 0, 0));

        int impact = SentinelTuning.slamWarnTicks(Tier.MYTHIC);
        for (int tick = 1; tick <= impact + SentinelTuning.RING_WARN_TICKS - 1; tick++) {
            slam.tick();
            assertEquals(20.0, alice.getHealth(), "not hit by the circle or early by the ring (tick " + tick + ")");
        }
        slam.tick();

        assertEquals(20.0 - BASE * SentinelTuning.RING_DAMAGE_MULTIPLE, alice.getHealth(), 1e-6,
                "the ring lands " + SentinelTuning.RING_WARN_TICKS + " ticks after the first impact");
    }

    @Test
    void noOtherTierHasTheRing() {
        for (Tier tier : new Tier[] {Tier.NORMAL, Tier.HEROIC}) {
            LivingEntity boss = spawn(tier);
            FightContext ctx = ctx(boss);
            RunningAttack slam = SLAM.start(ctx);
            double radius = SentinelTuning.SLAM_RADIUS * (tier == Tier.NORMAL ? 1.0 : 1.2);
            alice.teleport(AttackKit.slamPoint(ctx).add(radius + 2.0, 0, 0));

            run(slam, 100);

            assertEquals(20.0, alice.getHealth(), tier + " has no second ring");
            clearWorld();
        }
    }

    @Test
    void aSlamNearTheEdgeDrawsAndHitsNothingOutsideTheArea() {
        LivingEntity boss = spawn(Tier.NORMAL);
        boss.teleport(at(13, 64, 0));
        alice.teleport(at(13, 64, 5));
        PlayerMock bob = player("Bob", 16.5, 64, 3.4); // outside the 30 block square, inside the circle's reach
        FightContext ctx = ctx(boss);
        RunningAttack slam = SLAM.start(ctx);
        assertTrue(AttackKit.slamPoint(ctx).getX() + SentinelTuning.SLAM_RADIUS > 15, "the circle overhangs the area");

        run(slam, 60);

        assertFalse(effects.dust.isEmpty());
        for (SentinelKitTestBase.Recorder.Dust dust : effects.dust) {
            assertTrue(ctx.area().contains(at(dust.x(), dust.y(), dust.z())), "dust outside the area: " + dust);
        }
        for (SentinelKitTestBase.Recorder.Crack crack : effects.cracks) {
            assertTrue(ctx.area().contains(at(crack.x(), crack.y(), crack.z())), "debris outside the area: " + crack);
        }
        assertEquals(20.0, bob.getHealth(), "someone outside the area is not a fighter");
        assertTrue(alice.getHealth() < 20.0, "while a fighter inside it is");
    }

    @Test
    void anImpactOutsideTheAreaFlingsNoDebrisButStillHitsFightersInsideTheCircle() {
        LivingEntity boss = spawn(Tier.NORMAL);
        boss.teleport(at(-13.5, 64, 0));
        alice.teleport(at(-13.5, 64, 5));
        FightContext ctx = ctx(boss);
        RunningAttack slam = SLAM.start(ctx);
        assertFalse(ctx.area().contains(AttackKit.slamPoint(ctx)), "the fist lands past the edge");

        run(slam, 60);

        assertTrue(effects.cracks.isEmpty(), "no debris where the fight does not reach");
        assertTrue(alice.getHealth() < 20.0);
    }

    @Test
    void theSlamIsTheBossDamageScaledNotAFixedNumber() throws ReflectiveOperationException {
        LivingEntity boss = spawn(Tier.HEROIC);
        giveAttackDamage(boss, 14.0);
        RunningAttack slam = SLAM.start(ctx(boss));

        run(slam, 60);

        assertEquals(20.0 - 14.0 * SentinelTuning.SLAM_DAMAGE_MULTIPLE, alice.getHealth(), 1e-6);
    }

    /** MockBukkit's Ravager has no attack-damage attribute, so one is put on it the way scaleOf in the rig test does. */
    private static void giveAttackDamage(LivingEntity boss, double value) throws ReflectiveOperationException {
        java.lang.reflect.Field attributes = org.mockbukkit.mockbukkit.entity.LivingEntityMock.class.getDeclaredField("attributes");
        attributes.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<org.bukkit.attribute.Attribute, org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock> byAttribute =
                (java.util.Map<org.bukkit.attribute.Attribute, org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock>) attributes.get(boss);
        byAttribute.put(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                new org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock(org.bukkit.attribute.Attribute.ATTACK_DAMAGE, value));
    }

    // --- Thorn Sweep ---

    @Test
    void theSweepHitsTheFrontAndTheArcEdgeButNotBeyondItOrBehind() {
        LivingEntity boss = spawn(Tier.NORMAL);
        alice.teleport(at(0, 64, 3));                                     // dead ahead, the nearest
        PlayerMock edge = player("Edge", 4 * Math.sin(Math.toRadians(60)), 64, 4 * Math.cos(Math.toRadians(60)));
        PlayerMock wide = player("Wide", 5 * Math.sin(Math.toRadians(80)), 64, 5 * Math.cos(Math.toRadians(80)));
        PlayerMock rear = player("Rear", 0, 64, -6);                      // inside the radius, behind the boss
        RunningAttack sweep = SWEEP.start(ctx(boss));

        run(sweep, 60);

        assertEquals(20.0 - BASE * SentinelTuning.SWEEP_DAMAGE_MULTIPLE, alice.getHealth(), 1e-6, "front");
        assertTrue(edge.getHealth() < 20.0, "inside the 70 degree half angle");
        assertEquals(20.0, wide.getHealth(), "80 degrees off the facing is outside the arc");
        assertEquals(20.0, rear.getHealth(), "stepping behind the boss avoids it");
    }

    @Test
    void theBossTurnsToTheNearestFighterAtWindUpAndHoldsThatHeading() {
        LivingEntity boss = spawn(Tier.NORMAL);
        alice.teleport(at(5, 64, 0)); // due east of a boss facing south
        RunningAttack sweep = SWEEP.start(ctx(boss));

        assertEquals(-90.0f, Location.normalizeYaw(boss.getLocation().getYaw()), 1e-3, "turned to face east");

        boss.setRotation(0f, 0f); // whatever else turns the body mid-swing
        sweep.tick();
        assertEquals(-90.0f, Location.normalizeYaw(boss.getLocation().getYaw()), 1e-3, "and held there");
        assertEquals("sweep_windup", body(boss).rig().pose());
    }

    @Test
    void theSweepFollowsTheNewHeadingNotTheOldOne() {
        LivingEntity boss = spawn(Tier.NORMAL);
        alice.teleport(at(5, 64, 0));
        PlayerMock offTheNewHeading = player("Rear", 0, 64, 6); // in front of the boss's old heading, off its new one
        RunningAttack sweep = SWEEP.start(ctx(boss));

        run(sweep, 60);

        assertTrue(alice.getHealth() < 20.0);
        assertEquals(20.0, offTheNewHeading.getHealth());
    }

    @Test
    void theSweepThrowsSidewaysAlongTheSwing() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RunningAttack sweep = SWEEP.start(ctx(boss));

        run(sweep, 60);

        Vector thrown = alice.getVelocity();
        assertTrue(thrown.getX() < -0.5, "toward the boss's right, -X when it faces +Z: " + thrown);
        assertEquals(0.0, thrown.getZ(), 1e-6, "none of it along the facing");
        assertEquals(SentinelTuning.SWEEP_KNOCKBACK_VERTICAL, thrown.getY(), 1e-6);
    }

    @Test
    void theSweepPoisonsOnHeroicAndMythicButNotOnNormal() {
        for (Tier tier : Tier.values()) {
            alice.setHealth(20.0);
            alice.removePotionEffect(PotionEffectType.POISON);
            LivingEntity boss = spawn(tier);
            RunningAttack sweep = SWEEP.start(ctx(boss));

            run(sweep, 60);

            assertTrue(alice.getHealth() < 20.0, tier + " still hits");
            assertEquals(tier != Tier.NORMAL, alice.hasPotionEffect(PotionEffectType.POISON), tier + " poison");
            clearWorld();
        }
    }

    @Test
    void theSweepLandsOnTheWarningTimeOfItsTier() {
        int[] expected = {24, 20, 18};
        for (Tier tier : Tier.values()) {
            alice.setHealth(20.0);
            LivingEntity boss = spawn(tier);
            RunningAttack sweep = SWEEP.start(ctx(boss));

            int landed = runUntil(sweep, () -> alice.getHealth() < 20.0, 100);

            assertEquals(expected[tier.ordinal()], landed, tier + " warning");
            clearWorld();
        }
    }

    // --- Root Eruption ---

    @Test
    void aSpotStaysWhereItWasMarkedSoWalkingAwayDodgesIt() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RunningAttack roots = ROOTS.start(ctx(boss));
        run(roots, SentinelTuning.rootWarnTicks(Tier.NORMAL) / 2);

        alice.teleport(at(3, 64, 5)); // 3 blocks off the spot, beyond its 1.6 radius
        run(roots, 100);

        assertEquals(20.0, alice.getHealth(), "the roots erupted where Alice had been");
        assertFalse(effects.cracks.isEmpty(), "and they still burst out of the ground");
    }

    @Test
    void aFighterWhoStaysIsHurtAndSlowedByASecondLevelSlowness() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RunningAttack roots = ROOTS.start(ctx(boss));
        assertEquals("rooted", body(boss).rig().pose());

        int landed = runUntil(roots, () -> alice.getHealth() < 20.0, 100);

        assertEquals(40, landed, "2.0 s warning on Normal");
        assertEquals(20.0 - BASE * SentinelTuning.ROOT_DAMAGE_MULTIPLE, alice.getHealth(), 1e-6);
        PotionEffect slow = alice.getPotionEffect(PotionEffectType.SLOWNESS);
        assertNotNull(slow);
        assertEquals(1, slow.getAmplifier(), "Slowness II");
        assertEquals(SentinelTuning.ROOT_SLOW_TICKS, slow.getDuration());
    }

    @Test
    void rootsLandOnTheWarningTimeOfEachTier() {
        int[] expected = {40, 36, 32};
        for (Tier tier : Tier.values()) {
            alice.setHealth(20.0);
            LivingEntity boss = spawn(tier);
            RunningAttack roots = ROOTS.start(ctx(boss));

            int landed = runUntil(roots, () -> alice.getHealth() < 20.0, 100);

            assertEquals(expected[tier.ordinal()], landed, tier + " warning");
            clearWorld();
        }
    }

    /** Five fighters far enough apart that no two share a spot: Normal marks 2, Heroic 3, Mythic all 5 plus 2 extra spots. */
    @ParameterizedTest
    @EnumSource(Tier.class)
    void theNumberOfSpotsGrowsWithTheTierAndMythicAddsTwoMore(Tier tier) {
        int marked = SentinelTuning.byTier(tier, 2, 3, 5);
        int bursts = SentinelTuning.byTier(tier, 2, 3, 7);
        LivingEntity boss = spawn(tier);
        List<PlayerMock> everyone = new ArrayList<>(List.of(alice));
        double[] xs = {-12, -6, 6, 12};
        for (int i = 0; i < xs.length; i++) {
            everyone.add(player("P" + i, xs[i], 64, 5));
        }
        RunningAttack roots = ROOTS.start(ctx(boss));
        assertEquals(marked, roots.targets().size(), "fighters marked");

        run(roots, 100);

        assertEquals(marked, everyone.stream().filter(p -> p.getHealth() < 20.0).count(), "fighters hurt");
        assertEquals(bursts, effects.cracks.size(), "eruptions");
        assertTrue(effects.cracks.stream().allMatch(c -> ctx(boss).area().contains(at(c.x(), c.y(), c.z()))),
                "eruptions all inside the area");
    }

    @Test
    void rootsRiseFromTheSpotsWhileTheyWarn() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RunningAttack roots = ROOTS.start(ctx(boss));

        run(roots, 20);

        assertTrue(effects.particles.contains(Particle.COMPOSTER), "rising spores over the marked spot");
    }

    // --- Slam into Sweep ---

    @Test
    void aForcedChainFollowsTheSlamWithASweepInTheSameAttackSlot() {
        rng = new ForcedRandom(0.0);
        LivingEntity boss = spawn(Tier.HEROIC);
        RunningAttack combo = COMBO.start(ctx(boss));

        List<String> poses = new ArrayList<>();
        int ticks = 0;
        boolean done = false;
        while (!done && ticks < 300) {
            done = combo.tick();
            ticks++;
            String pose = body(boss).rig().pose();
            if (poses.isEmpty() || !poses.get(poses.size() - 1).equals(pose)) {
                poses.add(pose);
            }
        }

        assertTrue(done);
        assertEquals(List.of("slam_windup", "slam_hit", "sweep_windup", "sweep_hit"), poses);
        assertEquals(20.0 - BASE * SentinelTuning.SLAM_DAMAGE_MULTIPLE - BASE * SentinelTuning.SWEEP_DAMAGE_MULTIPLE,
                alice.getHealth(), 1e-6, "both blows landed");
    }

    @Test
    void aFailedRollEndsTheComboAfterTheSlam() {
        rng = new ForcedRandom(0.99);
        LivingEntity boss = spawn(Tier.HEROIC);
        RunningAttack combo = COMBO.start(ctx(boss));

        int ticks = run(combo, 300);

        assertTrue(ticks > 0);
        assertEquals(20.0 - BASE * SentinelTuning.SLAM_DAMAGE_MULTIPLE, alice.getHealth(), 1e-6, "only the slam");
        assertFalse(body(boss).rig().pose().startsWith("sweep"));
    }

    @Test
    void theChainMakesTheComboLongerThanItsSlamAlone() {
        rng = new ForcedRandom(0.0);
        LivingEntity chained = spawn(Tier.HEROIC);
        int chainedTicks = run(COMBO.start(ctx(chained)), 300);
        clearWorld();
        alice.setHealth(20.0);
        rng = new ForcedRandom(0.99);
        LivingEntity single = spawn(Tier.HEROIC);

        int singleTicks = run(COMBO.start(ctx(single)), 300);

        assertTrue(chainedTicks > singleTicks + SentinelTuning.sweepWarnTicks(Tier.HEROIC),
                "the sweep's whole wind-up is added inside the one attack: " + chainedTicks + " vs " + singleTicks);
    }

    // --- Shadow Bloom ---

    @Test
    void theBloomDarkensEveryoneInsideTheCircleAtTheEndAndHurtsNoOne() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        PlayerMock inside = player("Inside", 8, 64, 0);
        PlayerMock outside = player("Outside", 12, 64, 0);
        RunningAttack bloom = BLOOM.start(ctx(boss));
        assertEquals("heartguard", body(boss).rig().pose());

        for (int tick = 1; tick < SentinelTuning.BLOOM_WARN_TICKS; tick++) {
            bloom.tick();
            assertFalse(alice.hasPotionEffect(PotionEffectType.DARKNESS), "not before the warning ends (tick " + tick + ")");
        }
        bloom.tick();

        for (PlayerMock darkened : List.of(alice, inside)) {
            PotionEffect dark = darkened.getPotionEffect(PotionEffectType.DARKNESS);
            assertNotNull(dark, darkened.getName());
            assertEquals(0, dark.getAmplifier());
            assertEquals(SentinelTuning.BLOOM_DARKNESS_TICKS, dark.getDuration(), "5 seconds");
            assertEquals(20.0, darkened.getHealth(), "no damage");
        }
        assertNull(outside.getPotionEffect(PotionEffectType.DARKNESS), "outside the circle in time");
    }

    @Test
    void theBloomIsWarnedForItsWholeWindowWithDarkDustAndAHeartbeat() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        RunningAttack bloom = BLOOM.start(ctx(boss));

        for (int tick = 1; tick < SentinelTuning.BLOOM_WARN_TICKS; tick++) {
            int before = effects.dust.size();
            bloom.tick();
            if (tick % 2 == 0) {
                assertTrue(effects.dust.size() > before, "a frame on tick " + tick);
            }
        }

        assertTrue(effects.sounds.stream().filter(s -> s == Sound.ENTITY_WARDEN_HEARTBEAT).count() >= 3,
                "a heartbeat at the start and again as it builds");
        assertTrue(effects.particles.contains(Particle.SQUID_INK));
        assertTrue(effects.dust.stream().anyMatch(d -> d.color().getRed() < 40 && d.color().getBlue() < 60), "dark dust");
    }
}

package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.BossBrain;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.PhasePlan;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.RigBody;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Sentinel's plans, thorn patches, body hold, hit flash and restart, driven through the engine's own tick. */
class SentinelKitLifecycleTest extends SentinelKitTestBase {

    /** One attack start: the tick it began on and which attack. */
    private record Start(int tick, String id) {
    }

    /** Runs the runtime {@code ticks} ticks, keeping Alice alive, and lists every attack the brain began. */
    private List<Start> startsOver(BossBrain brain, int ticks) {
        List<Start> starts = new ArrayList<>();
        String previous = null;
        for (int tick = 1; tick <= ticks; tick++) {
            heal(alice);
            runtime.tick();
            String now = brain.currentAttackId().orElse(null);
            if (now != null && !now.equals(previous)) {
                starts.add(new Start(tick, now));
            }
            previous = now;
        }
        return starts;
    }

    private static List<String> ids(PhasePlan plan, int phase) {
        return plan.phase(phase).pool().stream().map(e -> e.attack().id()).collect(Collectors.toList());
    }

    private static int weightOf(PhasePlan plan, int phase, BossAttack attack) {
        return plan.phase(phase).pool().stream().filter(e -> e.attack().id().equals(attack.id())).findFirst().orElseThrow().weight();
    }

    // --- phase plans ---

    @Test
    void eachTierHasItsOwnPoolsAndTheRootsNeverComeBeforePhaseTwo() {
        PhasePlan normal = SentinelPlans.forTier(Tier.NORMAL);
        PhasePlan heroic = SentinelPlans.forTier(Tier.HEROIC);
        PhasePlan mythic = SentinelPlans.forTier(Tier.MYTHIC);

        assertEquals(List.of("stonebreaker_slam", "thorn_sweep"), ids(normal, 0));
        assertEquals(List.of("stonebreaker_slam", "thorn_sweep", "root_eruption"), ids(normal, 1));
        assertEquals(ids(normal, 1), ids(normal, 2));

        assertEquals(List.of("stonebreaker_slam", "thorn_sweep"), ids(heroic, 0));
        assertEquals(List.of("stonebreaker_slam", "thorn_sweep", "root_eruption", "slam_sweep_combo"), ids(heroic, 1));
        assertEquals(ids(heroic, 1), ids(heroic, 2));

        assertEquals(List.of("stonebreaker_slam", "thorn_sweep", "shadow_bloom"), ids(mythic, 0));
        assertEquals(List.of("stonebreaker_slam", "thorn_sweep", "root_eruption", "slam_sweep_combo", "shadow_bloom"),
                ids(mythic, 1));
        assertEquals(ids(mythic, 1), ids(mythic, 2));

        for (Tier tier : Tier.values()) {
            assertFalse(ids(SentinelPlans.forTier(tier), 0).contains("root_eruption"), tier + " roots in phase 1");
            assertEquals(tier != Tier.NORMAL, ids(SentinelPlans.forTier(tier), 1).contains("slam_sweep_combo"),
                    tier + " combo");
            assertEquals(tier == Tier.MYTHIC, ids(SentinelPlans.forTier(tier), 0).contains("shadow_bloom"),
                    tier + " darkness");
        }
    }

    @Test
    void theBreakingPhaseWeightsRootsAndCombosUpAndTheGapsShrinkWithPhaseAndTier() {
        PhasePlan heroic = SentinelPlans.forTier(Tier.HEROIC);
        assertTrue(weightOf(heroic, 2, ROOTS) > weightOf(heroic, 1, ROOTS));
        assertTrue(weightOf(heroic, 2, COMBO) > weightOf(heroic, 1, COMBO));

        int[][] gaps = {{50, 40, 30}, {40, 32, 24}, {32, 26, 20}};
        for (Tier tier : Tier.values()) {
            PhasePlan plan = SentinelPlans.forTier(tier);
            for (int phase = 0; phase < 3; phase++) {
                assertEquals(gaps[tier.ordinal()][phase], plan.phase(phase).gapTicks(), tier + " phase " + phase);
            }
            assertEquals(PhasePlan.DEFAULT_OPENING_GAP_TICKS, plan.openingGapTicks());
        }
    }

    @Test
    void attacksStartFromTheOpeningGapAtFullHealthAndKeepComing() {
        LivingEntity boss = spawn(Tier.NORMAL);
        BossBrain brain = runtime.get(boss.getUniqueId()).orElseThrow();

        List<Start> starts = startsOver(brain, 500);

        assertEquals(0, brain.phase(), "still the first phase");
        assertTrue(starts.get(0).tick() <= PhasePlan.DEFAULT_OPENING_GAP_TICKS + 2, "first attack: " + starts.get(0));
        assertTrue(starts.size() >= 5, "a loop, not a single attack: " + starts);
        assertTrue(starts.stream().allMatch(s -> Set.of("stonebreaker_slam", "thorn_sweep").contains(s.id())), starts.toString());
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void aRestartedFightRunsItsOwnTiersPlan(Tier tier) {
        LivingEntity boss = spawn(tier);
        effects.held.add(boss.getUniqueId()); // as if saved mid-attack
        runtime.end(boss.getUniqueId(), "test restart");

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));

        assertFalse(isHeld(boss), "a boss that comes back is not left held");
        BossBrain brain = runtime.get(boss.getUniqueId()).orElseThrow();
        List<Start> starts = startsOver(brain, 2000);
        boolean bloomed = starts.stream().anyMatch(s -> s.id().equals("shadow_bloom"));
        assertEquals(tier == Tier.MYTHIC, bloomed, tier + " darkness appears only in the Mythic plan");
    }

    // --- body hold ---

    @Test
    void theBaseIsHeldForTheWholeAttackAndFreedWhenItEndsOrIsCancelled() {
        LivingEntity boss = spawn(Tier.NORMAL);
        assertFalse(isHeld(boss));

        var slam = SLAM.start(ctx(boss));
        assertTrue(isHeld(boss), "held from the first tick of the wind-up");
        run(slam, 29);
        assertTrue(isHeld(boss), "and through the impact");
        run(slam, 100);
        assertFalse(isHeld(boss), "freed once the follow-through is over");

        var again = SLAM.start(ctx(boss));
        assertTrue(isHeld(boss));
        again.cancel();
        assertFalse(isHeld(boss), "a cancelled attack frees it too");
    }

    @Test
    void aChainedComboHoldsTheBaseThroughBothBlows() {
        rng = new ForcedRandom(0.0);
        LivingEntity boss = spawn(Tier.HEROIC);
        var combo = COMBO.start(ctx(boss));

        int slamAlone = SentinelTuning.slamWarnTicks(Tier.HEROIC) + SentinelTuning.SLAM_RECOVERY_TICKS + 2;
        run(combo, slamAlone);
        assertTrue(isHeld(boss), "no gap between the slam and the sweep for the boss to wander in");
        run(combo, 200);
        assertFalse(isHeld(boss));
    }

    @Test
    void theBaseIsFreedWhenEveryFighterLeavesMidAttack() {
        LivingEntity boss = spawn(Tier.NORMAL);
        runPlan(boss, Tier.NORMAL, loop(SLAM));
        runtime.tick();
        runtime.tick();
        assertTrue(isHeld(boss), "winding up");

        alice.teleport(at(60, 64, 60)); // out of the area
        runtime.tick();

        assertFalse(isHeld(boss), "the brain dropped the attack and the hold with it");
    }

    @Test
    void theBaseIsFreedWhenTheFightEnds() {
        LivingEntity boss = spawn(Tier.NORMAL);
        runPlan(boss, Tier.NORMAL, loop(SLAM));
        runtime.tick();
        runtime.tick();
        assertTrue(isHeld(boss));

        runtime.end(boss.getUniqueId(), "test");

        assertFalse(isHeld(boss));
    }

    // --- Mythic thorn patches ---

    @Test
    void aMythicSlamLeavesTwoPatchesNearTheImpactInsideTheArea() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = runPlan(boss, Tier.MYTHIC, loop(SLAM)).context();
        ThornPatches patches = ThornPatches.of(ctx);

        int tick = 0;
        while (patches.count() == 0 && tick++ < 100) {
            heal(alice);
            runtime.tick();
        }

        assertEquals(1 + SentinelTuning.slamWarnTicks(Tier.MYTHIC), tick, "left at the first impact, not the ring");
        assertEquals(SentinelTuning.PATCHES_PER_SLAM, patches.count());
        Location impact = AttackKit.slamPoint(ctx);
        for (Location patch : patches.locations()) {
            assertTrue(patch.distance(impact) <= SentinelTuning.PATCH_SPREAD + 1e-6, "near the impact: " + patch);
            assertTrue(ctx.area().contains(patch), "inside the area: " + patch);
        }
    }

    @Test
    void normalAndHeroicSlamsLeaveNoPatches() {
        for (Tier tier : new Tier[] {Tier.NORMAL, Tier.HEROIC}) {
            LivingEntity boss = spawn(tier);
            FightContext ctx = runPlan(boss, tier, loop(SLAM)).context();

            for (int i = 0; i < 150; i++) {
                heal(alice);
                runtime.tick();
            }

            assertEquals(0, ThornPatches.of(ctx).count(), tier.name());
            clearWorld();
        }
    }

    @Test
    void neverMoreThanSixPatchesAliveAtOnce() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = runPlan(boss, Tier.MYTHIC, loop(SLAM)).context();
        ThornPatches patches = ThornPatches.of(ctx);

        int most = 0;
        int slams = 0;
        int previous = 0;
        for (int i = 0; i < 400; i++) {
            heal(alice);
            runtime.tick();
            most = Math.max(most, patches.count());
            slams += patches.count() > previous ? 1 : 0;
            previous = patches.count();
        }

        assertEquals(SentinelTuning.MAX_PATCHES, most, "the cap is reached (and never passed)");
        assertTrue(slams >= 4, "after enough slams for the cap to matter: " + slams);
    }

    @Test
    void aSeventhPatchPushesOutTheOldestSoOnlySixRemain() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        ThornPatches patches = ThornPatches.of(runPlan(boss, Tier.MYTHIC, idle()).context());

        for (int i = 0; i < 8; i++) {
            patches.add(at(i, 64, 0));
        }

        assertEquals(SentinelTuning.MAX_PATCHES, patches.count());
        assertEquals(2.0, patches.locations().get(0).getX(), "the first two went");
        assertEquals(7.0, patches.locations().get(5).getX());
    }

    @Test
    void aPatchExpiresAfterSixSecondsAndIsDrawnUntilThen() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = runPlan(boss, Tier.MYTHIC, loop(SLAM)).context();
        ThornPatches patches = ThornPatches.of(ctx);
        for (int guard = 0; patches.count() == 0 && guard < 100; guard++) {
            heal(alice);
            runtime.tick();
        }
        assertEquals(SentinelTuning.PATCHES_PER_SLAM, patches.count(), "the slam has landed");
        alice.teleport(at(60, 64, 60)); // nobody left to fight: no new slams, the patches still run their course

        int dustAtWindowStart = effects.dust.size();
        for (int tick = 1; tick < SentinelTuning.PATCH_LIFE_TICKS; tick++) {
            runtime.tick();
            assertEquals(SentinelTuning.PATCHES_PER_SLAM, patches.count(), "alive on tick " + tick);
            if (tick % 4 == 0) {
                assertTrue(effects.dust.size() > dustAtWindowStart, "drawn in the four ticks up to " + tick + ", never invisible");
                dustAtWindowStart = effects.dust.size();
            }
        }
        runtime.tick();

        assertEquals(0, patches.count(), "gone after exactly " + SentinelTuning.PATCH_LIFE_TICKS + " ticks (6 s)");
    }

    @Test
    void patchesAreRemovedWhenTheBossDiesAndStopDrawingAndHurting() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = runPlan(boss, Tier.MYTHIC, idle()).context();
        ThornPatches patches = ThornPatches.of(ctx);
        patches.add(alice.getLocation());
        runtime.tick();
        assertEquals(1, patches.count());

        server.getPluginManager().callEvent(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new ArrayList<>()));

        assertEquals(0, patches.count());
        double health = alice.getHealth();
        int dust = effects.dust.size();
        tickRuntime(40);
        assertEquals(health, alice.getHealth(), "a dead boss's patches hurt nobody");
        assertEquals(dust, effects.dust.size(), "and are not drawn");
    }

    @Test
    void aPatchPricksAtMostOncePerSecondNoMatterHowManyYouStandIn() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = runPlan(boss, Tier.MYTHIC, idle()).context();
        ThornPatches patches = ThornPatches.of(ctx);
        patches.add(alice.getLocation());
        patches.add(alice.getLocation().add(0.5, 0, 0));
        var bob = player("Bob", 10, 64, 10);

        runtime.tick();
        assertEquals(20.0 - SentinelTuning.PATCH_DAMAGE, alice.getHealth(), 1e-6, "pricked on the first tick");
        assertTrue(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "and slowed");
        for (int tick = 2; tick <= SentinelTuning.PATCH_HIT_INTERVAL_TICKS; tick++) {
            runtime.tick();
        }
        assertEquals(20.0 - SentinelTuning.PATCH_DAMAGE, alice.getHealth(), 1e-6, "once in the first second, from two patches");
        runtime.tick();

        assertEquals(20.0 - 2 * SentinelTuning.PATCH_DAMAGE, alice.getHealth(), 1e-6, "again a second later");
        assertEquals(20.0, bob.getHealth(), "someone off the patches is left alone");
    }

    // --- Shadow Bloom cooldown ---

    @Test
    void shadowBloomWaitsOutItsCooldownBetweenCasts() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        BossBrain brain = runPlan(boss, Tier.MYTHIC, loop(BLOOM));

        List<Start> starts = startsOver(brain, 600);

        assertEquals(2, starts.size(), starts.toString());
        assertEquals(1, starts.get(0).tick());
        assertTrue(starts.get(1).tick() - starts.get(0).tick() >= SentinelTuning.BLOOM_COOLDOWN_TICKS,
                "about 25 seconds apart: " + starts);
    }

    // --- hit flash ---

    private void hit(LivingEntity boss) {
        fight.onDamage(new EntityDamageByEntityEvent(alice, boss, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 1.0));
    }

    private boolean allGlowingRed(RigBody body) {
        return body.rig().displays().values().stream().allMatch(d -> d.isGlowing()
                && Color.RED.equals(d.getGlowColorOverride()));
    }

    private boolean noneGlowing(RigBody body) {
        return body.rig().displays().values().stream().noneMatch(ItemDisplay::isGlowing);
    }

    @Test
    void aHitMakesTheBodyGlowRedForThreeTicksThenStops() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RigBody body = body(boss);
        assertTrue(noneGlowing(body));

        hit(boss);

        assertTrue(allGlowingRed(body));
        runtime.tick();
        runtime.tick();
        assertTrue(allGlowingRed(body), "still lit on the second tick");
        runtime.tick();
        assertTrue(noneGlowing(body), "out on the third");
    }

    @Test
    void hitsDuringTheGlowDoNotRestartItButTheNextHitAfterwardsFlashesAgain() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RigBody body = body(boss);

        hit(boss);
        runtime.tick();
        hit(boss); // one tick in: rides the same glow
        runtime.tick();
        runtime.tick();
        assertTrue(noneGlowing(body), "three ticks after the first hit, however many came in between");

        hit(boss);
        assertTrue(allGlowingRed(body), "a later hit flashes again");
    }

    @Test
    void aCancelledHitDoesNotFlashTheBody() {
        LivingEntity boss = spawn(Tier.NORMAL);
        EntityDamageByEntityEvent cancelled = new EntityDamageByEntityEvent(alice, boss,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 5.0);
        cancelled.setCancelled(true);

        fight.onDamage(cancelled);

        assertTrue(noneGlowing(body(boss)));
    }

    @Test
    void theFlashSurvivesARestartOfTheFight() {
        LivingEntity boss = spawn(Tier.NORMAL);
        runtime.end(boss.getUniqueId(), "test restart");
        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));

        hit(boss);

        assertTrue(allGlowingRed(body(boss)));
    }
}

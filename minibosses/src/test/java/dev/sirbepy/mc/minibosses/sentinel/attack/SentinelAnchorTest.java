package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.BossBrain;
import dev.sirbepy.mc.minibosses.engine.EngineKeys;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.PhasePlan;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelAnchors.HitResult;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelAnchors.Kind;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BossBar;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Heartguard and Wild Regrowth, and the anchors they share, through the real fight and its listener. */
class SentinelAnchorTest extends SentinelKitTestBase {

    private static final Color BEAM_GREEN = Color.fromRGB(90, 230, 80);
    private static final Color RING_GREEN = Color.fromRGB(70, 200, 70);
    private static final int[] HEARTGUARD_ANCHORS = {3, 4, 4};
    private static final int[] REGROWTH_ANCHORS = {2, 3, 4};
    private static final int[] HITS_TO_BREAK = {3, 4, 5};

    private int probeStarts;
    private int probeCancels;

    /** An attack that does nothing for 40 ticks: something for a stagger to interrupt and a start to count. */
    private BossAttack probe() {
        return BossAttack.of("probe", c -> {
            probeStarts++;
            return new RunningAttack() {
                private int left = 40;

                @Override
                public boolean tick() {
                    return --left <= 0;
                }

                @Override
                public void cancel() {
                    probeCancels++;
                }
            };
        });
    }

    // --- helpers ---

    private double max(LivingEntity boss) {
        return boss.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private EntityDamageByEntityEvent playerHit(Entity target, double damage) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(alice, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), damage);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private SentinelAnchors anchors(FightContext ctx) {
        return SentinelAnchors.of(ctx);
    }

    /** Shields a fresh boss of {@code tier} by hand, with nothing else going on in its plan. */
    private FightContext shielded(Tier tier) {
        LivingEntity boss = spawn(tier);
        FightContext ctx = runPlan(boss, tier, idle()).context();
        SentinelDefence.of(ctx).raiseShield();
        return ctx;
    }

    /** Breaks every stand in {@code stands}: the tier's number of hits on each, ten ticks apart. */
    private void breakAll(List<ArmorStand> stands, Tier tier) {
        int hits = SentinelTuning.anchorHitsToBreak(tier);
        for (int round = 1; round <= hits; round++) {
            for (ArmorStand stand : stands) {
                playerHit(stand, 4.0);
            }
            if (round < hits) {
                tickRuntime(SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS);
            }
        }
    }

    private int liveStands() {
        return world.getEntitiesByClass(ArmorStand.class).size();
    }

    private double horizontal(Entity a, Entity b) {
        return Math.hypot(a.getLocation().getX() - b.getLocation().getX(), a.getLocation().getZ() - b.getLocation().getZ());
    }

    // --- the plan ---

    @Test
    void heartguardIsInPhasesTwoAndThreeWeightTwoAndRegrowthOnlyInPhaseThree() {
        for (Tier tier : Tier.values()) {
            PhasePlan plan = SentinelPlans.forTier(tier);
            for (int phase = 0; phase < 3; phase++) {
                List<String> ids = plan.phase(phase).pool().stream().map(e -> e.attack().id()).toList();
                assertEquals(phase > 0, ids.contains(Heartguard.ID), tier + " heartguard in phase " + (phase + 1));
                assertEquals(phase == 2, ids.contains(WildRegrowth.ID), tier + " regrowth in phase " + (phase + 1));
            }
            for (int phase = 1; phase < 3; phase++) {
                assertEquals(2, plan.phase(phase).pool().stream().filter(e -> e.attack().id().equals(Heartguard.ID))
                        .findFirst().orElseThrow().weight());
            }
            assertEquals(2, plan.phase(2).pool().stream().filter(e -> e.attack().id().equals(WildRegrowth.ID))
                    .findFirst().orElseThrow().weight());
        }
    }

    @Test
    void theCooldownsAreTwentyFiveAndThirtySeconds() {
        assertEquals(500, HEARTGUARD.cooldownTicks());
        assertEquals(600, REGROWTH.cooldownTicks());
    }

    // --- anchors: placement and look ---

    @ParameterizedTest
    @EnumSource(Tier.class)
    void heartguardRaisesTheTiersAnchorsFiveToEightBlocksOutSpreadInsideTheArea(Tier tier) {
        LivingEntity boss = spawn(tier);
        FightContext ctx = runPlan(boss, tier, loop(HEARTGUARD)).context();

        int tick = 0;
        while (anchors(ctx).live(Kind.HEARTGUARD) == 0 && tick++ < 60) {
            runtime.tick();
        }

        assertEquals(1 + SentinelTuning.HEARTGUARD_OPEN_TICKS, tick, "they rise once the chest is open, not before");
        List<ArmorStand> stands = anchors(ctx).stands(Kind.HEARTGUARD);
        assertEquals(HEARTGUARD_ANCHORS[tier.ordinal()], stands.size());
        for (ArmorStand stand : stands) {
            double out = horizontal(stand, boss);
            assertTrue(out >= SentinelTuning.ANCHOR_MIN_DISTANCE - 1e-6 && out <= SentinelTuning.ANCHOR_MAX_DISTANCE + 1e-6,
                    "5 to 8 blocks out: " + out);
            assertTrue(ctx.area().contains(stand.getLocation()), "inside the area");
            double ground = ctx.area().surfaceY(stand.getLocation().getX(), stand.getLocation().getZ(), boss.getLocation().getY());
            assertEquals(ground + SentinelTuning.ANCHOR_HEIGHT, stand.getLocation().getY(), 1e-6,
                    "head height above the ground, so melee reaches");
            for (ArmorStand other : stands) {
                if (other != stand) {
                    assertTrue(horizontal(stand, other) >= SentinelTuning.ANCHOR_MIN_SPACING, "spread out");
                }
            }
        }
    }

    @Test
    void anAnchorIsASmallGlowingInvisibleStandWearingAnAmethystBlockOwnedByTheFight() {
        FightContext ctx = shielded(Tier.NORMAL);

        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);

        assertTrue(stand.isSmall() && stand.isGlowing() && !stand.isVisible() && !stand.isMarker() && !stand.hasGravity());
        assertFalse(stand.hasBasePlate() || stand.hasArms());
        assertEquals(Material.AMETHYST_BLOCK, stand.getEquipment().getHelmet().getType());
        assertTrue(SentinelAnchors.isAnchor(stand));
        assertEquals(ctx.boss().getUniqueId().toString(),
                stand.getPersistentDataContainer().get(EngineKeys.owner(plugin), PersistentDataType.STRING));
        assertFalse(stand.isPersistent(), "never saved with the chunk");
        assertTrue(stand.getScoreboardTags().contains("minibosses.fight_temp"), "swept like any fight entity");
    }

    @Test
    void regrowthAnchorsWearMoss() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = runPlan(boss, Tier.NORMAL, idle()).context();

        ArmorStand stand = anchors(ctx).spawn(Kind.REGROWTH, 1).get(0);

        assertEquals(Material.MOSS_BLOCK, stand.getEquipment().getHelmet().getType());
    }

    @Test
    void anchorsStayInsideTheAreaAndSpreadEvenWithTheBossAtItsEdge() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        FightContext ctx = runPlan(boss, Tier.MYTHIC, idle()).context();
        boss.teleport(at(13, 64, 0));

        List<ArmorStand> stands = anchors(ctx).spawn(Kind.HEARTGUARD, 4);

        for (ArmorStand stand : stands) {
            assertTrue(ctx.area().contains(stand.getLocation()), "inside: " + stand.getLocation());
            for (ArmorStand other : stands) {
                if (other != stand) {
                    assertTrue(horizontal(stand, other) >= SentinelTuning.ANCHOR_MIN_SPACING);
                }
            }
        }
    }

    @Test
    void aGreenBeamIsDrawnFromEveryLiveAnchorToTheHeartEveryFourTicks() {
        FightContext ctx = shielded(Tier.NORMAL);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);
        effects.dust.clear();

        tickRuntime(SentinelTuning.BEAM_EVERY_TICKS);

        List<Recorder.Dust> beams = effects.dust.stream().filter(d -> BEAM_GREEN.equals(d.color())).toList();
        assertTrue(beams.stream().anyMatch(d -> Math.abs(d.x() - stand.getLocation().getX()) < 1e-6
                && Math.abs(d.y() - (stand.getLocation().getY() + 0.5)) < 1e-6), "starts at the anchor");
        var heart = body(ctx.boss()).rig().anchor("heart");
        assertTrue(beams.stream().anyMatch(d -> Math.hypot(d.x() - heart.getX(), d.z() - heart.getZ()) < 1e-6
                && Math.abs(d.y() - heart.getY()) < 1e-6), "and ends at the heart");

        effects.dust.clear();
        tickRuntime(SentinelTuning.BEAM_EVERY_TICKS - 1);
        assertTrue(effects.dust.stream().noneMatch(d -> BEAM_GREEN.equals(d.color())), "only every fourth tick");
    }

    // --- hits ---

    @ParameterizedTest
    @EnumSource(Tier.class)
    void aPlayerNeedsThreeFourOrFiveCountedHitsToBreakAnAnchor(Tier tier) {
        FightContext ctx = shielded(tier);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);
        int needed = HITS_TO_BREAK[tier.ordinal()];

        for (int hit = 1; hit < needed; hit++) {
            playerHit(stand, 4.0);
            assertTrue(stand.isValid(), "still up after hit " + hit);
            assertEquals(needed - hit, anchors(ctx).hitsLeft(stand));
            tickRuntime(SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS);
        }
        playerHit(stand, 4.0);

        assertFalse(stand.isValid(), "gone on hit " + needed);
        assertEquals(HEARTGUARD_ANCHORS[tier.ordinal()] - 1, anchors(ctx).live(Kind.HEARTGUARD));
    }

    @Test
    void hitsInsideTenTicksOfACountedHitCountOnce() {
        FightContext ctx = shielded(Tier.MYTHIC);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);

        playerHit(stand, 4.0);
        playerHit(stand, 4.0);
        playerHit(stand, 4.0);
        assertEquals(4, anchors(ctx).hitsLeft(stand), "a flurry in one tick is one hit");
        tickRuntime(SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS - 1);
        playerHit(stand, 4.0);
        assertEquals(4, anchors(ctx).hitsLeft(stand), "nine ticks later is still the same hit");
        tickRuntime(1);
        playerHit(stand, 4.0);

        assertEquals(3, anchors(ctx).hitsLeft(stand), "the tenth tick counts again");
    }

    @Test
    void everyPlayerEventOnAnAnchorIsCancelledSoItNeverTakesDamage() {
        FightContext ctx = shielded(Tier.NORMAL);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);

        assertTrue(playerHit(stand, 4.0).isCancelled());
    }

    @Test
    void anArrowAPlayerShotCountsAndEachArrowCountsOnceItsIntervalHasPassed() {
        FightContext ctx = shielded(Tier.NORMAL);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);
        Arrow arrow = world.spawn(at(0, 64, 5), Arrow.class);
        arrow.setShooter(alice);
        Arrow second = world.spawn(at(0, 64, 5), Arrow.class);
        second.setShooter(alice);

        EntityDamageByEntityEvent first = new EntityDamageByEntityEvent(arrow, stand,
                EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(alice).build(), 6.0);
        server.getPluginManager().callEvent(first);
        assertTrue(first.isCancelled());
        assertEquals(2, anchors(ctx).hitsLeft(stand), "the arrow counted");

        tickRuntime(SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(second, stand,
                EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).withDirectEntity(second).withCausingEntity(alice).build(), 6.0));
        assertEquals(1, anchors(ctx).hitsLeft(stand), "and so did the next one");
    }

    @Test
    void damageThatIsNotAPlayersNeverCountsAndNeverBreaksAnAnchor() {
        FightContext ctx = shielded(Tier.NORMAL);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);
        int before = anchors(ctx).hitsLeft(stand);
        Arrow stray = world.spawn(at(0, 64, 5), Arrow.class);

        List<EntityDamageEvent> events = List.of(
                new EntityDamageEvent(stand, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                        DamageSource.builder(DamageType.EXPLOSION).build(), 100.0),
                new EntityDamageEvent(stand, EntityDamageEvent.DamageCause.FIRE,
                        DamageSource.builder(DamageType.IN_FIRE).build(), 100.0),
                new EntityDamageByEntityEvent(ctx.boss(), stand, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(ctx.boss()).build(), 100.0),
                new EntityDamageByEntityEvent(stray, stand, EntityDamageEvent.DamageCause.PROJECTILE,
                        DamageSource.builder(DamageType.ARROW).withDirectEntity(stray).build(), 100.0));
        for (EntityDamageEvent event : events) {
            for (int i = 0; i < 4; i++) { // one more than the three hits that break a Normal anchor
                server.getPluginManager().callEvent(event);
                tickRuntime(SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS);
            }
            assertTrue(event.isCancelled(), event.getCause() + " is cancelled");
        }

        assertTrue(stand.isValid());
        assertEquals(before, anchors(ctx).hitsLeft(stand));
    }

    @Test
    void anAnchorHitDoesNotHurtTheBossAndNeverMakesAFighterButABossHitDuringTheShieldStillCredits() {
        FightContext ctx = shielded(Tier.NORMAL);
        LivingEntity boss = ctx.boss();
        double before = boss.getHealth();

        playerHit(anchors(ctx).stands(Kind.HEARTGUARD).get(0), 50.0);

        assertEquals(before, boss.getHealth(), "the anchor hit never reaches the boss");
        assertTrue(fight.fightersOf(boss).isEmpty(), "and credits nobody");

        EntityDamageByEntityEvent bossHit = playerHit(boss, 8.0);

        assertFalse(bossHit.isCancelled());
        assertEquals(2.0, bossHit.getDamage(), 1e-6, "a quarter of 8 gets through");
        assertTrue(fight.fightersOf(boss).contains(alice.getUniqueId()), "and the hitter is credited as a fighter");
    }

    @Test
    void anAnchorCannotBeEmptiedOrFilledByRightClick() {
        FightContext ctx = shielded(Tier.NORMAL);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);
        var swap = new org.bukkit.event.player.PlayerArmorStandManipulateEvent(alice, stand,
                new org.bukkit.inventory.ItemStack(Material.DIAMOND), stand.getEquipment().getHelmet(),
                org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.HAND);

        server.getPluginManager().callEvent(swap);

        assertTrue(swap.isCancelled());
    }

    @Test
    void breakingAnAnchorBurstsItIntoCrystalDebrisWithItsSound() {
        FightContext ctx = shielded(Tier.NORMAL);
        effects.sounds.clear();

        breakAll(List.of(anchors(ctx).stands(Kind.HEARTGUARD).get(0)), Tier.NORMAL);

        assertTrue(effects.cracks.stream().anyMatch(c -> c.material() == Material.AMETHYST_BLOCK));
        assertTrue(effects.sounds.contains(Sound.BLOCK_AMETHYST_BLOCK_BREAK));
    }

    // --- Heartguard ---

    @Test
    void theHeartguardCastHoldsTheBossFor30TicksPlaysTheOpenChestAndThenLetsItGo() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = ctx(boss);

        RunningAttack cast = HEARTGUARD.start(ctx);

        assertTrue(isHeld(boss));
        assertEquals("heartguard", body(boss).rig().pose());
        assertTrue(effects.sounds.contains(Sound.BLOCK_BEACON_ACTIVATE));
        assertEquals(SentinelTuning.HEARTGUARD_OPEN_TICKS, runUntil(cast, () -> anchors(ctx).live(Kind.HEARTGUARD) > 0, 60));
        assertTrue(isHeld(boss));
        int rest = run(cast, 60);
        assertEquals(SentinelTuning.HEARTGUARD_CAST_TICKS - SentinelTuning.HEARTGUARD_OPEN_TICKS, rest);
        assertFalse(isHeld(boss), "the cast is short; the shield is the pressure");
        assertTrue(SentinelDefence.of(ctx).shielded(), "and the shield lives on without the attack");
    }

    @Test
    void aCastCutShortBeforeTheChestOpensLeavesNoAnchors() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = ctx(boss);
        RunningAttack cast = HEARTGUARD.start(ctx);
        run(cast, 5);

        cast.cancel();

        assertEquals(0, anchors(ctx).live(Kind.HEARTGUARD));
        assertFalse(SentinelDefence.of(ctx).shielded());
    }

    @Test
    void whileAnyAnchorLivesTheBossTakesAQuarterDamageAndFullDamageOnceTheLastIsGone() {
        FightContext ctx = shielded(Tier.NORMAL);
        LivingEntity boss = ctx.boss();
        List<ArmorStand> stands = anchors(ctx).stands(Kind.HEARTGUARD);

        assertEquals(2.0, playerHit(boss, 8.0).getDamage(), 1e-6, "shielded");
        breakAll(stands.subList(0, 2), Tier.NORMAL);
        assertEquals(1, anchors(ctx).live(Kind.HEARTGUARD));
        assertEquals(2.0, playerHit(boss, 8.0).getDamage(), 1e-6, "one anchor is enough to hold the shield");
        breakAll(stands.subList(2, 3), Tier.NORMAL);

        assertEquals(8.0, playerHit(boss, 8.0).getDamage(), 1e-6, "no anchor, no shield");
    }

    @Test
    void theHeartPulsesWhileTheShieldHolds() {
        FightContext ctx = shielded(Tier.NORMAL);
        effects.particles.clear();

        tickRuntime(SentinelTuning.HEARTGUARD_PULSE_TICKS);

        assertTrue(effects.particles.contains(Particle.END_ROD));
    }

    @Test
    void breakingEveryAnchorStaggersTheBossStartsNoAttackAndOpensItToExtraDamageForFourSeconds() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = runPlan(boss, Tier.NORMAL, loop(probe())).context();
        SentinelDefence defence = SentinelDefence.of(ctx);
        defence.raiseShield();
        tickRuntime(3);
        assertEquals(1, probeStarts);
        breakAll(anchors(ctx).stands(Kind.HEARTGUARD), Tier.NORMAL);
        int startsBefore = probeStarts;

        tickRuntime(1);

        assertEquals("stagger", body(boss).rig().pose());
        assertTrue(defence.vulnerable());
        assertEquals(1.25, defence.damageMultiplier(), 1e-6);
        assertEquals(1, probeCancels, "the attack it was in the middle of was dropped");
        for (int tick = 1; tick < SentinelTuning.STAGGER_TICKS; tick++) {
            tickRuntime(1);
            assertEquals(startsBefore, probeStarts, "no attack while staggered (tick " + tick + ")");
            if (tick == 5) {
                assertTrue(isHeld(boss), "and it does not walk");
            }
        }
        tickRuntime(1);
        assertEquals(startsBefore + 1, probeStarts, "attacking again on the 30th tick after the stagger began");
        tickRuntime(1);
        assertFalse(isHeld(boss), "let go once the stagger is over");

        // The window ends 80 ticks after it opened: this tick is 31 + 1 in, so 48 more keep it, one more closes it.
        tickRuntime(SentinelTuning.VULNERABLE_TICKS - SentinelTuning.STAGGER_TICKS - 2);
        assertTrue(defence.vulnerable(), "still open on the last tick of the four seconds");
        tickRuntime(1);
        assertFalse(defence.vulnerable(), "closed after four seconds");
        assertEquals(1.0, defence.damageMultiplier(), 1e-6);
    }

    @Test
    void theOpenWindowAddsAQuarterToWhatTheBossTakes() {
        LivingEntity boss = spawn(Tier.NORMAL);
        FightContext ctx = runPlan(boss, Tier.NORMAL, idle()).context();
        SentinelDefence.of(ctx).raiseShield();
        breakAll(anchors(ctx).stands(Kind.HEARTGUARD), Tier.NORMAL);
        tickRuntime(1);

        assertEquals(10.0, playerHit(boss, 8.0).getDamage(), 1e-6);
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void aShieldThatRunsOutEndsWithNoOpeningAndTheAnchorsShatterHarmlessly(Tier tier) {
        FightContext ctx = shielded(tier);
        SentinelDefence defence = SentinelDefence.of(ctx);
        int life = SentinelTuning.heartguardLifeTicks(tier);
        assertEquals(new int[] {400, 360, 320}[tier.ordinal()], life);
        effects.particles.clear();

        tickRuntime(life - 1);
        assertTrue(defence.shieldHolding(), "still up one tick before the end");
        assertEquals(HEARTGUARD_ANCHORS[tier.ordinal()], anchors(ctx).live(Kind.HEARTGUARD));
        tickRuntime(1);

        assertEquals(0, anchors(ctx).live(Kind.HEARTGUARD));
        assertFalse(defence.shielded());
        assertFalse(defence.vulnerable(), "timing out earns the players nothing");
        assertEquals(1.0, defence.damageMultiplier(), 1e-6);
        assertNotEquals("stagger", body(ctx.boss()).rig().pose());
        assertTrue(effects.particles.contains(Particle.POOF), "a puff, not a break");
        assertTrue(effects.cracks.isEmpty());
        tickRuntime(5);
        assertFalse(defence.vulnerable(), "and nothing opens a tick later either");
    }

    @Test
    void onlyOneShieldAtATime() {
        FightContext ctx = shielded(Tier.NORMAL);
        SentinelDefence defence = SentinelDefence.of(ctx);

        assertFalse(HEARTGUARD.canStart(ctx), "not while one is up");
        assertFalse(REGROWTH.canStart(ctx), "nor does the boss root itself over one");
        defence.raiseShield();
        assertEquals(3, liveStands(), "raising it again adds nothing");

        breakAll(anchors(ctx).stands(Kind.HEARTGUARD), Tier.NORMAL);
        tickRuntime(1);

        assertTrue(HEARTGUARD.canStart(ctx), "free again once it is gone");
    }

    @Test
    void heartguardWaitsOutItsCooldownBetweenCasts() {
        LivingEntity boss = spawn(Tier.NORMAL);
        BossBrain brain = runPlan(boss, Tier.NORMAL, loop(HEARTGUARD));
        List<Integer> starts = new ArrayList<>();
        String previous = null;

        for (int tick = 1; tick <= 700; tick++) {
            runtime.tick();
            String now = brain.currentAttackId().orElse(null);
            if (now != null && !now.equals(previous)) {
                starts.add(tick);
            }
            previous = now;
        }

        assertEquals(2, starts.size(), starts.toString());
        assertTrue(starts.get(1) - starts.get(0) >= SentinelTuning.HEARTGUARD_COOLDOWN_TICKS, starts.toString());
    }

    @Test
    void theShieldAndEveryAnchorGoWhenTheBossDies() {
        FightContext ctx = shielded(Tier.HEROIC);
        LivingEntity boss = ctx.boss();
        assertEquals(4, liveStands());

        server.getPluginManager().callEvent(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new ArrayList<>()));

        assertEquals(0, liveStands());
    }

    @Test
    void theShieldAndEveryAnchorGoWhenTheFightEndsOrThePluginShutsDown() {
        FightContext ctx = shielded(Tier.MYTHIC);
        runtime.end(ctx.boss().getUniqueId(), "test");
        assertEquals(0, liveStands());

        clearWorld();
        shielded(Tier.NORMAL);
        assertEquals(3, liveStands());
        runtime.shutdown();
        assertEquals(0, liveStands());
    }

    @Test
    void aStrayAnchorLeftByACrashedFightIsSweptWithTheOtherTemporaryEntities() {
        LivingEntity boss = spawn(Tier.NORMAL);
        ArmorStand stray = world.spawn(at(5, 65, 5), ArmorStand.class, s -> {
            s.addScoreboardTag(SentinelAnchors.TAG);
            s.addScoreboardTag("minibosses.fight_temp");
            s.getPersistentDataContainer().set(EngineKeys.owner(plugin), PersistentDataType.STRING, UUID.randomUUID().toString());
        });

        runtime.sweepStrays();

        assertFalse(stray.isValid());
        assertTrue(boss.isValid());
    }

    // --- restart ---

    @Test
    void aBossResumedMidShieldIsNotShieldedNotHeldAndLeavesNoAnchorBehind() {
        LivingEntity boss = spawn(Tier.HEROIC);
        FightContext ctx = runPlan(boss, Tier.HEROIC, idle()).context();
        SentinelDefence.of(ctx).raiseShield();
        assertEquals(0.25, SentinelDefence.of(ctx).damageMultiplier(), 1e-6);
        runtime.end(boss.getUniqueId(), "test restart");
        effects.held.add(boss.getUniqueId()); // as if saved mid-attack
        // An anchor that somehow outlived the old fight: not saved normally, but it must not be left unbreakable.
        ArmorStand leftover = world.spawn(at(5, 65, 5), ArmorStand.class, s -> {
            s.addScoreboardTag(SentinelAnchors.TAG);
            s.getPersistentDataContainer().set(EngineKeys.owner(plugin), PersistentDataType.STRING,
                    boss.getUniqueId().toString());
        });

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));

        FightContext resumed = ctx(boss);
        assertFalse(SentinelDefence.of(resumed).shielded());
        assertEquals(1.0, SentinelDefence.of(resumed).damageMultiplier(), 1e-6);
        assertEquals(8.0, playerHit(boss, 8.0).getDamage(), 1e-6, "full damage again");
        assertFalse(isHeld(boss));
        assertEquals("idle", body(boss).rig().pose());
        assertFalse(leftover.isValid());
        assertEquals(0, liveStands());
    }

    @Test
    void aBossResumedMidChannelIsNotRootedAndHasNoBlossoms() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        boss.setHealth(max(boss) * 0.3);
        runPlan(boss, Tier.MYTHIC, loop(REGROWTH));
        tickRuntime(30);
        assertTrue(isHeld(boss), "rooted mid-channel");
        assertEquals(4, liveStands());
        runtime.end(boss.getUniqueId(), "test restart");
        effects.held.add(boss.getUniqueId()); // as if saved mid-attack

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));

        assertFalse(isHeld(boss));
        assertEquals(0, liveStands());
        assertEquals("idle", body(boss).rig().pose());
        assertEquals(0, anchors(ctx(boss)).live(Kind.REGROWTH), "a fresh fight, no channel");
    }

    // --- Wild Regrowth ---

    /** A boss at {@code fraction} of its health with a plan that only ever casts Wild Regrowth, one tick in (anchors planted). */
    private BossBrain channel(Tier tier, double fraction) {
        LivingEntity boss = spawn(tier);
        boss.setHealth(max(boss) * fraction);
        BossBrain brain = runPlan(boss, tier, loop(REGROWTH));
        tickRuntime(1);
        assertEquals("wild_regrowth", brain.currentAttackId().orElseThrow());
        return brain;
    }

    @Test
    void wildRegrowthIsNotCastAboveNinetyPercentHealth() {
        LivingEntity boss = spawn(Tier.NORMAL);
        BossBrain brain = runPlan(boss, Tier.NORMAL, loop(REGROWTH));
        FightContext ctx = brain.context();

        boss.setHealth(max(boss) * 0.91);
        assertFalse(REGROWTH.canStart(ctx));
        tickRuntime(100);
        assertTrue(brain.currentAttackId().isEmpty(), "nothing started, and nothing was planted");
        assertEquals(0, liveStands());

        boss.setHealth(max(boss) * 0.89);
        assertTrue(REGROWTH.canStart(ctx));
        tickRuntime(2);
        assertEquals("wild_regrowth", brain.currentAttackId().orElse(null));
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void theBossHealsOnePercentOfMaxPerLiveAnchorEachSecond(Tier tier) {
        BossBrain brain = channel(tier, 0.3);
        LivingEntity boss = brain.context().boss();
        int anchorCount = REGROWTH_ANCHORS[tier.ordinal()];
        assertEquals(anchorCount, anchors(brain.context()).live(Kind.REGROWTH));
        double start = boss.getHealth();

        tickRuntime(SentinelTuning.REGROWTH_HEAL_EVERY_TICKS - 1);
        assertTrue(effects.heals.isEmpty(), "nothing in the first second");
        tickRuntime(1);

        assertEquals(1, effects.heals.size());
        assertEquals(max(boss) * 0.01 * anchorCount, effects.heals.get(0), 1e-6);
        assertEquals(start + max(boss) * 0.01 * anchorCount, boss.getHealth(), 1e-6);
        tickRuntime(SentinelTuning.REGROWTH_HEAL_EVERY_TICKS);
        assertEquals(2, effects.heals.size(), "and again a second later");
    }

    @Test
    void healingShrinksAsAnchorsDie() {
        BossBrain brain = channel(Tier.NORMAL, 0.3);
        FightContext ctx = brain.context();
        ArmorStand first = anchors(ctx).stands(Kind.REGROWTH).get(0);
        double unit = max(ctx.boss()) * 0.01;

        playerHit(first, 4.0);
        tickRuntime(10);
        playerHit(first, 4.0);
        tickRuntime(10);
        assertEquals(1, effects.heals.size());
        assertEquals(2 * unit, effects.heals.get(0), 1e-6, "two blossoms, two percent");
        playerHit(first, 4.0);
        assertFalse(first.isValid());
        tickRuntime(20);

        assertEquals(2, effects.heals.size());
        assertEquals(unit, effects.heals.get(1), 1e-6, "one blossom left, one percent");
    }

    @Test
    void theBossNeverHealsAboveItsMax() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        boss.setHealth(max(boss) - 0.1);
        RunningAttack attack = REGROWTH.start(ctx(boss));

        run(attack, SentinelTuning.REGROWTH_HEAL_EVERY_TICKS);

        assertEquals(1, effects.heals.size());
        assertTrue(effects.heals.get(0) > 0.1, "the heal itself is bigger than the room left");
        assertEquals(max(boss), boss.getHealth(), 1e-6);
    }

    @Test
    void aDeadBossIsNeverHealed() {
        LivingEntity boss = spawn(Tier.NORMAL);
        boss.setHealth(max(boss) * 0.3);
        FightContext ctx = ctx(boss);
        RunningAttack attack = REGROWTH.start(ctx);
        boss.remove();

        run(attack, SentinelTuning.REGROWTH_HEAL_EVERY_TICKS * 2);

        assertTrue(effects.heals.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void theChannelLastsEightTenOrTwelveSecondsAndThenTheBlossomsWither(Tier tier) {
        BossBrain brain = channel(tier, 0.3);
        int channel = new int[] {160, 200, 240}[tier.ordinal()];
        assertEquals(channel, SentinelTuning.regrowthChannelTicks(tier));
        effects.particles.clear();

        int ticks = 0;
        while (brain.currentAttackId().isPresent() && ticks < 400) {
            tickRuntime(1);
            ticks++;
            assertTrue(ticks >= channel || isHeld(brain.context().boss()), "planted all the way through");
        }

        assertEquals(channel, ticks, tier + " channel");
        assertEquals(0, liveStands(), "withered");
        assertTrue(effects.particles.contains(Particle.POOF));
        assertEquals(channel / SentinelTuning.REGROWTH_HEAL_EVERY_TICKS, effects.heals.size(), "one heal per second, none after");
        assertFalse(SentinelDefence.of(brain.context()).vulnerable(), "a channel that ran out leaves no opening");
        assertFalse(isHeld(brain.context().boss()), "released");
    }

    @Test
    void theBossDoesNothingElseWhileRootedAndPlaysTheRootedPose() {
        BossBrain brain = channel(Tier.NORMAL, 0.3);
        assertEquals("rooted", body(brain.context().boss()).rig().pose());
        assertTrue(effects.sounds.contains(Sound.BLOCK_ROOTED_DIRT_PLACE));

        for (int tick = 1; tick < 150; tick++) {
            tickRuntime(1);
            assertEquals("wild_regrowth", brain.currentAttackId().orElse(null), "the slot is taken at tick " + tick);
            assertTrue(isHeld(brain.context().boss()));
        }
    }

    @Test
    void aRingOfGreenDustAroundTheBossShowsItIsHealing() {
        BossBrain brain = channel(Tier.NORMAL, 0.3);
        effects.dust.clear();

        tickRuntime(SentinelTuning.REGROWTH_RING_EVERY_TICKS);

        LivingEntity boss = brain.context().boss();
        List<Recorder.Dust> ring = effects.dust.stream().filter(d -> RING_GREEN.equals(d.color())).toList();
        assertTrue(ring.size() > 10, "drawn: " + ring.size());
        assertTrue(ring.stream().allMatch(d -> Math.abs(Math.hypot(d.x() - boss.getLocation().getX(),
                d.z() - boss.getLocation().getZ()) - SentinelTuning.REGROWTH_RING_RADIUS) < 0.05), "a circle around the boss");
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void destroyingEveryBlossomEndsTheChannelAtOnceAndStaggersAndOpensTheBoss(Tier tier) {
        BossBrain brain = channel(tier, 0.3);
        FightContext ctx = brain.context();
        LivingEntity boss = ctx.boss();
        int length = SentinelTuning.regrowthChannelTicks(tier);

        breakAll(anchors(ctx).stands(Kind.REGROWTH), tier);
        assertEquals(0, anchors(ctx).live(Kind.REGROWTH));
        tickRuntime(1);

        assertTrue(brain.currentAttackId().isEmpty(), "the channel is over on the spot");
        assertEquals("stagger", body(boss).rig().pose());
        assertTrue(SentinelDefence.of(ctx).vulnerable());
        assertEquals(1.25, SentinelDefence.of(ctx).damageMultiplier(), 1e-6);
        assertTrue((HITS_TO_BREAK[tier.ordinal()] - 1) * SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS + 2 < length,
                "well before the channel would have ended");
        int healed = effects.heals.size();
        tickRuntime(2);
        assertTrue(isHeld(boss), "staggered in place");
        tickRuntime(60);
        assertEquals(healed, effects.heals.size(), "no more healing once it is cut");
        assertFalse(isHeld(boss));
        assertEquals(10.0, playerHit(boss, 8.0).getDamage(), 1e-6, "a quarter more for the four seconds");
    }

    @Test
    void aCancelledChannelFreesTheBossAndRemovesTheBlossoms() {
        BossBrain brain = channel(Tier.HEROIC, 0.3);
        LivingEntity boss = brain.context().boss();
        assertTrue(isHeld(boss));
        assertEquals(3, liveStands());

        alice.teleport(at(60, 64, 60)); // nobody left in the area: the brain drops the attack
        tickRuntime(1);

        assertFalse(isHeld(boss));
        assertEquals(0, liveStands());
    }

    @Test
    void aChannelInProgressWhenTheFightEndsLeavesNothingBehind() {
        BossBrain brain = channel(Tier.MYTHIC, 0.3);
        LivingEntity boss = brain.context().boss();

        runtime.end(boss.getUniqueId(), "test");

        assertFalse(isHeld(boss));
        assertEquals(0, liveStands());
    }

    // --- the boss bar ---

    @Test
    void theBossBarFollowsAHealWithoutWaitingForTheNextHit() throws ReflectiveOperationException {
        LivingEntity boss = spawn(Tier.NORMAL);
        boss.setHealth(max(boss) * 0.3);
        var field = fight.getClass().getDeclaredField("bossBars");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        BossBar bar = ((Map<UUID, BossBar>) field.get(fight)).get(boss.getUniqueId());

        fight.onRegain(new EntityRegainHealthEvent(boss, max(boss) * 0.1, EntityRegainHealthEvent.RegainReason.CUSTOM));

        assertEquals(0.4, bar.getProgress(), 1e-6);
    }

    @Test
    void aHitResultTellsACountedHitFromOneTooSoonAndFromNotAnAnchorAtAll() {
        FightContext ctx = shielded(Tier.NORMAL);
        ArmorStand stand = anchors(ctx).stands(Kind.HEARTGUARD).get(0);

        assertEquals(HitResult.COUNTED, anchors(ctx).hit(stand, alice));
        assertEquals(HitResult.TOO_SOON, anchors(ctx).hit(stand, alice));
        assertEquals(HitResult.NONE, anchors(ctx).hit(ctx.boss(), alice));
    }
}

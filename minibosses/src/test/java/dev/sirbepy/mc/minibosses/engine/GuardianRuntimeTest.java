package dev.sirbepy.mc.minibosses.engine;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.sirbepy.mc.minibosses.engine.PhasePlan.Entry;
import dev.sirbepy.mc.minibosses.engine.PhasePlan.Phase;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry, cleanup and resume: what happens to a fight and its temporary entities around it. */
class GuardianRuntimeTest extends EngineTestBase {

    private GuardianRuntime runtime;
    private final List<String> started = new ArrayList<>();
    private final AtomicInteger cancelledCount = new AtomicInteger();

    @BeforeEach
    void setUpRuntime() {
        runtime = new GuardianRuntime(plugin, () -> new Random(1), effects,
                player -> !airborne.contains(player.getUniqueId()));
        server.getPluginManager().registerEvents(runtime, plugin);
    }

    private BossAttack longAttack(String id) {
        return BossAttack.of(id, ctx -> {
            started.add(id);
            return new RunningAttack() {
                @Override
                public boolean tick() {
                    return false;
                }

                @Override
                public void cancel() {
                    cancelledCount.incrementAndGet();
                }
            };
        });
    }

    private PhasePlan plan() {
        List<Entry> pool = List.of(new Entry(longAttack("long"), 1));
        return new PhasePlan(List.of(new Phase("one", 5, pool), new Phase("two", 5, pool), new Phase("three", 5, pool)));
    }

    private LivingEntity startedBoss() {
        LivingEntity boss = boss();
        runtime.start(boss, Tier.NORMAL, plan(), area());
        return boss;
    }

    private ArmorStand spawnTemp(BossBrain brain) {
        return brain.context().entities().spawn(at(15, 64, 18), ArmorStand.class, stand -> stand.setMarker(true));
    }

    private EntityDeathEvent deathOf(LivingEntity boss) {
        return new EntityDeathEvent(boss, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), new ArrayList<>());
    }

    @Test
    void startRegistersABrainAndEndRemovesItAndEverythingItSpawned() {
        LivingEntity boss = boss();
        BossBrain brain = runtime.start(boss, Tier.NORMAL, plan(), area());
        assertTrue(runtime.get(boss.getUniqueId()).isPresent());
        ArmorStand add = spawnTemp(brain);
        assertTrue(add.isValid());

        assertTrue(runtime.end(boss.getUniqueId(), "test"));

        assertFalse(add.isValid(), "the temporary entity is gone with the fight");
        assertTrue(runtime.get(boss.getUniqueId()).isEmpty());
        assertFalse(runtime.end(boss.getUniqueId(), "again"), "ending twice is harmless");
    }

    @Test
    void temporaryEntitiesAreTaggedForTheConsoleAndOwnedByTheirBoss() {
        LivingEntity boss = boss();
        ArmorStand add = spawnTemp(runtime.start(boss, Tier.NORMAL, plan(), area()));

        assertTrue(add.getScoreboardTags().contains("minibosses.fight_temp"));
        assertFalse(FightEntities.TAG.contains(":"), "a colon would break @e[tag=...]");
        assertEquals(boss.getUniqueId().toString(),
                add.getPersistentDataContainer().get(EngineKeys.owner(plugin), PersistentDataType.STRING));
        assertFalse(add.isPersistent(), "a temporary entity must not be saved with the chunk");
    }

    @Test
    void bossDeathRemovesTheFightsEntitiesAfterTheGuardiansOwnHandlerRan() {
        LivingEntity boss = boss();
        ArmorStand add = spawnTemp(runtime.start(boss, Tier.NORMAL, plan(), area()));

        server.getPluginManager().callEvent(deathOf(boss));

        assertFalse(add.isValid());
        assertTrue(runtime.get(boss.getUniqueId()).isEmpty());
    }

    @Test
    void theBossLeavingTheWorldEndsTheFightAndItsEntities() {
        LivingEntity boss = boss();
        ArmorStand add = spawnTemp(runtime.start(boss, Tier.NORMAL, plan(), area()));

        runtime.onRemoveFromWorld(new EntityRemoveFromWorldEvent(boss, world));

        assertFalse(add.isValid());
        assertTrue(runtime.get(boss.getUniqueId()).isEmpty());
    }

    @Test
    void anAddLeavingTheWorldDoesNotEndTheBossesFight() {
        LivingEntity boss = boss();
        ArmorStand add = spawnTemp(runtime.start(boss, Tier.NORMAL, plan(), area()));

        runtime.onRemoveFromWorld(new EntityRemoveFromWorldEvent(add, world));

        assertTrue(runtime.get(boss.getUniqueId()).isPresent());
    }

    @Test
    void shutdownEndsEveryFightAndClearsTheirEntities() {
        LivingEntity first = boss();
        LivingEntity second = boss();
        ArmorStand a = spawnTemp(runtime.start(first, Tier.NORMAL, plan(), area()));
        ArmorStand b = spawnTemp(runtime.start(second, Tier.NORMAL, plan(), area()));

        runtime.shutdown();

        assertFalse(a.isValid());
        assertFalse(b.isValid());
        assertFalse(runtime.isRunning(first.getUniqueId()));
    }

    @Test
    void theStraySweepRemovesTaggedEntitiesWhoseOwnerIsNotRunning() {
        LivingEntity running = boss();
        ArmorStand kept = spawnTemp(runtime.start(running, Tier.NORMAL, plan(), area()));

        // A fight that died with the server: tagged entities, owner nowhere.
        Zombie stray = world.spawn(at(5, 64, 5), Zombie.class, z -> {
            z.addScoreboardTag(FightEntities.TAG);
            z.getPersistentDataContainer().set(EngineKeys.owner(plugin), PersistentDataType.STRING,
                    UUID.randomUUID().toString());
        });
        ArmorStand untaggedOwner = world.spawn(at(6, 64, 5), ArmorStand.class,
                s -> s.addScoreboardTag(FightEntities.TAG));
        ArmorStand unrelated = world.spawn(at(7, 64, 5), ArmorStand.class, s -> {
        });

        int removed = runtime.sweepStrays();

        assertEquals(2, removed);
        assertFalse(stray.isValid());
        assertFalse(untaggedOwner.isValid(), "a tagged entity with no readable owner is a stray too");
        assertTrue(kept.isValid(), "a running fight keeps its entities");
        assertTrue(unrelated.isValid(), "untagged entities are never touched");
    }

    @Test
    void chunkLoadsSweepStraysButSpareARunningFightsEntities() {
        LivingEntity boss = boss();
        ArmorStand kept = spawnTemp(runtime.start(boss, Tier.NORMAL, plan(), area()));
        Zombie stray = world.spawn(at(5, 64, 5), Zombie.class, z -> {
            z.addScoreboardTag(FightEntities.TAG);
            z.getPersistentDataContainer().set(EngineKeys.owner(plugin), PersistentDataType.STRING,
                    UUID.randomUUID().toString());
        });

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(stray, kept)));

        assertFalse(stray.isValid());
        assertTrue(kept.isValid());
    }

    @Test
    void anAttackScopedEntityGoesWhenTheAttackIsCancelledButAFightScopedOneStays() {
        LivingEntity boss = boss();
        BossBrain brain = runtime.start(boss, Tier.NORMAL, plan(), area());
        ArmorStand forTheFight = spawnTemp(brain);
        ArmorStand forTheAttack = brain.context().entities().spawnForAttack(at(15, 64, 18), ArmorStand.class,
                stand -> stand.setMarker(true));

        // Nobody in the arena: the running attack is cancelled, taking its own entities with it.
        alice.teleport(at(80, 64, 80));
        brain.tick();

        assertFalse(forTheAttack.isValid());
        assertTrue(forTheFight.isValid());
    }

    @Test
    void attackScopedEntitiesAreClearedWhenTheAttackFinishesOnItsOwn() {
        LivingEntity boss = boss();
        List<ArmorStand> spawned = new ArrayList<>();
        BossAttack oneShot = BossAttack.of("shot", ctx -> {
            spawned.add(ctx.entities().spawnForAttack(at(15, 64, 18), ArmorStand.class, s -> s.setMarker(true)));
            return new RunningAttack() {
                @Override
                public boolean tick() {
                    return true;
                }

                @Override
                public void cancel() {
                }
            };
        });
        List<Entry> pool = List.of(new Entry(oneShot, 1));
        BossBrain brain = runtime.start(boss, Tier.NORMAL,
                new PhasePlan(List.of(new Phase("a", 0, pool), new Phase("b", 0, pool), new Phase("c", 0, pool))), area());

        for (int i = 0; i < PhasePlan.DEFAULT_OPENING_GAP_TICKS + 3; i++) {
            brain.tick();
        }

        assertFalse(spawned.isEmpty());
        assertFalse(spawned.get(0).isValid(), "the attack is over, so are its entities");
    }

    @Test
    void aFighterQuittingCancelsAnAttackAimedOnlyAtThemThroughTheEventToo() {
        LivingEntity boss = boss();
        BossAttack lockOn = BossAttack.of("lockOn", ctx -> new RunningAttack() {
            @Override
            public boolean tick() {
                return false;
            }

            @Override
            public void cancel() {
                cancelledCount.incrementAndGet();
            }

            @Override
            public Set<UUID> targets() {
                return Set.of(alice.getUniqueId());
            }
        });
        List<Entry> pool = List.of(new Entry(lockOn, 1));
        BossBrain brain = runtime.start(boss, Tier.NORMAL,
                new PhasePlan(List.of(new Phase("a", 0, pool), new Phase("b", 0, pool), new Phase("c", 0, pool))), area());
        for (int i = 0; i < PhasePlan.DEFAULT_OPENING_GAP_TICKS + 3; i++) {
            brain.tick();
        }
        assertTrue(brain.currentAttackId().isPresent());

        runtime.onQuit(new PlayerQuitEvent(alice, net.kyori.adventure.text.Component.empty(),
                PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertTrue(brain.currentAttackId().isEmpty());
        assertEquals(1, cancelledCount.get());
    }

    @Test
    void theSharedTickDrivesEveryFightAndDropsOnesWhoseBossIsGone() {
        LivingEntity first = startedBoss();
        LivingEntity second = startedBoss();

        for (int i = 0; i < PhasePlan.DEFAULT_OPENING_GAP_TICKS + 2; i++) {
            runtime.tick();
        }
        assertEquals(List.of("long", "long"), started, "both fights ticked from the one shared call");

        second.remove();
        runtime.tick();
        assertTrue(runtime.get(first.getUniqueId()).isPresent());
        assertTrue(runtime.get(second.getUniqueId()).isEmpty());
    }

    @Test
    void aBrokenAttackEndsOnlyItsOwnFightInsteadOfStallingTheTask() {
        LivingEntity healthy = startedBoss();
        LivingEntity broken = boss();
        runtime.start(broken, Tier.NORMAL, new PhasePlan(List.of(
                new Phase("a", 0, List.of(new Entry(BossAttack.of("boom", ctx -> {
                    throw new IllegalStateException("boom");
                }), 1))),
                new Phase("b", 0, List.of(new Entry(longAttack("x"), 1))),
                new Phase("c", 0, List.of(new Entry(longAttack("y"), 1))))), area());

        for (int i = 0; i < PhasePlan.DEFAULT_OPENING_GAP_TICKS + 2; i++) {
            runtime.tick();
        }

        assertTrue(runtime.get(broken.getUniqueId()).isEmpty());
        assertTrue(runtime.get(healthy.getUniqueId()).isPresent());
        assertTrue(started.contains("long"), "the healthy fight kept going");
    }

    // --- resume after a restart ---

    private void stampAsGuardian(LivingEntity boss, String id, Tier tier) {
        var pdc = boss.getPersistentDataContainer();
        pdc.set(new org.bukkit.NamespacedKey(plugin, "guardian"), PersistentDataType.STRING, id);
        pdc.set(new org.bukkit.NamespacedKey(plugin, "guardian_tier"), PersistentDataType.STRING, tier.name());
    }

    @Test
    void aLiveGuardianFoundAfterARestartGetsItsBrainBackFromItsOwnResolver() {
        LivingEntity boss = boss();
        stampAsGuardian(boss, "test_guardian", Tier.HEROIC);
        List<Tier> resolvedTiers = new ArrayList<>();
        runtime.register("test_guardian", (found, tier) -> {
            resolvedTiers.add(tier);
            return Optional.of(new GuardianRuntime.Resumed(plan(), area()));
        });

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));

        assertEquals(List.of(Tier.HEROIC), resolvedTiers, "the resolver is told the tier the boss was summoned at");
        BossBrain brain = runtime.get(boss.getUniqueId()).orElseThrow();
        assertEquals(Tier.HEROIC, brain.context().tier());
    }

    @Test
    void aResumedFightContinuesInTheStoredPhaseWithoutRefiringHooks() {
        LivingEntity boss = boss();
        stampAsGuardian(boss, "test_guardian", Tier.NORMAL);
        boss.getPersistentDataContainer().set(EngineKeys.phase(plugin), PersistentDataType.INTEGER, 1);
        boss.setHealth(50);
        AtomicInteger hooks = new AtomicInteger();
        List<Entry> pool = List.of(new Entry(longAttack("resumed"), 1));
        runtime.register("test_guardian", (found, tier) -> Optional.of(new GuardianRuntime.Resumed(
                new PhasePlan(List.of(new Phase("a", 5, pool), new Phase("b", 5, pool, c -> hooks.incrementAndGet()),
                        new Phase("c", 5, pool, c -> hooks.incrementAndGet()))), area())));

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));
        for (int i = 0; i < 40; i++) {
            runtime.tick();
        }

        assertEquals(1, runtime.get(boss.getUniqueId()).orElseThrow().phase());
        assertEquals(0, hooks.get());
        assertTrue(started.contains("resumed"));
    }

    @Test
    void bossesWithoutAResolverOrAlreadyRunningOrDeadAreLeftAlone() {
        LivingEntity unknown = boss();
        stampAsGuardian(unknown, "nobody_registered_this", Tier.NORMAL);
        LivingEntity running = startedBoss();
        stampAsGuardian(running, "test_guardian", Tier.NORMAL);
        BossBrain before = runtime.get(running.getUniqueId()).orElseThrow();
        AtomicInteger calls = new AtomicInteger();
        runtime.register("test_guardian", (found, tier) -> {
            calls.incrementAndGet();
            return Optional.of(new GuardianRuntime.Resumed(plan(), area()));
        });

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(unknown, running)));

        assertTrue(runtime.get(unknown.getUniqueId()).isEmpty());
        assertEquals(0, calls.get());
        assertNotNull(runtime.get(running.getUniqueId()).orElseThrow());
        assertTrue(before == runtime.get(running.getUniqueId()).orElseThrow(), "a running fight is not replaced");
    }
}

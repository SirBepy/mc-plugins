package dev.sirbepy.mc.minibosses.engine;

import dev.sirbepy.mc.minibosses.engine.PhasePlan.Entry;
import dev.sirbepy.mc.minibosses.engine.PhasePlan.Phase;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The attack loop and phase machinery, driven tick by tick with fake attacks. */
class BossBrainTest extends EngineTestBase {

    /** Every attack start, in order. */
    private final List<String> log = new ArrayList<>();
    /** Every attack cancelled while running. */
    private final List<String> cancelled = new ArrayList<>();

    private BossAttack fake(String id, int duration) {
        return fake(id, duration, 0, Set.of());
    }

    private BossAttack fake(String id, int duration, int cooldown, Set<UUID> targets) {
        return BossAttack.of(id, cooldown, ctx -> {
            log.add(id);
            return new RunningAttack() {
                int left = duration;

                @Override
                public boolean tick() {
                    return --left <= 0;
                }

                @Override
                public void cancel() {
                    cancelled.add(id);
                }

                @Override
                public Set<UUID> targets() {
                    return targets;
                }
            };
        });
    }

    private static Phase phase(String name, int gap, Consumer<FightContext> onEnter, BossAttack... attacks) {
        List<Entry> pool = new ArrayList<>();
        for (BossAttack attack : attacks) {
            pool.add(new Entry(attack, 1));
        }
        return new Phase(name, gap, pool, onEnter);
    }

    private static void noHook(FightContext ctx) {
    }

    private PhasePlan plan(BossAttack opening, BossAttack second, BossAttack third) {
        return new PhasePlan(List.of(phase("one", 5, BossBrainTest::noHook, opening),
                phase("two", 5, BossBrainTest::noHook, second), phase("three", 5, BossBrainTest::noHook, third)));
    }

    private static void run(BossBrain brain, int ticks) {
        for (int i = 0; i < ticks; i++) {
            brain.tick();
        }
    }

    @Test
    void attacksStartInTheFirstSecondsAtFullHealthAndKeepRepeating() {
        BossBrain brain = new BossBrain(context(boss(), 1), plan(fake("slam", 10), fake("b", 10), fake("c", 10)));

        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 2);
        assertEquals(List.of("slam"), log, "the first attack starts right after the opening gap, with the boss untouched");

        run(brain, 300);
        assertTrue(log.size() > 10, "attacks must keep coming, not fire once: " + log.size());
        assertEquals(Set.of("slam"), new HashSet<>(log), "phase one's pool is the only one in use at full health");
    }

    @Test
    void theGapBetweenAttacksIsHonoured() {
        BossBrain brain = new BossBrain(context(boss(), 1), new PhasePlan(List.of(
                phase("one", 30, BossBrainTest::noHook, fake("slam", 10)),
                phase("two", 30, BossBrainTest::noHook, fake("b", 10)),
                phase("three", 30, BossBrainTest::noHook, fake("c", 10)))));

        run(brain, 400);

        // Each cycle is: start (1 tick) + 10 ticks running + 30 gap, so 41-ish ticks after the opening gap.
        assertTrue(log.size() >= 8 && log.size() <= 10, "expected about 9 attacks in 400 ticks, got " + log.size());
    }

    @Test
    void neverTheSameAttackTwiceInARowWhenThePoolHasMoreThanOne() {
        for (long seed = 0; seed < 20; seed++) {
            log.clear();
            BossBrain brain = new BossBrain(context(boss(), seed), new PhasePlan(List.of(
                    phase("one", 0, BossBrainTest::noHook, fake("a", 1), fake("b", 1), fake("c", 1)),
                    phase("two", 0, BossBrainTest::noHook, fake("a", 1)),
                    phase("three", 0, BossBrainTest::noHook, fake("a", 1)))));
            run(brain, 400);
            assertTrue(log.size() > 50);
            for (int i = 1; i < log.size(); i++) {
                assertFalse(log.get(i).equals(log.get(i - 1)), "repeat at " + i + " with seed " + seed + ": " + log);
            }
            assertEquals(Set.of("a", "b", "c"), new HashSet<>(log), "weighted random must reach every attack");
        }
    }

    @Test
    void aLoneAttackRepeatsBecauseThereIsNothingElseToPick() {
        BossBrain brain = new BossBrain(context(boss(), 1), plan(fake("only", 1), fake("b", 1), fake("c", 1)));
        run(brain, 200);
        assertTrue(log.size() > 3);
        assertEquals(Set.of("only"), new HashSet<>(log));
    }

    @Test
    void anAttackOnCooldownIsSkippedUntilItsCooldownHasPassed() {
        BossAttack heavy = fake("heavy", 1, 100, Set.of());
        BossAttack light = fake("light", 1);
        BossBrain brain = new BossBrain(context(boss(), 3), new PhasePlan(List.of(
                phase("one", 0, BossBrainTest::noHook, heavy, light),
                phase("two", 0, BossBrainTest::noHook, light),
                phase("three", 0, BossBrainTest::noHook, light))));

        run(brain, 330);

        long heavyCount = log.stream().filter("heavy"::equals).count();
        assertTrue(heavyCount >= 1 && heavyCount <= 4, "a 100 tick cooldown allows at most 4 heavy hits in 330 ticks: " + heavyCount);
    }

    @Test
    void anAttackThatCannotStartIsSkippedAndPicksGoToTheOthersUntilItCan() {
        boolean[] open = {false};
        BossAttack gated = new BossAttack() {
            @Override
            public String id() {
                return "gated";
            }

            @Override
            public boolean canStart(FightContext ctx) {
                return open[0];
            }

            @Override
            public RunningAttack start(FightContext ctx) {
                log.add("gated");
                return fake("gated", 1).start(ctx);
            }
        };
        BossAttack light = fake("light", 1);
        BossAttack other = fake("other", 1);
        BossBrain brain = new BossBrain(context(boss(), 3), new PhasePlan(List.of(
                phase("one", 0, BossBrainTest::noHook, gated, light, other),
                phase("two", 0, BossBrainTest::noHook, light),
                phase("three", 0, BossBrainTest::noHook, light))));

        run(brain, 100);
        assertEquals(Set.of("light", "other"), new HashSet<>(log), "closed: never picked, however long the loop runs");
        assertTrue(log.size() > 3, "and the loop is not stalled by it");

        open[0] = true;
        run(brain, 200);
        assertTrue(log.contains("gated"), "open: picked again");
    }

    @Test
    void aStaggerDropsTheRunningAttackAndStartsNothingUntilItHasPassed() {
        FightContext ctx = context(boss(), 1);
        BossBrain brain = new BossBrain(ctx, new PhasePlan(List.of(
                phase("one", 0, BossBrainTest::noHook, fake("slow", 100)),
                phase("two", 0, BossBrainTest::noHook, fake("slow", 100)),
                phase("three", 0, BossBrainTest::noHook, fake("slow", 100)))));
        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 10);
        assertEquals(List.of("slow"), log, "winding up");

        ctx.stagger(30);
        run(brain, 30);

        assertEquals(List.of("slow"), cancelled, "the attack in progress was dropped");
        assertEquals(List.of("slow"), log, "and nothing new started while staggered");
        run(brain, 2);
        assertEquals(List.of("slow", "slow"), log, "the boss attacks again once the stagger is over");
    }

    @Test
    void thePoolChangesWithThePhase() {
        LivingEntity boss = boss();
        BossBrain brain = new BossBrain(context(boss, 1), plan(fake("one", 1), fake("two", 1), fake("three", 1)));
        run(brain, 100);
        assertEquals(Set.of("one"), new HashSet<>(log));

        boss.setHealth(60); // 60%: phase two
        log.clear();
        run(brain, 100);
        assertEquals(Set.of("two"), new HashSet<>(log));

        boss.setHealth(20); // 20%: phase three
        log.clear();
        run(brain, 100);
        assertEquals(Set.of("three"), new HashSet<>(log));
    }

    @Test
    void phaseHooksFireExactlyOnceAtSeventyAndThirtyFivePercent() {
        LivingEntity boss = boss();
        List<String> hooks = new ArrayList<>();
        BossBrain brain = new BossBrain(context(boss, 1), new PhasePlan(List.of(
                phase("one", 5, c -> hooks.add("one"), fake("a", 1)),
                phase("two", 5, c -> hooks.add("two@" + c.phase()), fake("b", 1)),
                phase("three", 5, c -> hooks.add("three@" + c.phase()), fake("c", 1)))));

        boss.setHealth(71);
        run(brain, 10);
        assertEquals(List.of(), hooks, "71% is still the opening phase, and the opening phase has no entry hook");

        boss.setHealth(70);
        run(brain, 10);
        assertEquals(List.of("two@1"), hooks, "70% moves into phase two, once");

        boss.setHealth(50);
        run(brain, 10);
        assertEquals(List.of("two@1"), hooks, "staying in the phase does not re-fire the hook");

        boss.setHealth(35);
        run(brain, 10);
        assertEquals(List.of("two@1", "three@2"), hooks);

        boss.setHealth(1);
        run(brain, 50);
        assertEquals(List.of("two@1", "three@2"), hooks, "no third transition exists");
    }

    @Test
    void oneHitPastBothThresholdsFiresBothHooksInOrder() {
        LivingEntity boss = boss();
        List<String> hooks = new ArrayList<>();
        BossBrain brain = new BossBrain(context(boss, 1), new PhasePlan(List.of(
                phase("one", 5, c -> hooks.add("one"), fake("a", 1)),
                phase("two", 5, c -> hooks.add("two"), fake("b", 1)),
                phase("three", 5, c -> hooks.add("three"), fake("c", 1)))));

        boss.setHealth(10); // 100% straight to 10%
        brain.tick();

        assertEquals(List.of("two", "three"), hooks, "every skipped phase still gets its transition, in order");
        assertEquals(2, brain.phase());
    }

    @Test
    void healingBackAboveAThresholdNeverGoesBackAPhase() {
        LivingEntity boss = boss();
        List<String> hooks = new ArrayList<>();
        BossBrain brain = new BossBrain(context(boss, 1), new PhasePlan(List.of(
                phase("one", 5, c -> hooks.add("one"), fake("a", 1)),
                phase("two", 5, c -> hooks.add("two"), fake("b", 1)),
                phase("three", 5, c -> hooks.add("three"), fake("c", 1)))));
        boss.setHealth(60);
        run(brain, 3);
        boss.setHealth(100);
        run(brain, 3);
        assertEquals(1, brain.phase());
        boss.setHealth(60);
        run(brain, 3);
        assertEquals(List.of("two"), hooks);
    }

    @Test
    void customThresholdsMoveTheBands() {
        LivingEntity boss = boss();
        PhasePlan plan = new PhasePlan(List.of(0.5, 0.2), 10, List.of(
                phase("one", 5, BossBrainTest::noHook, fake("a", 1)),
                phase("two", 5, BossBrainTest::noHook, fake("b", 1)),
                phase("three", 5, BossBrainTest::noHook, fake("c", 1))));
        assertEquals(0, plan.phaseFor(0.6));
        assertEquals(1, plan.phaseFor(0.5));
        assertEquals(1, plan.phaseFor(0.3));
        assertEquals(2, plan.phaseFor(0.2));
        BossBrain brain = new BossBrain(context(boss, 1), plan);
        boss.setHealth(60);
        brain.tick();
        assertEquals(0, brain.phase(), "60% is still above this boss's first threshold");
    }

    @Test
    void aPlanNeedsOnePhasePerBand() {
        assertThrows(IllegalArgumentException.class, () -> new PhasePlan(List.of(
                phase("one", 5, BossBrainTest::noHook, fake("a", 1)))));
    }

    @Test
    void aPhaseWithNoAttacksStartsNothingButStillFiresItsTransitionHook() {
        LivingEntity boss = boss();
        List<String> hooks = new ArrayList<>();
        PhasePlan plan = new PhasePlan(List.of(
                phase("one", 5, BossBrainTest::noHook),
                phase("two", 5, c -> hooks.add("two")),
                phase("three", 5, c -> hooks.add("three"), fake("late", 1))));
        BossBrain brain = new BossBrain(context(boss, 1), plan);

        run(brain, 200);
        assertTrue(log.isEmpty(), "an empty pool means the boss just walks and melees");
        assertTrue(brain.currentAttackId().isEmpty());

        boss.setHealth(60);
        run(brain, 200);
        assertEquals(List.of("two"), hooks);
        assertTrue(log.isEmpty());

        boss.setHealth(20);
        run(brain, 50);
        assertEquals(List.of("two", "three"), hooks);
        assertTrue(log.contains("late"), "a later phase with attacks still runs them");
    }

    @Test
    void theAnimatorIsTickedEveryTickWhileTheFightRuns() {
        LivingEntity boss = boss();
        FightContext ctx = context(boss, 1);
        int[] ticks = {0};
        ctx.animator(new Animator() {
            @Override
            public void play(String pose, int duration) {
            }

            @Override
            public void tick() {
                ticks[0]++;
            }
        });
        BossBrain brain = new BossBrain(ctx, plan(fake("a", 1), fake("b", 1), fake("c", 1)));

        run(brain, 7);

        assertEquals(7, ticks[0]);
    }

    @Test
    void thePhaseIsKeptOnTheBossAndARebuiltBrainResumesItWithoutRefiring() {
        LivingEntity boss = boss();
        List<String> hooks = new ArrayList<>();
        PhasePlan plan = new PhasePlan(List.of(
                phase("one", 5, c -> hooks.add("one"), fake("a", 1)),
                phase("two", 5, c -> hooks.add("two"), fake("b", 1)),
                phase("three", 5, c -> hooks.add("three"), fake("c", 1))));
        BossBrain first = new BossBrain(context(boss, 1), plan);
        boss.setHealth(30);
        first.tick();
        assertEquals(List.of("two", "three"), hooks);
        assertEquals(2, boss.getPersistentDataContainer()
                .get(EngineKeys.phase(plugin), PersistentDataType.INTEGER).intValue());

        // A restart: the brain is gone, the boss and its persistent data are not.
        hooks.clear();
        BossBrain resumed = new BossBrain(context(boss, 1), plan);
        assertEquals(2, resumed.phase());
        run(resumed, 50);
        assertEquals(List.of(), hooks, "a resumed brain must not replay the transitions it already made");
        assertEquals(Set.of("c"), new HashSet<>(log), "and it fights with phase three's pool");
    }

    @Test
    void withNoEligibleFighterInsideTheAreaTheRunningAttackIsCancelledAndNothingStarts() {
        BossBrain brain = new BossBrain(context(boss(), 1), plan(fake("slam", 200), fake("b", 1), fake("c", 1)));
        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 5);
        assertEquals(List.of("slam"), log);
        assertTrue(brain.currentAttackId().isPresent());

        alice.teleport(at(80, 64, 80)); // out of the arena
        brain.tick();
        assertEquals(List.of("slam"), cancelled, "the attack in progress is cancelled");
        assertTrue(brain.currentAttackId().isEmpty());

        run(brain, 200);
        assertEquals(List.of("slam"), log, "nothing new starts while the arena is empty");
    }

    @Test
    void theBossResumesAttackingWhenAFighterReturns() {
        BossBrain brain = new BossBrain(context(boss(), 1), plan(fake("slam", 3), fake("b", 1), fake("c", 1)));
        alice.teleport(at(80, 64, 80));
        run(brain, 100);
        assertEquals(List.of(), log);

        alice.teleport(at(15, 64, 20));
        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 3);
        assertFalse(log.isEmpty(), "a fighter back inside gets attacked");
    }

    @Test
    void onlyOnlineAliveSurvivalOrAdventurePlayersInsideTheAreaCountAsFighters() {
        FightContext ctx = context(boss(), 1);
        PlayerMock creative = player("Creative", 10, 64, 10);
        creative.setGameMode(GameMode.CREATIVE);
        PlayerMock spectator = player("Spectator", 11, 64, 10);
        spectator.setGameMode(GameMode.SPECTATOR);
        PlayerMock adventure = player("Adventure", 12, 64, 10);
        adventure.setGameMode(GameMode.ADVENTURE);
        player("Outside", 50, 64, 50);

        assertEquals(Set.of("Alice", "Adventure"),
                ctx.fighters().stream().map(p -> p.getName()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void aQuitCancelsAnAttackAimedOnlyAtThatPlayer() {
        PlayerMock bob = player("Bob", 10, 64, 10);
        BossBrain brain = new BossBrain(context(boss(), 1),
                plan(fake("lockOn", 200, 0, Set.of(bob.getUniqueId())), fake("b", 1), fake("c", 1)));
        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 3);
        assertTrue(brain.currentAttackId().isPresent());

        brain.playerLeft(alice.getUniqueId());
        assertTrue(brain.currentAttackId().isPresent(), "Alice was not the target");

        brain.playerLeft(bob.getUniqueId());
        assertTrue(brain.currentAttackId().isEmpty());
        assertEquals(List.of("lockOn"), cancelled);
    }

    @Test
    void anAttackAimedAtEveryoneSurvivesOneFighterQuitting() {
        BossBrain brain = new BossBrain(context(boss(), 1), plan(fake("sweep", 200), fake("b", 1), fake("c", 1)));
        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 3);
        brain.playerLeft(alice.getUniqueId());
        assertTrue(brain.currentAttackId().isPresent());
    }

    @Test
    void ticksEndTheBrainOnceTheBossIsGone() {
        LivingEntity boss = boss();
        BossBrain brain = new BossBrain(context(boss, 1), plan(fake("slam", 50), fake("b", 1), fake("c", 1)));
        run(brain, PhasePlan.DEFAULT_OPENING_GAP_TICKS + 3);
        boss.remove();

        assertFalse(brain.tick());
        assertTrue(brain.ended());
        assertEquals(List.of("slam"), cancelled, "the attack in progress is cancelled when the boss goes");
    }

    @Test
    void aTickerKeepsRunningBetweenAttacksAndWithNobodyInsideThenClosesWhenTheFightEnds() {
        FightContext ctx = context(boss(), 1);
        BossBrain brain = new BossBrain(ctx, plan(fake("slam", 10), fake("b", 10), fake("c", 10)));
        int[] ticked = {0};
        boolean[] closed = {false};
        ctx.addTicker(new FightTicker() {
            @Override
            public boolean tick() {
                ticked[0]++;
                return false;
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        });
        alice.teleport(at(100, 64, 100)); // out of the area: no fighters, so no attack ever runs

        run(brain, 30);

        assertEquals(30, ticked[0], "ticked every brain tick, fighters or not");
        assertFalse(closed[0]);
        brain.end();
        assertTrue(closed[0]);
    }

    @Test
    void aTickerThatReportsDoneIsDroppedAndClosedOnce() {
        FightContext ctx = context(boss(), 1);
        BossBrain brain = new BossBrain(ctx, plan(fake("slam", 10), fake("b", 10), fake("c", 10)));
        int[] ticked = {0};
        int[] closed = {0};
        ctx.addTicker(new FightTicker() {
            @Override
            public boolean tick() {
                return ++ticked[0] == 3;
            }

            @Override
            public void close() {
                closed[0]++;
            }
        });

        run(brain, 10);
        brain.end();

        assertEquals(3, ticked[0]);
        assertEquals(1, closed[0]);
    }

    @Test
    void perFightStateIsCreatedOnceAndForgottenWhenTheFightEnds() {
        FightContext ctx = context(boss(), 1);
        BossBrain brain = new BossBrain(ctx, plan(fake("slam", 10), fake("b", 10), fake("c", 10)));
        int[] made = {0};

        Object first = ctx.state(StringBuilder.class, c -> {
            made[0]++;
            return new StringBuilder();
        });
        Object second = ctx.state(StringBuilder.class, c -> {
            made[0]++;
            return new StringBuilder();
        });
        brain.end();
        Object afterEnd = ctx.state(StringBuilder.class, c -> new StringBuilder());

        assertTrue(first == second);
        assertEquals(1, made[0]);
        assertFalse(first == afterEnd);
    }
}

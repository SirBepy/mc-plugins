package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegraphedStrikeTest extends EngineTestBase {

    private final List<List<Player>> landed = new ArrayList<>();

    private TelegraphedStrike strikeAt(FightContext ctx, Telegraph telegraph) {
        return TelegraphedStrike.start(ctx, telegraph, landed::add);
    }

    private static Set<String> names(List<Player> players) {
        return players.stream().map(Player::getName).collect(Collectors.toSet());
    }

    @Test
    void theStrikeWarnsForTheWholeWindowThenLandsExactlyOnce() {
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 20));

        for (int i = 0; i < 19; i++) {
            assertFalse(strike.tick(), "still warning at tick " + (i + 1));
        }
        assertTrue(landed.isEmpty(), "nothing may land before the warning window is over");
        assertTrue(strike.tick());
        assertEquals(1, landed.size());
        assertEquals(Set.of("Alice"), names(landed.get(0)));
        assertTrue(strike.tick(), "a finished strike stays finished and lands nothing more");
        assertEquals(1, landed.size());
    }

    @Test
    void theOutlineIsDrawnFromTheFirstTickAndEveryTwoTicksAfter() {
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 10));
        assertFalse(effects.dust.isEmpty(), "the first frame is drawn the moment the warning starts");

        int afterStart = effects.dust.size();
        strike.tick(); // tick 1: no frame
        assertEquals(afterStart, effects.dust.size());
        strike.tick(); // tick 2: frame
        int afterTwo = effects.dust.size();
        assertTrue(afterTwo > afterStart);
        strike.tick();
        assertEquals(afterTwo, effects.dust.size());
        strike.tick();
        assertTrue(effects.dust.size() > afterTwo);
    }

    @Test
    void aSecondBrighterOutlineClosesInSoPlayersCanReadTheTimeLeft() {
        FightContext ctx = context(boss(), 1);
        Telegraph telegraph = Telegraph.of(new Shape.Circle(at(15, 64, 20), 8), 20);
        TelegraphedStrike strike = strikeAt(ctx, telegraph);
        for (int i = 0; i < 10; i++) {
            strike.tick();
        }
        Color base = telegraph.color();
        double halfwayReach = effects.dust.stream().filter(d -> !d.color().equals(base))
                .mapToDouble(d -> Math.hypot(d.x() - 15, d.z() - 20)).max().orElse(-1);
        assertEquals(4.0, halfwayReach, 0.1, "halfway through the warning the bright edge is halfway out");
        assertTrue(effects.dust.stream().anyMatch(d -> d.color().equals(base)), "the finished shape stays drawn too");
    }

    @Test
    void theStartAndResolveSoundsPlay() {
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 4)
                .withSounds(Sound.ENTITY_RAVAGER_ROAR, Sound.ENTITY_GENERIC_EXPLODE));
        assertEquals(List.of(Sound.ENTITY_RAVAGER_ROAR), effects.sounds);
        while (!strike.tick()) {
            // run out the warning
        }
        assertEquals(List.of(Sound.ENTITY_RAVAGER_ROAR, Sound.ENTITY_GENERIC_EXPLODE), effects.sounds);
    }

    @Test
    void aGroundedStrikeSkipsAPlayerWhoIsAirborneWhenItLands() {
        PlayerMock bob = player("Bob", 15, 64, 21);
        airborne.add(bob.getUniqueId());
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx,
                Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 4).withGrounded(true));
        while (!strike.tick()) {
            // run out the warning
        }
        assertEquals(Set.of("Alice"), names(landed.get(0)), "Bob jumped the strike");
    }

    @Test
    void anUngroundedStrikeHitsAJumperAsWell() {
        airborne.add(alice.getUniqueId());
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 4));
        while (!strike.tick()) {
            // run out the warning
        }
        assertEquals(Set.of("Alice"), names(landed.get(0)));
    }

    @Test
    void aPlayerWhoLeftTheShapeBeforeItLandsIsNotHit() {
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 3), 10));
        for (int i = 0; i < 5; i++) {
            strike.tick();
        }
        alice.teleport(at(25, 64, 25));
        while (!strike.tick()) {
            // run out the warning
        }
        assertTrue(landed.get(0).isEmpty());
    }

    @Test
    void aStrikeNearTheEdgeIsClippedNothingIsDrawnOrHitOutsideTheArea() {
        PlayerMock outside = player("Outside", 31.5, 64, 15);
        alice.teleport(at(28, 64, 15));
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(29, 64, 15), 6), 6));
        while (!strike.tick()) {
            // run out the warning
        }
        FightArea area = area();
        assertFalse(effects.dust.isEmpty());
        for (var dust : effects.dust) {
            assertTrue(area.contains(at(dust.x(), 64, dust.z())), "drew outside the area at " + dust);
        }
        assertTrue(effects.dust.stream().anyMatch(d -> d.x() > 28.0), "the inside part is still drawn");
        assertEquals(Set.of("Alice"), names(landed.get(0)), outside.getName() + " stood in the circle but outside the area");
    }

    @Test
    void aCancelledStrikeStopsDrawingAndLandsNothing() {
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 6));
        strike.tick();
        strike.cancel();
        int drawn = effects.dust.size();
        for (int i = 0; i < 10; i++) {
            assertTrue(strike.tick());
        }
        assertEquals(drawn, effects.dust.size());
        assertTrue(landed.isEmpty());
    }

    @Test
    void creativeAndSpectatorPlayersAreNeverStruck() {
        PlayerMock creative = player("Creative", 15, 64, 19);
        creative.setGameMode(GameMode.CREATIVE);
        PlayerMock spectator = player("Spectator", 16, 64, 19);
        spectator.setGameMode(GameMode.SPECTATOR);
        PlayerMock adventure = player("Adventure", 14, 64, 19);
        adventure.setGameMode(GameMode.ADVENTURE);
        FightContext ctx = context(boss(), 1);
        TelegraphedStrike strike = strikeAt(ctx, Telegraph.of(new Shape.Circle(at(15, 64, 20), 4), 3));
        while (!strike.tick()) {
            // run out the warning
        }
        assertEquals(Set.of("Alice", "Adventure"), names(landed.get(0)));
    }

    @Test
    void aStrikeSequenceLaunchesStepsOnTheirTicksAndFinishesWithTheLastStrike() {
        FightContext ctx = context(boss(), 1);
        List<Integer> launchedAt = new ArrayList<>();
        int[] clock = {0};
        StrikeSequence sequence = new StrikeSequence(ctx, List.of(
                new StrikeSequence.Step(0, c -> {
                    launchedAt.add(clock[0]);
                    return TelegraphedStrike.start(c, Telegraph.of(new Shape.Circle(at(15, 64, 20), 3), 4), landed::add);
                }),
                new StrikeSequence.Step(6, c -> {
                    launchedAt.add(clock[0]);
                    return TelegraphedStrike.start(c, Telegraph.of(new Shape.Circle(at(15, 64, 20), 3), 4), landed::add);
                })));
        boolean done = false;
        for (clock[0] = 1; clock[0] <= 30 && !done; clock[0]++) {
            done = sequence.tick();
        }
        assertEquals(List.of(0, 6), launchedAt);
        assertEquals(2, landed.size());
        assertTrue(done);
    }

    @Test
    void cancellingAStrikeSequenceStopsStrikesStillWarning() {
        FightContext ctx = context(boss(), 1);
        StrikeSequence sequence = new StrikeSequence(ctx, List.of(new StrikeSequence.Step(0,
                c -> TelegraphedStrike.start(c, Telegraph.of(new Shape.Circle(at(15, 64, 20), 3), 10), landed::add)),
                new StrikeSequence.Step(8, c -> TelegraphedStrike.start(c,
                        Telegraph.of(new Shape.Circle(at(15, 64, 20), 3), 10), landed::add))));
        sequence.tick();
        sequence.cancel();
        for (int i = 0; i < 30; i++) {
            sequence.tick();
        }
        assertTrue(landed.isEmpty());
    }

    @Test
    void damageIsAttributedToTheBossAsBothDirectAndCausingEntity() {
        LivingEntity boss = boss();
        FightContext ctx = context(boss, 1);

        var source = ctx.sourceOf(DamageType.MOB_ATTACK);

        assertEquals(boss, source.getCausingEntity(), "the death message and damage listeners must see the Guardian");
        assertEquals(boss, source.getDirectEntity());
        assertEquals(DamageType.MOB_ATTACK, source.getDamageType());
        assertEquals(1.0, ctx.healthFraction(), 1.0e-9);
    }
}

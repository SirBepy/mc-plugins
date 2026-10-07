package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelTestBase;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallManagerTest extends CartelTestBase {

    private static double horizontal(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    @Test
    void aHitBroadcastsTheWarningAndSpawnsBothCousinsTenBlocksFromTheTarget() {
        String reply = calls.hit(owner, victim);

        assertTrue(reply.contains("on their way"), reply);
        assertEquals(CallManager.Mode.HIT, calls.mode());
        List<Mob> pair = calls.cousins();
        assertEquals(2, pair.size());
        for (Mob cousin : pair) {
            assertTrue(cousins.isCousin(cousin));
            double d = horizontal(cousin.getLocation(), victim.getLocation());
            assertTrue(d >= 9 && d <= 11, "cousin spawned " + d + " blocks from the target");
            assertEquals(victim, cousin.getTarget());
        }
        assertTrue(chat(victim).contains("Victim, you have made enemies of the cartel."));
        assertTrue(chat(owner).contains("Victim, you have made enemies of the cartel."));
    }

    @Test
    void aHitStartsTheThirtySixHourCooldown() {
        calls.hit(owner, victim);

        assertEquals(Duration.ofHours(36), calls.hitWait(owner.getUniqueId()));
        calls.end(null);
        now[0] += Duration.ofHours(35).toMillis();
        assertTrue(calls.hit(owner, victim).startsWith("Assassinate Mode is ready again in 1h"));
        assertFalse(calls.active());

        now[0] += Duration.ofHours(1).toMillis();
        calls.hit(owner, victim);
        assertTrue(calls.active());
    }

    @Test
    void onlyOneCallAtATime() {
        calls.guard(owner);

        assertEquals("The cousins are already out. Call them off first.", calls.hit(owner, victim));
        assertEquals(CallManager.Mode.GUARD, calls.mode());
        assertEquals(Duration.ZERO, calls.hitWait(owner.getUniqueId()), "a refused hit spends nothing");
    }

    @Test
    void theOwnerAndUnreachablePlayersCantBeTargeted() {
        assertEquals("The cousins won't come for you.", calls.hit(owner, owner));
        victim.setGameMode(GameMode.CREATIVE);
        assertTrue(calls.hit(owner, victim).contains("can't be reached"));

        assertFalse(calls.active());
        assertEquals(Duration.ZERO, calls.hitWait(owner.getUniqueId()));
    }

    @Test
    void nowhereToStandSpendsNoCooldown() {
        victim.teleport(new Location(world, 500.5, FLOOR_Y + 40, 500.5));

        assertTrue(calls.hit(owner, victim).contains("can't find a way"));

        assertFalse(calls.active());
        assertEquals(Duration.ZERO, calls.hitWait(owner.getUniqueId()));
    }

    @Test
    void theTargetsDeathEndsTheHitAndTheCousinsVanish() {
        calls.hit(owner, victim);
        List<Mob> pair = calls.cousins();

        calls.onPlayerDeath(victim, pair.getFirst());

        assertFalse(calls.active());
        assertTrue(pair.stream().allMatch(cousin -> !cousin.isValid()));
        assertTrue(chat(owner).stream().anyMatch(line -> line.startsWith("The job is done.")));
    }

    @Test
    void aKillByTheCousinsGivesTheBossTheTargetsHead() {
        calls.hit(owner, victim);

        calls.onPlayerDeath(victim, calls.cousins().getFirst());

        assertEquals(1, heads(owner, "Victim"), "the head's own shape is Trophies' job, covered in TrophiesTest");
    }

    @Test
    void anyOtherDeathOfTheTargetEndsTheHitWithoutAHead() {
        calls.hit(owner, victim);

        calls.onPlayerDeath(victim, null);
        assertFalse(calls.active());
        assertEquals(0, heads(owner, "Victim"));

        store.setHitReadyAt(owner.getUniqueId(), 0);
        calls.hit(owner, victim);
        calls.onPlayerDeath(victim, owner);
        assertEquals(0, heads(owner, "Victim"), "killing the target yourself earns no trophy");
    }

    @Test
    void aGuardKillEarnsNoHead() {
        calls.guard(owner);

        calls.onPlayerDeath(victim, calls.cousins().getFirst());

        assertEquals(0, heads(owner, "Victim"));
    }

    @Test
    void theTargetLoggingOutEndsTheHit() {
        calls.hit(owner, victim);

        victim.disconnect();
        calls.tick();

        assertFalse(calls.active());
    }

    @Test
    void theHitEndsWhenTimeRunsOut() {
        calls.hit(owner, victim);
        now[0] += config.hitLength().toMillis() - 1;
        calls.tick();
        assertTrue(calls.active());

        now[0] += 1;
        calls.tick();
        assertFalse(calls.active());
    }

    @Test
    void outrunningTheCousinsMakesThemPickUpTheTrailTwiceThenGiveUp() {
        calls.hit(owner, victim);
        floor(200, 0, 15);
        floor(400, 0, 15);
        floor(600, 0, 15);

        victim.teleport(new Location(world, 200.5, FLOOR_Y, 0.5));
        calls.tick();
        assertTrue(calls.active());
        for (Mob cousin : calls.cousins()) {
            double d = horizontal(cousin.getLocation(), victim.getLocation());
            assertTrue(d >= 9 && d <= 11, "cousin re-appeared " + d + " blocks away");
        }

        victim.teleport(new Location(world, 400.5, FLOOR_Y, 0.5));
        calls.tick();
        assertTrue(calls.active());

        victim.teleport(new Location(world, 600.5, FLOOR_Y, 0.5));
        calls.tick();
        assertFalse(calls.active(), "the trail is lost after " + config.retracks() + " retracks");
    }

    @Test
    void killingBothCousinsEndsTheCall() {
        calls.hit(owner, victim);
        calls.cousins().forEach(Mob::remove);

        calls.tick();

        assertFalse(calls.active());
    }

    @Test
    void inAHitTheCousinsMayOnlyTargetTheVictim() {
        calls.hit(owner, victim);
        Mob cousin = calls.cousins().getFirst();
        Zombie bystanderMob = world.spawn(new Location(world, 1, FLOOR_Y, 1), Zombie.class);

        assertTrue(calls.allowedTarget(cousin, victim));
        assertFalse(calls.allowedTarget(cousin, owner));
        assertFalse(calls.allowedTarget(cousin, bystanderMob));
        assertTrue(calls.allowedTarget(cousin, null));
    }

    @Test
    void onGuardTheCousinsFightMonstersAndWhoeverHurtTheOwnerButNotOtherPlayers() {
        String reply = calls.guard(owner);
        assertTrue(reply.startsWith("Leonel and Marco are here."), reply);
        assertEquals(Duration.ofHours(2), calls.guardWait(owner.getUniqueId()));
        Mob cousin = calls.cousins().getFirst();
        Zombie monster = world.spawn(new Location(world, 4, FLOOR_Y, 4), Zombie.class);

        assertTrue(calls.allowedTarget(cousin, monster));
        assertFalse(calls.allowedTarget(cousin, victim), "a player who did nothing is left alone");
        assertFalse(calls.allowedTarget(cousin, owner));
        assertFalse(calls.allowedTarget(cousin, calls.cousins().get(1)));

        calls.noteAttack(owner, victim);
        assertTrue(calls.allowedTarget(cousin, victim), "a player who hurt the owner is fair game");

        now[0] += CallManager.ATTACKER_MEMORY_MILLIS + 1;
        calls.tick();
        assertFalse(calls.allowedTarget(cousin, victim), "for " + CallManager.ATTACKER_MEMORY_MILLIS + " ms");
    }

    private Material marcosHand() {
        return calls.cousins().get(CallManager.MARCO).getEquipment().getItemInMainHand().getType();
    }

    private void tickAt(long millisIntoTheHit, long start) {
        now[0] = start + millisIntoTheHit;
        calls.tick();
    }

    @Test
    void marcoDrawsThePistolOnlyAfterThreeSecondsWithNoMeleePath() {
        calls.hit(owner, victim);
        long start = now[0];
        meleePath[0] = false;

        tickAt(0, start);
        tickAt(Sidearm.DRAW_AFTER_MILLIS - 1, start);
        assertTrue(shots.isEmpty());
        assertFalse(calls.pistolDrawn());

        tickAt(Sidearm.DRAW_AFTER_MILLIS, start);
        assertTrue(calls.pistolDrawn());
        assertEquals(Material.BOW, marcosHand());
        assertEquals(List.of("Marco Salamanca -> Victim"), shots, "only Marco shoots");
        assertEquals(List.of("Marco Salamanca"), standOffs, "Marco keeps his distance while the pistol is out");
        assertTrue(calls.status().contains("Marco's pistol out"), calls.status());
    }

    @Test
    void marcoShootsAtMostOnceEveryOneAndAHalfSeconds() {
        calls.hit(owner, victim);
        long start = now[0];
        meleePath[0] = false;

        for (long t = 0; t <= Sidearm.DRAW_AFTER_MILLIS + 3_000; t += 500) {
            tickAt(t, start);
        }

        assertEquals(3, shots.size(), "shots at 3 s, 4.5 s and 6 s: " + shots);
    }

    @Test
    void aMeleePathOpeningPutsThePistolAwayAndRestartsTheWait() {
        calls.hit(owner, victim);
        long start = now[0];
        meleePath[0] = false;
        tickAt(0, start);
        tickAt(Sidearm.DRAW_AFTER_MILLIS, start);
        assertEquals(Material.BOW, marcosHand());

        meleePath[0] = true;
        tickAt(Sidearm.DRAW_AFTER_MILLIS + 500, start);
        assertEquals(Material.NETHERITE_SWORD, marcosHand());
        assertFalse(calls.pistolDrawn());

        meleePath[0] = false;
        tickAt(Sidearm.DRAW_AFTER_MILLIS + 1_000, start);
        tickAt(2 * Sidearm.DRAW_AFTER_MILLIS + 500, start);
        assertFalse(calls.pistolDrawn(), "the 3 s count starts over");
        tickAt(2 * Sidearm.DRAW_AFTER_MILLIS + 1_000, start);
        assertTrue(calls.pistolDrawn());
        assertEquals(1, standOffs.size(), "the stand-off is attached once and follows drawn() after that");
    }

    @Test
    void aTargetOutOfBowRangeIsChasedNotShot() {
        calls.hit(owner, victim);
        long start = now[0];
        meleePath[0] = false;
        floor(5, 40, 3);
        victim.teleport(new Location(world, 5.5, FLOOR_Y, 40.5));

        for (long t = 0; t <= 2 * Sidearm.DRAW_AFTER_MILLIS; t += 500) {
            tickAt(t, start);
        }

        assertTrue(calls.active());
        assertFalse(calls.pistolDrawn());
        assertTrue(shots.isEmpty());
    }

    @Test
    void guardModeNeverDrawsThePistol() {
        calls.guard(owner);
        long start = now[0];
        meleePath[0] = false;

        for (long t = 0; t <= 2 * Sidearm.DRAW_AFTER_MILLIS; t += 500) {
            tickAt(t, start);
        }

        assertFalse(calls.pistolDrawn());
        assertTrue(shots.isEmpty());
        assertTrue(calls.cousins().stream().noneMatch(c -> c.getEquipment().getItemInMainHand().getType() == Material.BOW));
    }

    @Test
    void zombiesOutsideACallMayTargetNothing() {
        Zombie stray = world.spawn(new Location(world, 4, FLOOR_Y, 4), Zombie.class);
        cousins.mark(stray);

        assertFalse(calls.allowedTarget(stray, victim));
        assertNull(calls.mode());
    }
}

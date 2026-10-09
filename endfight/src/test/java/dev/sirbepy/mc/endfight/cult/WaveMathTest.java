package dev.sirbepy.mc.endfight.cult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveMathTest {

    private static final List<Double> THRESHOLDS = List.of(0.75, 0.5, 0.25);

    @Test
    void eachThresholdFiresOnlyOnce() {
        Set<Integer> crossed = new HashSet<>();

        List<Integer> first = WaveMath.newlyCrossedIndices(200, 140, 200, THRESHOLDS, crossed);
        assertEquals(List.of(0), first, "140/200 = 0.7, crossed the 0.75 threshold");
        crossed.addAll(first);

        List<Integer> second = WaveMath.newlyCrossedIndices(140, 139, 200, THRESHOLDS, crossed);
        assertTrue(second.isEmpty(), "still above 0.5 and 0.75 already spent");
    }

    @Test
    void oneBigHitCrossesEveryThresholdInBetween() {
        Set<Integer> crossed = new HashSet<>();

        List<Integer> newly = WaveMath.newlyCrossedIndices(200, 20, 200, THRESHOLDS, crossed);

        assertEquals(List.of(0, 1, 2), newly);
    }

    @Test
    void aHealedDragonNeverRefiresASpentThreshold() {
        Set<Integer> crossed = new HashSet<>();
        crossed.addAll(WaveMath.newlyCrossedIndices(200, 90, 200, THRESHOLDS, crossed));
        // Healed back above 0.75 (150/200), then hit back down through it again.
        List<Integer> afterHealing = WaveMath.newlyCrossedIndices(160, 90, 200, THRESHOLDS, crossed);

        assertTrue(afterHealing.isEmpty(), "0.75 was already spent for this dragon");
    }

    @Test
    void aDatapackRaisedMaxHealthIsRespected() {
        // 400 max health (double vanilla): 301/400 = 0.7525 (just above 0.75), 250/400 = 0.625
        // (between 0.75 and 0.5, so only the first threshold is crossed by this hit).
        List<Integer> newly = WaveMath.newlyCrossedIndices(301, 250, 400, THRESHOLDS, Set.of());

        assertEquals(List.of(0), newly);
    }

    private static List<String> types(List<WaveMath.PlannedSpawn> spawns) {
        return spawns.stream().map(WaveMath.PlannedSpawn::type).toList();
    }

    @Test
    void waveSizeScalesWithPlayerCount() {
        List<WaveMath.WaveEntry> entries = List.of(
                new WaveMath.WaveEntry("pillager", 4, 1),
                new WaveMath.WaveEntry("vindicator", 2, 1));

        List<WaveMath.PlannedSpawn> soloWave = WaveMath.plan(entries, 1, List.of(), 0, 60);
        List<WaveMath.PlannedSpawn> threePlayerWave = WaveMath.plan(entries, 3, List.of(), 0, 60);

        assertEquals(8, soloWave.size(), "4+1 pillagers, 2+1 vindicators");
        assertEquals(12, threePlayerWave.size(), "4+3 pillagers, 2+3 vindicators");
    }

    @Test
    void perNPlayersAddsOnlyOncePerNPlayers() {
        WaveMath.WaveEntry ravager = new WaveMath.WaveEntry("ravager", 1, 3);

        assertEquals(1, ravager.count(2), "fewer than 3 players: still just the base");
        assertEquals(2, ravager.count(3), "+1 at exactly 3 players");
        assertEquals(3, ravager.count(6), "+1 more at 6 players");
    }

    @Test
    void theMaxAliveCapTrimsTheSpawnListInCompositionOrder() {
        List<WaveMath.WaveEntry> entries = List.of(
                new WaveMath.WaveEntry("pillager", 4, 0),
                new WaveMath.WaveEntry("vindicator", 4, 0));

        List<WaveMath.PlannedSpawn> capped = WaveMath.plan(entries, 0, List.of(), 55, 60);

        assertEquals(5, capped.size(), "60 - 55 alive = room for 5 more");
        assertEquals(List.of("pillager", "pillager", "pillager", "pillager", "vindicator"), types(capped));
    }

    @Test
    void noRoomLeftUnderTheCapSpawnsNothing() {
        List<WaveMath.WaveEntry> entries = List.of(new WaveMath.WaveEntry("pillager", 4, 0));

        assertTrue(WaveMath.plan(entries, 0, List.of(), 60, 60).isEmpty());
    }

    // Joe, 2026-10-09: 3 phantoms on each of the top 5-6 damage dealers.
    @Test
    void phantomsGoThreeToEachOfTheTopDamageDealersOnly() {
        List<UUID> damagers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            damagers.add(UUID.randomUUID());
        }
        WaveMath.WaveEntry phantoms = new WaveMath.WaveEntry("phantom", 3, 0, WaveMath.From.DAMAGERS, 6);

        List<WaveMath.PlannedSpawn> spawns = WaveMath.plan(List.of(phantoms), 8, damagers, 0, 150);

        assertEquals(18, spawns.size(), "3 each on the top 6 of 8");
        for (int t = 0; t < 6; t++) {
            UUID target = damagers.get(t);
            assertEquals(3, spawns.stream().filter(s -> target.equals(s.target())).count(), "3 on damager " + t);
        }
        assertTrue(spawns.stream().noneMatch(s -> damagers.get(6).equals(s.target()) || damagers.get(7).equals(s.target())),
                "nothing on the 7th and 8th");
    }

    @Test
    void fewerDamageDealersThanTheTopCountGetThreeEach() {
        List<UUID> damagers = List.of(UUID.randomUUID(), UUID.randomUUID());
        WaveMath.WaveEntry phantoms = new WaveMath.WaveEntry("phantom", 3, 0, WaveMath.From.DAMAGERS, 6);

        assertEquals(6, WaveMath.plan(List.of(phantoms), 2, damagers, 0, 150).size());
    }

    @Test
    void dragonEntriesKeepWhereTheyComeFrom() {
        List<WaveMath.WaveEntry> entries = List.of(
                new WaveMath.WaveEntry("pillager", 1, 0),
                new WaveMath.WaveEntry("piglin", 2, 0, WaveMath.From.DRAGON, 0));

        List<WaveMath.PlannedSpawn> spawns = WaveMath.plan(entries, 0, List.of(), 0, 150);

        assertEquals(List.of(WaveMath.From.GATES, WaveMath.From.DRAGON, WaveMath.From.DRAGON),
                spawns.stream().map(WaveMath.PlannedSpawn::from).toList());
    }

    @Test
    void aWaveComesOutOverTheSpreadNeverSlowerThanOneATick() {
        assertEquals(1, WaveMath.perTick(10, 50), "10 mobs over 50 ticks: one a tick");
        assertEquals(3, WaveMath.perTick(120, 50), "120 over 50 ticks: 3 a tick (40 ticks)");
        assertEquals(0, WaveMath.perTick(0, 50));
        assertEquals(5, WaveMath.perTick(5, 0), "no spread: all at once");
    }
}

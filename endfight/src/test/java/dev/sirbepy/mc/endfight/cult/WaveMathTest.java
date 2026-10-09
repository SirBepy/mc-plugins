package dev.sirbepy.mc.endfight.cult;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Test
    void waveSizeScalesWithPlayerCount() {
        List<WaveMath.WaveEntry> entries = List.of(
                new WaveMath.WaveEntry("pillager", 4, 1),
                new WaveMath.WaveEntry("vindicator", 2, 1));

        List<String> soloWave = WaveMath.spawnList(entries, 1, 0, 60);
        List<String> threePlayerWave = WaveMath.spawnList(entries, 3, 0, 60);

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

        List<String> capped = WaveMath.spawnList(entries, 0, 55, 60);

        assertEquals(5, capped.size(), "60 - 55 alive = room for 5 more");
        assertEquals(List.of("pillager", "pillager", "pillager", "pillager", "vindicator"), capped);
    }

    @Test
    void noRoomLeftUnderTheCapSpawnsNothing() {
        List<WaveMath.WaveEntry> entries = List.of(new WaveMath.WaveEntry("pillager", 4, 0));

        List<String> capped = WaveMath.spawnList(entries, 0, 60, 60);

        assertTrue(capped.isEmpty());
    }
}

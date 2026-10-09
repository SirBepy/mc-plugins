package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Pillager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CultCounterTest {

    private ServerMock server;
    private WorldMock end;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theTitleNamesTheTypesBiggestFirst() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("evoker", 1);
        counts.put("pillager", 18);
        counts.put(Paratroopers.TYPE, 4);
        counts.put("piglin_brute", 9);

        assertEquals("Dragon's army: 32 left (18 Pillagers, 9 Piglin Brutes, 4 Paratroopers, 1 Evoker)",
                CultCounter.title(counts));
    }

    @Test
    void moreThanFiveTypesAreCutShort() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : new String[] {"pillager", "vindicator", "evoker", "ravager", "piglin", "phantom"}) {
            counts.put(type, 2);
        }

        assertEquals("Dragon's army: 12 left (2 Evokers, 2 Phantoms, 2 Piglins, 2 Pillagers, 2 Ravagers, ...)",
                CultCounter.title(counts));
    }

    @Test
    void anUnknownTypeStillGetsAReadableName() {
        assertEquals("Wither Skeletons", CultCounter.name("wither_skeleton", 2));
        assertEquals("Wither Skeleton", CultCounter.name("wither_skeleton", 1));
    }

    @Test
    void countsOnlyCultMobsByTheTypeTheySpawnedAs() {
        Pillager guard = end.spawn(new Location(end, 0, 65, 0), Pillager.class);
        CultIdentity.apply(guard, "pillager");
        Pillager plain = end.spawn(new Location(end, 3, 65, 0), Pillager.class);

        Map<String, Integer> counts = CultCounter.count(end);

        assertEquals(Map.of("pillager", 1), counts);
        assertEquals(1, counts.values().stream().mapToInt(Integer::intValue).sum(), "the plain pillager " + plain + " isn't counted");
    }

    @Test
    void theBarShowsWhileTheDragonLivesAndTheArmyStandsThenGoes() {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(end, 10, 65, 10));
        EnderDragon dragon = end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
        Pillager guard = end.spawn(new Location(end, 0, 65, 0), Pillager.class);
        CultIdentity.apply(guard, "pillager");
        CultCounter counter = new CultCounter(MockBukkit.createMockPlugin(), () -> new CultConfig(null));

        counter.tick();
        assertEquals(1, countBars(player), "shown with a dragon alive and one cultist");

        guard.remove();
        counter.tick();
        assertEquals(0, countBars(player), "gone once the army is");

        CultIdentity.apply(end.spawn(new Location(end, 0, 65, 0), Pillager.class), "pillager");
        dragon.remove();
        counter.tick();
        assertEquals(0, countBars(player), "never without a dragon");
    }

    private static int countBars(PlayerMock player) {
        int n = 0;
        for (var ignored : player.activeBossBars()) {
            n++;
        }
        return n;
    }
}

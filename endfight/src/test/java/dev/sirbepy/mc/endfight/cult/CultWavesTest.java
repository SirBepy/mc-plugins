package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaLayout;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.Pillar;
import dev.sirbepy.mc.endfight.arena.PillarScanner;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CultWavesTest {

    private static final int SURFACE_Y = 64;

    private ServerMock server;
    private WorldMock end;
    private ArenaRegistry registry;
    private CultConfig config;
    private CultWaves waves;

    private static List<Pillar> samplePillars() {
        List<int[]> centers = PillarScanner.vanillaCenters();
        List<Pillar> pillars = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            pillars.add(new Pillar(centers.get(i)[0], centers.get(i)[1], 2 + i / 3, 76 + i * 3, SURFACE_Y));
        }
        return pillars;
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        registry = new ArenaRegistry();
        List<Pillar> pillars = samplePillars();
        registry.update(pillars, ArenaLayout.build(pillars, SURFACE_Y), true);
        config = new CultConfig(null);
        waves = new CultWaves(registry, () -> config, Logger.getLogger("endfight-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragonWithMaxHealth(double maxHealth) {
        EnderDragon dragon = end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
        dragon.registerAttribute(Attribute.MAX_HEALTH);
        var attribute = dragon.getAttribute(Attribute.MAX_HEALTH);
        attribute.setBaseValue(maxHealth);
        dragon.setHealth(maxHealth);
        return dragon;
    }

    private static int aliveCultCount(WorldMock world) {
        int count = 0;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            if (CultMarker.isCult(entity)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void crossingTheFirstThresholdSpawnsWave1sComposition() {
        EnderDragon dragon = dragonWithMaxHealth(200);
        dragon.setHealth(140); // post-hit health the real event flow would have already applied

        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 60));

        // wave 1 default, 0 players in range: 4 pillagers + 2 vindicators, no evoker/ravager yet.
        assertEquals(4, end.getEntitiesByClass(Pillager.class).stream().filter(CultMarker::isCult).count());
        assertEquals(2, end.getEntitiesByClass(Vindicator.class).stream().filter(CultMarker::isCult).count());
        assertEquals(0, end.getEntitiesByClass(Evoker.class).stream().filter(CultMarker::isCult).count());
        assertEquals(6, aliveCultCount(end));
    }

    // A full arena has more guards (one per post, ~95) than the wave cap, which must not swallow
    // every wave: the cap only counts wave mobs.
    @Test
    void guardsBeyondTheCapStillLeaveRoomForAWave() {
        for (int i = 0; i < 100; i++) {
            CultMarker.markGuard(end.spawn(new Location(end, 20, 65, 0), Pillager.class), i);
        }
        EnderDragon dragon = dragonWithMaxHealth(200);
        dragon.setHealth(140);

        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 60));

        assertEquals(106, aliveCultCount(end), "100 guards plus wave 1's 6 mobs");
    }

    @Test
    void aSecondHitThatDoesNotCrossAnotherThresholdSpawnsNothingMore() {
        EnderDragon dragon = dragonWithMaxHealth(200);
        dragon.setHealth(140);
        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 60));
        int afterFirstWave = aliveCultCount(end);

        dragon.setHealth(135);
        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5));

        assertEquals(afterFirstWave, aliveCultCount(end), "still above 0.5, no second wave yet");
    }

    @Test
    void droppingStraightToTheThirdThresholdFiresAllThreeWavesAtOnce() {
        EnderDragon dragon = dragonWithMaxHealth(200);
        dragon.setHealth(10); // 5%, straight past all 3 thresholds in one hit

        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 190));

        // wave1 (4p+2v) + wave2 (4p+2v+1e) + wave3 (4p+2v+1e+1r) = 12 pillagers, 6 vindicators, 2 evokers, 1 ravager
        assertEquals(12, end.getEntitiesByClass(Pillager.class).stream().filter(CultMarker::isCult).count());
        assertEquals(6, end.getEntitiesByClass(Vindicator.class).stream().filter(CultMarker::isCult).count());
        assertEquals(2, end.getEntitiesByClass(Evoker.class).stream().filter(CultMarker::isCult).count());
    }

    @Test
    void aZeroDamageHitNeverCountsAsCrossingAnything() {
        EnderDragon dragon = dragonWithMaxHealth(200);

        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0));

        assertEquals(0, aliveCultCount(end));
    }

    @Test
    void wavesDisabledInConfigSpawnsNothing() {
        config = new CultConfig(buildSection(false));
        waves = new CultWaves(registry, () -> config, Logger.getLogger("endfight-test"));
        EnderDragon dragon = dragonWithMaxHealth(200);
        dragon.setHealth(140);

        waves.onDamage(new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 60));

        assertEquals(0, aliveCultCount(end));
    }

    private static org.bukkit.configuration.ConfigurationSection buildSection(boolean wavesEnabled) {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("waves.enabled", wavesEnabled);
        return yaml;
    }
}

package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EntityMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaSteeringTest {

    @TempDir
    File dir;

    private ServerMock server;
    private WorldMock world;
    private KelpBeds beds;
    private Villager merfolk;
    private SeaSteering steering;
    private final List<Boolean> afloat = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        beds = new KelpBeds(new File(dir, "beds.yml"), Logger.getAnonymousLogger());
        merfolk = world.spawn(new Location(world, 0.5, 60, 0.5), Villager.class);
        Merfolk.make(merfolk);
        ((EntityMock) merfolk).setInWater(true);
        steering = new SeaSteering(beds, () -> List.of(merfolk),
                w -> new BlockTerrain(w, block -> !block.getType().isSolid()), (v, swimming) -> afloat.add(swimming), new Random(7));
    }

    /** A 21x21 pool, floor at y 59, water y 60-63, with the chunks around it loaded. */
    private void pool() {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                world.getBlockAt(x, 59, z).setType(Material.STONE);
                for (int y = 60; y <= 63; y++) world.getBlockAt(x, y, z).setType(Material.WATER);
            }
        }
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) world.loadChunk(cx, cz);
        }
    }

    private void wall(int x, int z1, int z2, int y1, int y2) {
        for (int z = z1; z <= z2; z++) {
            for (int y = y1; y <= y2; y++) world.getBlockAt(x, y, z).setType(Material.STONE);
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void atNightAMerfolkSwimsTowardsItsOwnKelpBed() {
        beds.claim(beds.add(world.getBlockAt(10, 60, 0), BlockFace.SOUTH), merfolk.getUniqueId());
        world.setTime(14000);

        steering.tick();

        assertTrue(merfolk.getVelocity().getX() > 0.1, "pushed east, towards the bed: " + merfolk.getVelocity());
        assertEquals(0, merfolk.getVelocity().getZ(), 0.05);
    }

    @Test
    void byDayAPickedJobSiteComesFirstAndWorkHoursSendItToItsJob() {
        world.setTime(5000);
        merfolk.setMemory(MemoryKey.JOB_SITE, new Location(world, 0, 60, -10));
        assertEquals(-9.5, steering.target(merfolk).getZ(), 1e-9);

        merfolk.setMemory(MemoryKey.POTENTIAL_JOB_SITE, new Location(world, 8, 60, 0));
        assertEquals(8.5, steering.target(merfolk).getX(), 1e-9);
    }

    @Test
    void outsideWorkHoursWithoutABedItIsLeftAlone() {
        world.setTime(10000);
        merfolk.setMemory(MemoryKey.JOB_SITE, new Location(world, 0, 60, -10));
        assertNull(steering.target(merfolk));
    }

    @Test
    void anUnemployedMerfolkIsPointedAtTheNearestFreeJobSite() {
        world.getBlockAt(4, 60, 0).setType(Material.BARREL);
        world.getBlockAt(6, 60, 0).setType(Material.STONECUTTER);
        Villager worker = world.spawn(new Location(world, 8.5, 60, 0.5), Villager.class);
        worker.setMemory(MemoryKey.JOB_SITE, new Location(world, 4, 60, 0));

        steering.findJobs();

        assertEquals(new Location(world, 6, 60, 0), merfolk.getMemory(MemoryKey.POTENTIAL_JOB_SITE),
                "the barrel is someone else's, so the stonecutter");
    }

    @Test
    void employedMerfolkAndMerfolkOnLandAreNotSentLooking() {
        world.getBlockAt(4, 60, 0).setType(Material.BARREL);
        merfolk.setProfession(Villager.Profession.FARMER);
        steering.findJobs();
        assertNull(merfolk.getMemory(MemoryKey.POTENTIAL_JOB_SITE));

        merfolk.setProfession(Villager.Profession.NONE);
        ((EntityMock) merfolk).setInWater(false);
        steering.findJobs();
        assertNull(merfolk.getMemory(MemoryKey.POTENTIAL_JOB_SITE));
    }

    @Test
    void onArrivalOrOnLandNothingPushes() {
        world.setTime(14000);
        beds.claim(beds.add(world.getBlockAt(1, 60, 0), BlockFace.SOUTH), merfolk.getUniqueId());
        steering.tick();
        assertEquals(0, merfolk.getVelocity().length(), 1e-9);

        beds.claim(beds.add(world.getBlockAt(20, 60, 0), BlockFace.SOUTH), merfolk.getUniqueId());
        ((EntityMock) merfolk).setInWater(false);
        steering.tick();
        assertEquals(0, merfolk.getVelocity().length(), 1e-9);
    }

    @Test
    void aWallInTheWayIsWalkedAroundInsteadOfPushedInto() {
        pool();
        // Out of the water and the whole way across except a gap at z 8..10.
        wall(3, -10, 7, 60, 64);
        beds.claim(beds.add(world.getBlockAt(8, 60, 0), BlockFace.SOUTH), merfolk.getUniqueId());
        world.setTime(14000);

        steering.tick();

        assertTrue(merfolk.getVelocity().getZ() > 0.05, "heading for the gap at z 8+: " + merfolk.getVelocity());
        assertTrue(afloat.stream().noneMatch(a -> a), "walking the floor, not swimming");
    }

    @Test
    void aSunkenWallAcrossTheWholeWayIsSwumOver() {
        pool();
        wall(2, -10, 10, 60, 61);
        merfolk.teleport(new Location(world, 1.5, 60, 0.5));
        beds.claim(beds.add(world.getBlockAt(8, 60, 0), BlockFace.SOUTH), merfolk.getUniqueId());
        world.setTime(14000);

        steering.tick();

        assertTrue(merfolk.getVelocity().getY() > 0.05, "pushed up to get over it: " + merfolk.getVelocity());
        assertEquals(List.of(true), afloat.subList(afloat.size() - 1, afloat.size()), "afloat while it swims up");
    }

    @Test
    void anIdleMerfolkWandersAroundTheSeaFloor() {
        pool();
        world.setTime(10000);
        double fastest = 0;
        for (int tick = 0; tick < SeaSteering.WANDER_MAX / SeaSteering.PERIOD_TICKS + 2; tick++) {
            steering.tick();
            fastest = Math.max(fastest, Math.hypot(merfolk.getVelocity().getX(), merfolk.getVelocity().getZ()));
        }
        assertTrue(fastest > 0.1, "it set off somewhere: " + fastest);
    }
}

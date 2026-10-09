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
import java.util.List;
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

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        beds = new KelpBeds(new File(dir, "beds.yml"), Logger.getAnonymousLogger());
        merfolk = world.spawn(new Location(world, 0.5, 60, 0.5), Villager.class);
        Merfolk.make(merfolk);
        ((EntityMock) merfolk).setInWater(true);
        steering = new SeaSteering(beds, () -> List.of(merfolk));
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
}

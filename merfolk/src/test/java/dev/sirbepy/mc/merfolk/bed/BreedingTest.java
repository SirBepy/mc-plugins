package dev.sirbepy.mc.merfolk.bed;

import dev.sirbepy.mc.merfolk.Merfolk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreedingTest {

    @TempDir
    File dir;

    private ServerMock server;
    private WorldMock world;
    private KelpBeds beds;
    private final List<Villager> merfolk = new ArrayList<>();
    private Breeding breeding;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        beds = new KelpBeds(new File(dir, "beds.yml"), Logger.getAnonymousLogger());
        breeding = new Breeding(beds, () -> List.copyOf(merfolk));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Villager merfolkAt(double x) {
        Villager villager = world.spawn(new Location(world, x, 60, 0), Villager.class);
        Merfolk.make(villager);
        merfolk.add(villager);
        return villager;
    }

    private Item thrown(Material type, int amount) {
        return world.dropItem(new Location(world, 0, 61, 0), new ItemStack(type, amount));
    }

    @Test
    void fishAndKelpAreMerfolkFoodBreadIsNot() {
        Villager villager = merfolkAt(0);

        assertEquals(0, Breeding.feed(villager, thrown(Material.BREAD, 3)));
        assertEquals(2, Breeding.feed(villager, thrown(Material.COD, 2)));
        assertEquals(1, Breeding.feed(villager, thrown(Material.TROPICAL_FISH, 1)));
        assertEquals(4, Breeding.feed(villager, thrown(Material.KELP, 4)));
        assertEquals(16, Breeding.food(villager));
        assertTrue(Breeding.willing(villager));
    }

    @Test
    void aMerfolkOnlyTakesWhatFitsAndLeavesTheRest() {
        Villager villager = merfolkAt(0);
        Item salmon = thrown(Material.SALMON, 20);

        assertEquals(9, Breeding.feed(villager, salmon));
        assertEquals(Breeding.FULL, Breeding.food(villager));
        assertEquals(11, salmon.getItemStack().getAmount());
    }

    @Test
    void homelessMerfolkClaimAFreeKelpBedAndOneBedHomesOne() {
        beds.add(world.getBlockAt(0, 60, 5), BlockFace.NORTH);
        Villager a = merfolkAt(0);
        Villager b = merfolkAt(1);

        breeding.tick();

        assertNotNull(beds.homeOf(a.getUniqueId()));
        assertNull(beds.homeOf(b.getUniqueId()), "one bed, one merfolk");
    }

    @Test
    void twoWillingMerfolkWithAFreeBedMakeAMerfolkBabyThatTakesTheBed() {
        beds.add(world.getBlockAt(0, 60, 5), BlockFace.NORTH);
        beds.add(world.getBlockAt(3, 60, 5), BlockFace.NORTH);
        beds.add(world.getBlockAt(6, 60, 5), BlockFace.NORTH);
        Villager mother = merfolkAt(0);
        Villager father = merfolkAt(1);
        Breeding.setFood(mother, 14);
        Breeding.setFood(father, 12);

        breeding.tick();

        List<Villager> babies = world.getEntitiesByClass(Villager.class).stream()
                .filter(v -> !v.isAdult()).toList();
        assertEquals(1, babies.size());
        Villager baby = babies.getFirst();
        assertTrue(Merfolk.is(baby));
        assertNotNull(beds.homeOf(baby.getUniqueId()));
        assertEquals(2, Breeding.food(mother));
        assertEquals(0, Breeding.food(father));
        assertEquals(Breeding.COOLDOWN_TICKS, mother.getAge());
        assertFalse(Breeding.willing(mother));
    }

    @Test
    void noFreeBedMeansNoBabyAndNoFoodSpent() {
        beds.add(world.getBlockAt(0, 60, 5), BlockFace.NORTH);
        beds.add(world.getBlockAt(3, 60, 5), BlockFace.NORTH);
        Villager mother = merfolkAt(0);
        Villager father = merfolkAt(1);
        Breeding.setFood(mother, 12);
        Breeding.setFood(father, 12);

        breeding.tick();

        assertEquals(2, world.getEntitiesByClass(Villager.class).size());
        assertEquals(12, Breeding.food(mother));
    }

    @Test
    void ordinaryVillagersFoodIsUntouched() {
        Villager ordinary = world.spawn(new Location(world, 0, 60, 0), Villager.class);
        assertFalse(Merfolk.is(ordinary));
        assertEquals(0, Breeding.food(ordinary));
    }
}

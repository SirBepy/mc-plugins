package dev.sirbepy.mc.merfolk.bed;

import dev.sirbepy.mc.merfolk.TownCheck;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KelpBedsTest {

    @TempDir
    File dir;

    private ServerMock server;
    private WorldMock world;
    private KelpBeds beds;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        beds = new KelpBeds(new File(dir, "beds.yml"), Logger.getAnonymousLogger());
        server.getPluginManager().registerEvents(new KelpBedListener(beds, TownCheck.NONE), MockBukkit.createMockPlugin());
        player = server.addPlayer();
        // Facing south (+z): yaw 0.
        player.teleport(new Location(world, 0.5, 61, -3, 0, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Sand floor at y=59, water at y=60 from z=0 to z=3. */
    private void seaFloor() {
        world.getChunkAt(0, 0).load();
        for (int z = 0; z <= 3; z++) {
            world.getBlockAt(0, 59, z).setType(Material.SAND);
            world.getBlockAt(0, 60, z).setType(Material.WATER);
        }
    }

    private PlayerInteractEvent rightClickFloor(int z) {
        player.getInventory().setItemInMainHand(KelpBeds.item(1));
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(),
                world.getBlockAt(0, 59, z), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void threeDriedKelpBlocksInARowMakeAKelpBed() {
        Recipe recipe = KelpBeds.recipe();
        assertTrue(recipe instanceof ShapedRecipe);
        ShapedRecipe shaped = (ShapedRecipe) recipe;
        assertEquals(1, shaped.getShape().length);
        assertEquals("KKK", shaped.getShape()[0]);
        assertEquals(Material.DRIED_KELP_BLOCK, shaped.getIngredientMap().get('K').getType());
        assertTrue(KelpBeds.isItem(shaped.getResult()));
    }

    @Test
    void placedOnTheSeaFloorItLiesInTwoWaterBlocksThatStayWater() {
        seaFloor();
        rightClickFloor(0);

        Block foot = world.getBlockAt(0, 60, 0);
        Block head = world.getBlockAt(0, 60, 1);
        KelpBed bed = beds.at(foot);
        assertNotNull(bed);
        assertEquals(bed, beds.at(head));
        assertEquals(BlockFace.SOUTH, bed.facing());
        assertEquals(Material.WATER, foot.getType());
        assertEquals(Material.WATER, head.getType());
        assertEquals(0, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(1, world.getEntitiesByClass(ItemDisplay.class).size());
        assertEquals(2, world.getEntitiesByClass(Interaction.class).size());
    }

    @Test
    void notOnDryLand() {
        world.getBlockAt(0, 59, 0).setType(Material.SAND);
        rightClickFloor(0);

        assertNull(beds.at(world.getBlockAt(0, 60, 0)));
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void bothHalvesMustBeWater() {
        seaFloor();
        world.getBlockAt(0, 60, 1).setType(Material.AIR);
        rightClickFloor(0);

        assertNull(beds.at(world.getBlockAt(0, 60, 0)));
    }

    @Test
    void itsWaterCannotBeBucketedAway() {
        seaFloor();
        rightClickFloor(0);
        Block foot = world.getBlockAt(0, 60, 0);
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, foot, world.getBlockAt(0, 59, 0), BlockFace.UP,
                Material.BUCKET, new ItemStack(Material.BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void bedsAndTheirResidentsSurviveARestart() {
        seaFloor();
        rightClickFloor(0);
        KelpBed bed = beds.at(world.getBlockAt(0, 60, 0));
        UUID resident = UUID.randomUUID();
        beds.claim(bed, resident);

        KelpBeds reloaded = new KelpBeds(new File(dir, "beds.yml"), Logger.getAnonymousLogger());
        reloaded.load();

        KelpBed again = reloaded.at(world.getBlockAt(0, 60, 1));
        assertNotNull(again);
        assertEquals(resident, again.resident());
        assertEquals(BlockFace.SOUTH, again.facing());
    }

    @Test
    void theModelsHeadEndFacesTheBedsHeadBlock() {
        assertEquals(0f, KelpBeds.yaw(BlockFace.SOUTH));
        assertEquals(180f, KelpBeds.yaw(BlockFace.NORTH));
        assertEquals(new Location(world, 0.5, 60, 1.0), beds.add(world.getBlockAt(0, 60, 0), BlockFace.SOUTH).centre(world));
    }
}

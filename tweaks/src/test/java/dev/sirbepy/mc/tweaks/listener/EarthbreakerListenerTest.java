package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthbreakerListenerTest {

    private static final int X = 10;
    private static final int Y = 100;
    private static final int Z = 10;

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private Plugin plugin;
    private EarthbreakerListener listener;
    private final List<Block> breakEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        // PlayerMock registers no BLOCK_BREAK_SPEED, and registerAttribute has no default to start it from.
        player = new PlayerMock(server, "dvolic21") {
            {
                attributes.put(Attribute.BLOCK_BREAK_SPEED, new AttributeInstanceMock(Attribute.BLOCK_BREAK_SPEED, 1.0));
            }
        };
        server.addPlayer(player);
        player.teleport(new Location(world, X + 0.5, Y + 2, Z + 0.5));
        plugin = MockBukkit.createMockPlugin("Tweaks");
        listener = new EarthbreakerListener(server);
        server.getPluginManager().registerEvents(listener, plugin);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void count(BlockBreakEvent event) {
                breakEvents.add(event.getBlock());
            }
        }, plugin);
        fill(Material.STONE);
        player.getInventory().setItemInMainHand(earthbreaker());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack earthbreaker() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(EarthbreakerListener.TAG, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    /** A 7x7x7 cube, one block wider than the 5x5 on every side. */
    private void fill(Material type) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) world.getBlockAt(X + dx, Y + dy, Z + dz).setType(type);
            }
        }
    }

    private Block at(int dx, int dy, int dz) {
        return world.getBlockAt(X + dx, Y + dy, Z + dz);
    }

    /** Starts digging the centre block on {@code face}, then breaks it the way the server does. */
    private void mine(BlockFace face) {
        Block center = at(0, 0, 0);
        server.getPluginManager().callEvent(new BlockDamageEvent(player, center, face, player.getInventory().getItemInMainHand(), false));
        player.breakBlock(center);
    }

    private int airCount() {
        int air = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) if (at(dx, dy, dz).getType() == Material.AIR) air++;
            }
        }
        return air;
    }

    @Test
    void miningTheTopFaceBreaksTheFlat5x5AroundIt() {
        mine(BlockFace.UP);

        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) assertEquals(Material.AIR, at(a, 0, b).getType(), "(" + a + ", " + b + ") broken");
        }
        assertEquals(25, airCount(), "nothing outside the flat 5x5");
    }

    @Test
    void miningASideFaceBreaksTheUpright5x5AcrossIt() {
        mine(BlockFace.NORTH);

        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) assertEquals(Material.AIR, at(a, b, 0).getType());
        }
        assertEquals(25, airCount());
    }

    @Test
    void miningAnEastOrWestFaceBreaksTheYZSquare() {
        mine(BlockFace.WEST);

        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) assertEquals(Material.AIR, at(0, a, b).getType());
        }
        assertEquals(25, airCount());
    }

    @Test
    void withNoRecordedFaceLookingStraightDownTakesTheFlatSquare() {
        player.teleport(new Location(world, X + 0.5, Y + 2, Z + 0.5, 0, 90));

        player.breakBlock(at(0, 0, 0));

        assertEquals(Material.AIR, at(2, 0, 2).getType());
        assertEquals(25, airCount());
    }

    @Test
    void onlyNaturalStoneAndOresAreTakenAroundIt() {
        at(1, 0, 0).setType(Material.DIRT);
        at(-1, 0, 0).setType(Material.OAK_PLANKS);
        at(0, 0, 1).setType(Material.CHEST);
        at(0, 0, -1).setType(Material.ANCIENT_DEBRIS);
        at(2, 0, 2).setType(Material.SPAWNER);
        at(-2, 0, -2).setType(Material.DEEPSLATE_DIAMOND_ORE);
        at(2, 0, -2).setType(Material.RED_TERRACOTTA);
        at(-2, 0, 2).setType(Material.BEDROCK);

        mine(BlockFace.UP);

        assertEquals(Material.DIRT, at(1, 0, 0).getType());
        assertEquals(Material.OAK_PLANKS, at(-1, 0, 0).getType());
        assertEquals(Material.CHEST, at(0, 0, 1).getType());
        assertEquals(Material.ANCIENT_DEBRIS, at(0, 0, -1).getType(), "debris is never area-mined");
        assertEquals(Material.SPAWNER, at(2, 0, 2).getType());
        assertEquals(Material.BEDROCK, at(-2, 0, 2).getType());
        assertEquals(Material.AIR, at(-2, 0, -2).getType(), "ores are");
        assertEquals(Material.AIR, at(2, 0, -2).getType(), "badlands terracotta is");
    }

    @Test
    void ancientDebrisMinedDirectlyStillTakesTheStoneAroundIt() {
        at(0, 0, 0).setType(Material.ANCIENT_DEBRIS);

        mine(BlockFace.UP);

        assertEquals(25, airCount());
    }

    @Test
    void miningSomethingThatIsNotStoneBreaksOnlyThatBlock() {
        at(0, 0, 0).setType(Material.DIRT);

        mine(BlockFace.UP);

        assertEquals(1, airCount());
    }

    @Test
    void sneakingMinesOneBlock() {
        player.setSneaking(true);

        mine(BlockFace.UP);

        assertEquals(1, airCount());
    }

    @Test
    void anOrdinaryPickaxeMinesOneBlock() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));

        mine(BlockFace.UP);

        assertEquals(1, airCount());
    }

    @Test
    void itWorksForWhoeverHoldsIt() {
        PlayerMock other = server.addPlayer("SirBepy");
        other.teleport(player.getLocation());
        other.getInventory().setItemInMainHand(earthbreaker());
        player = other;

        mine(BlockFace.UP);

        assertEquals(25, airCount());
    }

    @Test
    void aProtectedNeighbourIsSkippedAndTheRestStillBreak() {
        // Stands in for Towny or MineGuard: the x+2 column is someone else's claim.
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void claim(BlockBreakEvent event) {
                if (event.getBlock().getX() == X + 2) event.setCancelled(true);
            }
        }, plugin);

        mine(BlockFace.UP);

        for (int b = -2; b <= 2; b++) assertEquals(Material.STONE, at(2, 0, b).getType(), "claimed block kept");
        assertEquals(20, airCount());
    }

    @Test
    void aCancelledCentreBreakTakesNothingAround() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void claim(BlockBreakEvent event) {
                if (event.getBlock().equals(at(0, 0, 0))) event.setCancelled(true);
            }
        }, plugin);

        mine(BlockFace.UP);

        assertEquals(0, airCount());
    }

    @Test
    void everyNeighbourFiresOneBreakEventAndTheAreaDoesNotSpread() {
        mine(BlockFace.UP);

        assertEquals(25, breakEvents.size(), "the centre plus 24 neighbours, each once");
        assertEquals(25, breakEvents.stream().distinct().count());
    }

    @Test
    void areaModeDigsAtAQuarterOfTheSpeedAndSneakingLiftsIt() {
        listener.tick();
        assertEquals(0.25, player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getValue(), 1e-9);

        player.setSneaking(true);
        listener.tick();
        assertEquals(1.0, player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getValue(), 1e-9);

        player.setSneaking(false);
        listener.tick();
        listener.tick();
        assertEquals(0.25, player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getValue(), 1e-9, "added once, not stacked");

        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        listener.tick();
        assertEquals(1.0, player.getAttribute(Attribute.BLOCK_BREAK_SPEED).getValue(), 1e-9);
    }

    @Test
    void theAreaListIsStoneAndOresOnly() {
        assertTrue(EarthbreakerListener.AREA_MINEABLE.contains(Material.NETHER_QUARTZ_ORE));
        assertTrue(EarthbreakerListener.AREA_MINEABLE.contains(Material.TERRACOTTA));
        for (Material never : List.of(Material.ANCIENT_DEBRIS, Material.DIRT, Material.OAK_LOG, Material.STONE_BRICKS,
                Material.GLASS, Material.OBSIDIAN, Material.WHITE_GLAZED_TERRACOTTA, Material.MOSSY_COBBLESTONE)) {
            assertTrue(!EarthbreakerListener.AREA_MINEABLE.contains(never), never + " is never area-mined");
        }
    }
}

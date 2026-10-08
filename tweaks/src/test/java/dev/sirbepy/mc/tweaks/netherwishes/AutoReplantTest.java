package dev.sirbepy.mc.tweaks.netherwishes;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoReplantTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock coffie;
    private PlayerMock stranger;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        coffie = server.addPlayer("CoffiebagSupreme");
        stranger = server.addPlayer("Someone");
        server.getPluginManager().registerEvents(new AutoReplant(MockBukkit.createMockPlugin(), coffie::getUniqueId),
                MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block crop(int x, Material soil, Material type, boolean ripe) {
        world.getBlockAt(x, 64, 0).setType(soil);
        Block block = world.getBlockAt(x, 65, 0);
        block.setType(type);
        Ageable data = (Ageable) block.getBlockData();
        data.setAge(ripe ? data.getMaximumAge() : 0);
        block.setBlockData(data);
        return block;
    }

    /** What the server does: the break event, then the block goes, then time passes. */
    private void harvest(Block block, Player player) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        if (!event.isCancelled()) block.setType(Material.AIR);
    }

    private int count(Player player, Material item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == item) total += stack.getAmount();
        }
        return total;
    }

    private static int age(Block block) {
        return ((Ageable) block.getBlockData()).getAge();
    }

    @Test
    void aRipeCropHeBreaksReplantsAndUsesOneSeed() {
        coffie.getInventory().addItem(ItemStack.of(Material.WHEAT_SEEDS, 5));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertEquals(Material.WHEAT, wheat.getType());
        assertEquals(0, age(wheat));
        assertEquals(4, count(coffie, Material.WHEAT_SEEDS));
    }

    @Test
    void eachCropTakesItsOwnSeed() {
        coffie.getInventory().addItem(ItemStack.of(Material.CARROT, 2), ItemStack.of(Material.POTATO, 2),
                ItemStack.of(Material.BEETROOT_SEEDS, 2), ItemStack.of(Material.NETHER_WART, 2));
        Block carrots = crop(0, Material.FARMLAND, Material.CARROTS, true);
        Block potatoes = crop(2, Material.FARMLAND, Material.POTATOES, true);
        Block beets = crop(4, Material.FARMLAND, Material.BEETROOTS, true);
        Block wart = crop(6, Material.SOUL_SAND, Material.NETHER_WART, true);
        for (Block block : new Block[] {carrots, potatoes, beets, wart}) harvest(block, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertEquals(Material.CARROTS, carrots.getType());
        assertEquals(Material.POTATOES, potatoes.getType());
        assertEquals(Material.BEETROOTS, beets.getType());
        assertEquals(Material.NETHER_WART, wart.getType());
        for (Material seed : new Material[] {Material.CARROT, Material.POTATO, Material.BEETROOT_SEEDS, Material.NETHER_WART}) {
            assertEquals(1, count(coffie, seed), seed.name());
        }
    }

    @Test
    void cocoaGoesBackOnTheSameSideOfTheLog() {
        coffie.getInventory().addItem(ItemStack.of(Material.COCOA_BEANS, 3));
        world.getBlockAt(0, 65, 1).setType(Material.JUNGLE_LOG);
        Block cocoa = world.getBlockAt(0, 65, 0);
        cocoa.setType(Material.COCOA);
        Cocoa data = (Cocoa) cocoa.getBlockData();
        data.setFacing(BlockFace.SOUTH);
        data.setAge(data.getMaximumAge());
        cocoa.setBlockData(data);
        harvest(cocoa, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertEquals(Material.COCOA, cocoa.getType());
        assertEquals(BlockFace.SOUTH, ((Cocoa) cocoa.getBlockData()).getFacing());
        assertEquals(0, age(cocoa));
        assertEquals(2, count(coffie, Material.COCOA_BEANS));
    }

    @Test
    void anUnripeCropStaysBroken() {
        coffie.getInventory().addItem(ItemStack.of(Material.WHEAT_SEEDS, 5));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, false);
        harvest(wheat, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertTrue(wheat.getType().isAir());
        assertEquals(5, count(coffie, Material.WHEAT_SEEDS));
    }

    @Test
    void withoutASeedNothingIsPlanted() {
        coffie.getInventory().addItem(ItemStack.of(Material.CARROT, 5));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertTrue(wheat.getType().isAir());
        assertEquals(5, count(coffie, Material.CARROT));
    }

    @Test
    void aRenamedSeedIsNotUsed() {
        ItemStack named = ItemStack.of(Material.WHEAT_SEEDS, 5);
        named.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("Lucky seeds")));
        coffie.getInventory().addItem(named);
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertTrue(wheat.getType().isAir());
        assertEquals(5, count(coffie, Material.WHEAT_SEEDS));
    }

    @Test
    void aSeedInTheOffhandCounts() {
        coffie.getInventory().setItemInOffHand(ItemStack.of(Material.WHEAT_SEEDS, 1));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, coffie);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertEquals(Material.WHEAT, wheat.getType());
        assertEquals(0, count(coffie, Material.WHEAT_SEEDS));
    }

    @Test
    void onlyHisHarvestsReplant() {
        stranger.getInventory().addItem(ItemStack.of(Material.WHEAT_SEEDS, 5));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, stranger);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertTrue(wheat.getType().isAir());
        assertEquals(5, count(stranger, Material.WHEAT_SEEDS));
    }

    @Test
    void aCropReplenishAlreadyReplantedCostsNoSeed() {
        coffie.getInventory().addItem(ItemStack.of(Material.WHEAT_SEEDS, 5));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, coffie);
        // AuraSkills' Replenish puts the crop back 4 ticks after the break, for free.
        server.getScheduler().performTicks(4);
        wheat.setType(Material.WHEAT);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertEquals(Material.WHEAT, wheat.getType());
        assertEquals(5, count(coffie, Material.WHEAT_SEEDS));
    }

    @Test
    void farmlandTrampledToDirtMeanwhileGetsNoCrop() {
        coffie.getInventory().addItem(ItemStack.of(Material.WHEAT_SEEDS, 5));
        Block wheat = crop(0, Material.FARMLAND, Material.WHEAT, true);
        harvest(wheat, coffie);
        world.getBlockAt(0, 64, 0).setType(Material.DIRT);
        server.getScheduler().performTicks(AutoReplant.DELAY_TICKS);
        assertTrue(wheat.getType().isAir());
        assertEquals(5, count(coffie, Material.WHEAT_SEEDS));
    }
}

package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Miner branch: Vein Sense, Vein Miner, Smelter, Deep Pockets, Lucky Strike, Magnet, Drill. */
class ArtificerMiningTest extends KitTestBase {

    @Test
    void isOreMatchesVanillaOreNamingAndAncientDebris() {
        assertTrue(ArtificerMining.isOre(Material.DIAMOND_ORE));
        assertTrue(ArtificerMining.isOre(Material.DEEPSLATE_IRON_ORE));
        assertTrue(ArtificerMining.isOre(Material.ANCIENT_DEBRIS));
        assertFalse(ArtificerMining.isOre(Material.STONE));
    }

    @Test
    void hasSilkTouchDetectsTheEnchant() {
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        assertFalse(ArtificerMining.hasSilkTouch(pick));
        pick.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        assertTrue(ArtificerMining.hasSilkTouch(pick));
    }

    @Test
    void isToolCoversAllFourToolKinds() {
        assertTrue(ArtificerMining.isTool(Material.IRON_PICKAXE));
        assertTrue(ArtificerMining.isTool(Material.IRON_AXE));
        assertTrue(ArtificerMining.isTool(Material.IRON_SHOVEL));
        assertTrue(ArtificerMining.isTool(Material.IRON_HOE));
        assertFalse(ArtificerMining.isTool(Material.IRON_SWORD));
    }

    @Test
    void nearbyMatchingFindsSameTypeOresWithinRadiusOnly() {
        Block origin = world.getBlockAt(0, 64, 0);
        origin.setType(Material.IRON_ORE);
        world.getBlockAt(2, 64, 0).setType(Material.IRON_ORE);
        world.getBlockAt(10, 64, 0).setType(Material.IRON_ORE);
        world.getBlockAt(1, 64, 0).setType(Material.GOLD_ORE);
        List<Block> found = ArtificerMining.nearbyMatching(origin, b -> b.getType() == Material.IRON_ORE, 3);
        assertEquals(1, found.size());
        assertEquals(world.getBlockAt(2, 64, 0), found.get(0));
    }

    @Test
    void veinMinerBreaksConnectedOresUpToTheRankCapWhileSneaking() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "vein_miner", 1);
        p.setSneaking(true);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        for (int i = 0; i < 6; i++) world.getBlockAt(i, 64, 0).setType(Material.IRON_ORE);
        Block origin = world.getBlockAt(0, 64, 0);
        server.getPluginManager().callEvent(new BlockBreakEvent(origin, p));
        int brokenOres = 0;
        for (int i = 0; i < 6; i++) if (world.getBlockAt(i, 64, 0).getType() == Material.AIR) brokenOres++;
        // Rank 1 caps the chain at 4 extra ores, plus the origin break itself (not fired by this
        // raw event since BlockBreakEvent here isn't cancelled, only the chain's extras are counted).
        assertEquals(4, brokenOres);
    }

    @Test
    void veinMinerDoesNothingWithoutSneaking() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "vein_miner", 3);
        p.setSneaking(false);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        for (int i = 0; i < 4; i++) world.getBlockAt(i, 64, 0).setType(Material.IRON_ORE);
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(0, 64, 0), p));
        assertEquals(Material.IRON_ORE, world.getBlockAt(1, 64, 0).getType());
    }

    @Test
    void veinMinerStopsWhenAnotherListenerCancelsABreak() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "vein_miner", 3);
        p.setSneaking(true);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        for (int i = 0; i < 6; i++) world.getBlockAt(i, 64, 0).setType(Material.IRON_ORE);
        // A fake claim guard: refuse breaking the second ore in the chain, as a real Towny/spawn
        // guard listener would by cancelling the BlockBreakEvent that player.breakBlock() fires.
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onBreak(BlockBreakEvent e) {
                if (e.getBlock().equals(world.getBlockAt(1, 64, 0))) e.setCancelled(true);
            }
        }, plugin);
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(0, 64, 0), p));
        // The refused block stays an ore; nothing past it in the chain was attempted either.
        assertEquals(Material.IRON_ORE, world.getBlockAt(1, 64, 0).getType());
        assertEquals(Material.IRON_ORE, world.getBlockAt(5, 64, 0).getType());
    }

    @Test
    void smelterCanTurnRawIronIntoAnIngot() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "smelter", 2);
        roll = 0;
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.RAW_IRON_BLOCK);
        var drop = world.dropItem(block.getLocation(), new ItemStack(Material.RAW_IRON));
        var event = new org.bukkit.event.block.BlockDropItemEvent(block, block.getState(), p, new java.util.ArrayList<>(List.of(drop)));
        server.getPluginManager().callEvent(event);
        assertEquals(Material.IRON_INGOT, drop.getItemStack().getType());
    }

    @Test
    void deepPocketsCanAddAnExtraOreDrop() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "deep_pockets", 2);
        roll = 0;
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.IRON_ORE);
        var drop = world.dropItem(block.getLocation(), new ItemStack(Material.RAW_IRON));
        int before = world.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        var event = new org.bukkit.event.block.BlockDropItemEvent(block, block.getState(), p, new java.util.ArrayList<>(List.of(drop)));
        server.getPluginManager().callEvent(event);
        int after = world.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        assertTrue(after > before);
    }

    @Test
    void luckyStrikeCanAddABonusDropFromStone() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "lucky_strike", 2);
        roll = 0;
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.STONE);
        int before = world.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        var event = new org.bukkit.event.block.BlockDropItemEvent(block, block.getState(), p, new java.util.ArrayList<>());
        server.getPluginManager().callEvent(event);
        int after = world.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        assertTrue(after > before);
    }

    @Test
    void tunnelerBreaksTheBlockBelowDuringOverclock() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "tunneler", 1);
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        assertTrue(cast(p, Slot.ONE));
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(0, 64, 0), p));
        assertEquals(Material.AIR, world.getBlockAt(0, 63, 0).getType());
    }

    @Test
    void seismicBreaksTheSurroundingFaceDuringOverdrive() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "seismic", 1);
        world.getBlockAt(1, 64, 0).setType(Material.STONE);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        assertTrue(cast(p, Slot.ULTIMATE));
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(0, 64, 0), p));
        assertEquals(Material.AIR, world.getBlockAt(1, 64, 0).getType());
    }

    @Test
    void drillCastLaunchesTheRepeatingTunnelTask() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        p.teleport(at(0)); // yaw 0, pitch 0: faces +Z, matching the tunnel below
        for (int z = 1; z <= 9; z++) {
            for (int x = -1; x <= 1; x++) world.getBlockAt(x, 64, z).setType(Material.STONE);
        }
        assertTrue(kit(RpgClass.ARTIFICER).keystoneAbility(
                kit(RpgClass.ARTIFICER).talents().stream().filter(t -> t.id().equals("drill")).findFirst().orElseThrow())
                .cast(p));
        advance(300);
        int remaining = 0;
        for (int z = 1; z <= 9; z++) {
            for (int x = -1; x <= 1; x++) if (world.getBlockAt(x, 64, z).getType() == Material.STONE) remaining++;
        }
        assertTrue(remaining < 27, "the drill should have cleared at least some of the tunnel");
    }
}

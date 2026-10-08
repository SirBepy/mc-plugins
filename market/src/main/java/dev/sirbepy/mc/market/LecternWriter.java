package dev.sirbepy.mc.market;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the price book on the Guide stall's lectern, placing the lectern if it is missing.
 * MockBukkit has no lectern block state, so e2e/market.mjs is the only test of this class.
 */
final class LecternWriter {

    private LecternWriter() {
    }

    static void write(World world, Stall.Slot spot, ItemStack book) {
        Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());
        if (block.getType() != Material.LECTERN) {
            Directional data = (Directional) Material.LECTERN.createBlockData();
            data.setFacing(spot.facing());
            block.setBlockData(data, false);
        }
        var inventory = ((Lectern) block.getState()).getInventory();
        // Re-placing an identical book would flip every reader back to page 1.
        if (!book.equals(inventory.getItem(0))) {
            inventory.setItem(0, book);
        }
    }
}

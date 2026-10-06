package dev.sirbepy.mc.haru.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Emerald payment from the main inventory. Emerald blocks count as 9 and are broken for change, so
 * the owner never has to craft them back down first. Renamed emeralds are never touched.
 */
public final class Payment {

    private Payment() {
    }

    public static int emeraldsHeld(PlayerInventory inventory) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isPlain(item, Material.EMERALD)) {
                total += item.getAmount();
            } else if (isPlain(item, Material.EMERALD_BLOCK)) {
                total += item.getAmount() * 9;
            }
        }
        return total;
    }

    /** Takes {@code amount} emeralds' worth, loose emeralds first; false (and nothing taken) if short. */
    public static boolean take(Player player, int amount) {
        PlayerInventory inventory = player.getInventory();
        if (amount <= 0) {
            return true;
        }
        if (emeraldsHeld(inventory) < amount) {
            return false;
        }
        int remaining = removeUpTo(inventory, Material.EMERALD, amount);
        if (remaining > 0) {
            int blocks = (remaining + 8) / 9;
            removeUpTo(inventory, Material.EMERALD_BLOCK, blocks);
            int change = blocks * 9 - remaining;
            if (change > 0) {
                Rewards.give(player, new ItemStack(Material.EMERALD, change));
            }
        }
        return true;
    }

    /** Removes up to {@code amount} plain items of {@code material}; returns how many were missing. */
    private static int removeUpTo(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isPlain(item, material)) {
                continue;
            }
            int used = Math.min(remaining, item.getAmount());
            remaining -= used;
            if (used == item.getAmount()) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - used);
            }
        }
        inventory.setStorageContents(contents);
        return remaining;
    }

    /** Counts plain {@code material} in the main inventory (quest hand-ins). */
    public static int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isPlain(item, material)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Takes {@code amount} plain {@code material}; false (and nothing taken) if short. */
    public static boolean takeItems(PlayerInventory inventory, Material material, int amount) {
        if (count(inventory, material) < amount) {
            return false;
        }
        removeUpTo(inventory, material, amount);
        return true;
    }

    static boolean isPlain(ItemStack item, Material material) {
        return item != null && item.getType() == material
                && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName());
    }
}

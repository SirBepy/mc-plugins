package dev.sirbepy.mc.cartel;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Shared by anything that must hand a player an item that can't be lost to a full inventory: trophies and the phone. */
public final class Inventories {

    private Inventories() {
    }

    /** Puts {@code item} in {@code player}'s inventory, or drops the overflow at their feet if it's full. */
    public static void giveOrDrop(Player player, ItemStack item) {
        for (ItemStack overflow : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }
}

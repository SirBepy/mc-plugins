package dev.sirbepy.mc.core;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** For anything a plugin hands a player that must never be lost to a full inventory. */
public final class Inventories {

    private Inventories() {
    }

    /** Puts {@code item} in {@code player}'s inventory and drops whatever doesn't fit at their feet. */
    public static void giveOrDrop(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }
}

package dev.sirbepy.mc.endfight.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** The inventory -&gt; ender chest -&gt; ground chain every bundle goes through, for an online
 * participant (an offline one gets this same chain on their next join - see {@link RewardService}). */
final class RewardDelivery {

    private RewardDelivery() {
    }

    /** Returns where the bundle ended up: {@code "inventory"}, {@code "ender chest"} or
     * {@code "the ground"}, for the chat line telling the participant what happened. */
    static String deliver(Player player, ItemStack bundle) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(bundle);
        if (left.isEmpty()) {
            return "inventory";
        }
        ItemStack remaining = left.values().iterator().next();
        left = player.getEnderChest().addItem(remaining);
        if (left.isEmpty()) {
            return "ender chest";
        }
        for (ItemStack dropped : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
        return "the ground";
    }
}

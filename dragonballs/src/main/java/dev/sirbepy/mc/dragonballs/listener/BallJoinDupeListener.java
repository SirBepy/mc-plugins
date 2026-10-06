package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * On join: delete any ball item the registry doesn't actually have HELD by this player (a stray
 * dupe from creative or a duplication glitch), and swap any stone item whose cooldown already ended
 * while they were offline (the lazy half of {@link BallRegistry#clearStone}'s sweep, which only
 * reaches online players at the moment stone ends).
 */
public final class BallJoinDupeListener implements Listener {

    private final BallRegistry registry;

    public BallJoinDupeListener(BallRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            check(inventory, slot, inventory.getItem(slot), player);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        registry.starOf(offHand).ifPresent(star -> {
            if (!registry.isHeldBy(star, player.getUniqueId())) {
                inventory.setItemInOffHand(null);
            } else if (registry.items().isStoneItem(offHand) && !registry.isStone(star)) {
                inventory.setItemInOffHand(registry.createItem(star));
            }
        });
    }

    private void check(PlayerInventory inventory, int slot, ItemStack item, Player player) {
        registry.starOf(item).ifPresent(star -> {
            if (!registry.isHeldBy(star, player.getUniqueId())) {
                inventory.setItem(slot, null);
            } else if (registry.items().isStoneItem(item) && !registry.isStone(star)) {
                inventory.setItem(slot, registry.createItem(star));
            }
        });
    }
}

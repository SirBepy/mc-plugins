package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * On join: delete any ball item the owning set's registry doesn't actually have HELD by this player
 * (a stray dupe from creative or a duplication glitch), and swap any stone item whose cooldown
 * already ended while they were offline (the lazy half of {@link BallRegistry#clearStone}'s sweep,
 * which only reaches online players at the moment stone ends), and re-make a ball carried since
 * before the 3D look existed so it shows the model. Each item is checked against its OWN
 * set's registry, never a different set's.
 */
public final class BallJoinDupeListener implements Listener {

    private final BallSets sets;

    public BallJoinDupeListener(BallSets sets) {
        this.sets = sets;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            check(inventory, slot, inventory.getItem(slot), player);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        sets.items().starOf(offHand).ifPresent(star -> {
            BallRegistry registry = sets.registryFor(offHand).orElseThrow();
            if (!registry.isHeldBy(star, player.getUniqueId())) {
                inventory.setItemInOffHand(null);
            } else if (sets.items().isStoneItem(offHand) && !registry.isStone(star)) {
                inventory.setItemInOffHand(registry.createItem(star));
            } else if (!sets.items().hasCurrentLook(offHand)) {
                inventory.setItemInOffHand(sets.items().itemFor(registry.set(), star, sets.items().isStoneItem(offHand)));
            }
        });
    }

    private void check(PlayerInventory inventory, int slot, ItemStack item, Player player) {
        sets.items().starOf(item).ifPresent(star -> {
            BallRegistry registry = sets.registryFor(item).orElseThrow();
            if (!registry.isHeldBy(star, player.getUniqueId())) {
                inventory.setItem(slot, null);
            } else if (sets.items().isStoneItem(item) && !registry.isStone(star)) {
                inventory.setItem(slot, registry.createItem(star));
            } else if (!sets.items().hasCurrentLook(item)) {
                inventory.setItem(slot, sets.items().itemFor(registry.set(), star, sets.items().isStoneItem(item)));
            }
        });
    }
}

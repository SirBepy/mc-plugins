package dev.sirbepy.mc.minibosses.relic;

import io.papermc.paper.event.player.CartographyItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps sigils out of every recipe except the relic upgrade. They are plain {@code PAPER}, so
 * without this they would craft books and maps, rename in an anvil, or extend a map in a
 * cartography table. The smithing table is {@link RelicUpgradeListener}'s job.
 */
public final class SigilGuardListener implements Listener {

    private final Sigils sigils;

    public SigilGuardListener(Sigils sigils) {
        this.sigils = sigils;
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (sigils.isSigil(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (sigils.isSigil(event.getInventory().getFirstItem()) || sigils.isSigil(event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    // The cartography table has no prepare event, so the take is what gets refused.
    @EventHandler(ignoreCancelled = true)
    public void onCartography(CartographyItemEvent event) {
        for (ItemStack input : event.getInventory().getContents()) {
            if (sigils.isSigil(input)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}

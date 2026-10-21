package dev.sirbepy.mc.minibosses.relic;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Smithing-table front end for {@link RelicUpgrades}. The registered recipe matches on material
 * only (paper, any relic material, paper) so the table has a recipe to show a result slot for;
 * {@link #onPrepare} then decides by PDC, replacing the recipe's placeholder result with the
 * upgraded relic or with nothing.
 * Taking the result consumes one item from each slot through the server's normal smithing take.
 */
public final class RelicUpgradeListener implements Listener {

    private final JavaPlugin plugin;
    private final Relics relics;
    private final RelicUpgrades upgrades;

    public RelicUpgradeListener(JavaPlugin plugin, Relics relics, RelicUpgrades upgrades) {
        this.plugin = plugin;
        this.relics = relics;
        this.upgrades = upgrades;
    }

    public void registerRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, "relic_upgrade");
        // Remove-then-add so a plugin reload re-registers instead of colliding with its own old copy.
        plugin.getServer().removeRecipe(key);
        plugin.getServer().addRecipe(new SmithingTransformRecipe(key, new ItemStack(Material.HEART_OF_THE_SEA),
                new RecipeChoice.MaterialChoice(Material.PAPER),
                new RecipeChoice.MaterialChoice(relics.materials()),
                new RecipeChoice.MaterialChoice(Material.PAPER)));
    }

    @EventHandler
    public void onPrepare(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        ItemStack template = inventory.getInputTemplate();
        ItemStack base = inventory.getInputEquipment();
        ItemStack addition = inventory.getInputMineral();
        // A plain paper + plain heart of the sea also matches the recipe's materials, so the shape
        // alone is enough to take over; a sigil in any slot is taken over even when the shape is off.
        if (upgrades.matchesRecipeShape(template, base, addition) || upgrades.touchesSigil(template, base, addition)) {
            event.setResult(upgrades.result(template, base, addition).orElse(null));
        }
    }
}

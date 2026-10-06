package dev.sirbepy.mc.dragonballs.radar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Builds and identifies the Dragon Radar compass. Identified ONLY by the {@code dragonballs:radar}
 * PDC marker (never by the custom-model-data string, which only drives the resource pack's needle
 * vs. no-signal screen and is swapped in place as the hunt state changes).
 */
public final class RadarItems {

    static final String ACTIVE_MODEL = "dragon_radar";
    static final String IDLE_MODEL = "dragon_radar_idle";

    private final NamespacedKey radarKey;

    public RadarItems(JavaPlugin plugin) {
        this.radarKey = new NamespacedKey(plugin, "radar");
    }

    public NamespacedKey radarKey() {
        return radarKey;
    }

    public boolean isRadar(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(radarKey, PersistentDataType.BOOLEAN);
    }

    /** The purchasable item: starts showing the active needle screen (swapped to idle, if there's
     * nothing to point at, the next time {@code RadarTask} runs). */
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Dragon Radar", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Points at the nearest awake Dragon Ball.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Never says how far.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(radarKey, PersistentDataType.BOOLEAN, true);
        applyModel(meta, ACTIVE_MODEL);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isShowingActive(ItemStack item) {
        return hasModel(item, ACTIVE_MODEL);
    }

    public boolean isShowingIdle(ItemStack item) {
        return hasModel(item, IDLE_MODEL);
    }

    /**
     * Swaps only the custom-model-data string (name/lore/PDC untouched). Callers must only invoke
     * this when the active/idle state actually changes: rewriting a held item's components every
     * tick would replay the hand re-equip animation.
     */
    public void setActive(ItemStack item, boolean active) {
        ItemMeta meta = item.getItemMeta();
        applyModel(meta, active ? ACTIVE_MODEL : IDLE_MODEL);
        item.setItemMeta(meta);
    }

    private boolean hasModel(ItemStack item, String model) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent() && meta.getCustomModelDataComponent().getStrings().contains(model);
    }

    private void applyModel(ItemMeta meta, String model) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(model));
        meta.setCustomModelDataComponent(cmd);
    }
}

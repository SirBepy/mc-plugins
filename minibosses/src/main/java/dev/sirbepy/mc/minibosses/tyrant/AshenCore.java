package dev.sirbepy.mc.minibosses.tyrant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * The Ashen Tyrant's Normal-tier relic: re-themed off {@code NETHER_STAR} (same "rare, not
 * otherwise obtainable or craftable" shape {@code sentinel.HeartOfTheWild} re-themed
 * {@code HEART_OF_THE_SEA} for - a Nether Star is normally a Wither drop, never crafted, so a
 * player seeing one in a Tyrant's loot reads as "a real artifact", not confused with a farmed
 * item), held in the off hand for a passive Fire Resistance while the Tyrant's own ashen heat
 * still lingers in it. Identified only by the {@code ashen_core} PDC marker, the same
 * marker-not-name-or-material convention {@code HeartOfTheWild} uses.
 */
public final class AshenCore {

    // Reapplied every tick() call rather than granted once for a long duration, so the effect
    // disappears within one tick-interval of being unequipped - mirrors HeartOfTheWild's own
    // tick-and-reapply shape.
    private static final int FIRE_RESISTANCE_TICKS = 40;

    private final NamespacedKey markerKey;

    public AshenCore(JavaPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "ashen_core");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("The Ashen Core", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Torn from the Ashen Tyrant.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Held in your off hand, it shields you from flame.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isAshenCore(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    /** One tick of the passive effect: call from a repeating task over the online players. Reapplies
     * Fire Resistance while the relic sits in the off hand; does nothing otherwise, so unequipping
     * it lets the effect run out within {@link #FIRE_RESISTANCE_TICKS} instead of needing an
     * explicit removal. */
    public void tick(Player player) {
        if (isAshenCore(player.getInventory().getItemInOffHand())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0,
                    true, false, true));
        }
    }
}

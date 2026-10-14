package dev.sirbepy.mc.minibosses.sovereign;

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
 * The Void Sovereign's Normal-tier relic: re-themed off {@code SHULKER_SHELL} (same "rare, not
 * otherwise obtainable or craftable" shape {@code sentinel.HeartOfTheWild}/{@code tyrant.AshenCore}
 * each re-theme a drop-only vanilla item for - a Shulker Shell already comes only from the End, so
 * a player seeing one in a Sovereign's loot reads as "a real End artifact" rather than a confusing
 * reskin of something craftable), held in the off hand for a passive Slow Falling while the
 * Sovereign's own grip on gravity still lingers in it - a deliberate nod to the exposed-sky/void-
 * edge arena the todo's notes call out for this Guardian's Mythic mechanic, even though that
 * mechanic itself is a later dispatch's job. Identified only by the {@code hollow_crown} PDC
 * marker, the same marker-not-name-or-material convention the other two relics use.
 */
public final class HollowCrown {

    // Reapplied every tick() call rather than granted once for a long duration, so the effect
    // disappears within one tick-interval of being unequipped - mirrors HeartOfTheWild/AshenCore's
    // own tick-and-reapply shape.
    private static final int SLOW_FALLING_TICKS = 40;

    private final NamespacedKey markerKey;

    public HollowCrown(JavaPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "hollow_crown");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.SHULKER_SHELL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("The Hollow Crown", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Torn from the Void Sovereign.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Held in your off hand, it unweights your fall.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHollowCrown(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    /** One tick of the passive effect: call from a repeating task over the online players. Reapplies
     * Slow Falling while the relic sits in the off hand; does nothing otherwise, so unequipping it
     * lets the effect run out within {@link #SLOW_FALLING_TICKS} instead of needing an explicit
     * removal. */
    public void tick(Player player) {
        if (isHollowCrown(player.getInventory().getItemInOffHand())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALLING_TICKS, 0,
                    true, false, true));
        }
    }
}

package dev.sirbepy.mc.minibosses.sentinel;

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
 * The Overgrown Sentinel's Normal-tier relic: re-themed off {@code HEART_OF_THE_SEA} (same icon
 * family, "Heart of the X" naming, not otherwise obtainable or craftable so it is never confused
 * with the vanilla item), held in the off hand for a passive Regeneration while the Sentinel's
 * life still lingers in it. Identified only by the {@code heart_of_the_wild} PDC marker, the same
 * marker-not-name-or-material convention {@code dragonballs.item.BallItems}' star key uses.
 */
public final class HeartOfTheWild {

    private static final int REGEN_AMPLIFIER = 0;
    // Reapplied every tick() call rather than granted once for a long duration, so the effect
    // disappears within one tick-interval of being unequipped instead of lingering for its last
    // top-up - mirrors endfight.reward.DragonAuraService's own tick-and-reapply shape.
    private static final int REGEN_TICKS = 40;

    private final NamespacedKey markerKey;

    public HeartOfTheWild(JavaPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "heart_of_the_wild");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Heart of the Wild", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Torn from the Overgrown Sentinel.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Held in your off hand, it slowly mends you.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHeartOfTheWild(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    /** One tick of the passive effect: call from a repeating task over the online players. Reapplies
     * Regeneration while the relic sits in the off hand; does nothing otherwise, so unequipping it
     * lets the effect run out within {@link #REGEN_TICKS} instead of needing an explicit removal. */
    public void tick(Player player) {
        if (isHeartOfTheWild(player.getInventory().getItemInOffHand())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_TICKS, REGEN_AMPLIFIER,
                    true, false, true));
        }
    }
}

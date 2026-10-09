package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * A Cosmetic Token: a nether star marked with a PDC flag, never recognised by its name or look
 * alone (a renamed nether star is never a token - the flag is the only thing /cosmetic trusts).
 */
final class CosmeticToken {

    private final NamespacedKey key;

    CosmeticToken(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "cosmetic_token");
    }

    ItemStack create() {
        ItemStack item = ItemStack.of(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Cosmetic Token", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    plain("Hold an item and run"),
                    plain("/cosmetic <your idea>"),
                    plain("Claude restyles it;"),
                    plain("live after a restart")));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    private static Component plain(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    boolean is(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    /** Whether the inventory (offhand included) carries at least one token, without taking it. */
    boolean hasToken(PlayerInventory inventory) {
        for (ItemStack stack : inventory.getStorageContents()) {
            if (is(stack)) {
                return true;
            }
        }
        return is(inventory.getItemInOffHand());
    }

    /**
     * Removes one token from anywhere in the inventory (offhand included); false if it had none.
     * {@code getStorageContents()} (the 36 main/hotbar slots, indexed the same as {@code getItem}/
     * {@code setItem}) plus the offhand explicitly, since armor slots can never hold a token.
     */
    boolean takeOneFrom(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (is(storage[i])) {
                int slot = i;
                takeOne(storage[i], item -> inventory.setItem(slot, item));
                return true;
            }
        }
        ItemStack off = inventory.getItemInOffHand();
        if (is(off)) {
            takeOne(off, inventory::setItemInOffHand);
            return true;
        }
        return false;
    }

    private static void takeOne(ItemStack stack, Consumer<ItemStack> set) {
        if (stack.getAmount() <= 1) {
            set.accept(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            set.accept(stack);
        }
    }

    /** Gives the player one token, dropping it at their feet like RadarShop does when the inventory is full. */
    void giveTo(Player player) {
        var leftover = player.getInventory().addItem(create());
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), leftover.values().iterator().next());
        }
    }
}

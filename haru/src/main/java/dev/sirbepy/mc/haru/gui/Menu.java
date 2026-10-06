package dev.sirbepy.mc.haru.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/** A chest screen whose slots run actions instead of holding items; {@link MenuListener} dispatches. */
public final class Menu implements InventoryHolder {

    /** Gets the menu actually on screen, which after {@link #adopt} isn't the one the action was built in. */
    @FunctionalInterface
    public interface Action {
        void run(Menu shown, ClickType click);
    }

    private final Inventory inventory;
    private final Component title;
    private final Map<Integer, Action> actions = new HashMap<>();

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.title = title;
    }

    public Component title() {
        return title;
    }

    public void set(int slot, ItemStack icon) {
        inventory.setItem(slot, icon);
    }

    public void set(int slot, ItemStack icon, Action action) {
        inventory.setItem(slot, icon);
        actions.put(slot, action);
    }

    /** Fills every empty slot with a nameless pane. */
    public void frame(Material pane) {
        ItemStack filler = new ItemStack(pane);
        ItemMeta meta = filler.getItemMeta();
        meta.setHideTooltip(true);
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    /** Takes over another menu's icons and actions in place, so a refresh doesn't reset the cursor. */
    public void adopt(Menu other) {
        inventory.setContents(other.inventory.getContents());
        actions.clear();
        actions.putAll(other.actions);
    }

    public boolean click(int slot, ClickType type) {
        Action action = actions.get(slot);
        if (action == null) {
            return false;
        }
        action.run(this, type);
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

package dev.sirbepy.mc.names.gui;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.render.NickApplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens the {@code /nick} anvil rename-field GUI and reads the typed text back out. A dummy item
 * is placed in the input slot purely so the vanilla client enables the rename text field; the
 * result slot click is cancelled and never produces that vanilla item, only reads the text.
 */
public final class NickAnvilController implements Listener {

    private final NamesConfig config;
    private final NickApplier nickApplier;
    private final Set<UUID> activeSessions = ConcurrentHashMap.newKeySet();

    public NickAnvilController(NamesConfig config, NickApplier nickApplier) {
        this.config = config;
        this.nickApplier = nickApplier;
    }

    public void open(Player player) {
        AnvilView view = MenuType.ANVIL.create(player, config.messages().render("nick-gui-title"));
        view.setRepairCost(0);
        view.bypassEnchantmentLevelRestriction(true);
        view.getTopInventory().setItem(0, new ItemStack(Material.PAPER));
        activeSessions.add(player.getUniqueId());
        view.open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !activeSessions.contains(player.getUniqueId())) {
            return;
        }
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        event.setCancelled(true);
        String typed = anvilView.getRenameText();
        activeSessions.remove(player.getUniqueId());
        player.closeInventory();
        nickApplier.setNick(player, typed, player, true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getType() == InventoryType.ANVIL) {
            activeSessions.remove(event.getPlayer().getUniqueId());
        }
    }
}

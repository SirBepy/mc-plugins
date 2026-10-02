package dev.sirbepy.mc.names.gui;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.ColorChoice;
import dev.sirbepy.mc.names.render.NameChangeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class ColorGuiListener implements Listener {

    private final NamesConfig config;
    private final NameChangeService changeService;

    public ColorGuiListener(NamesConfig config, NameChangeService changeService) {
        this.config = config;
        this.changeService = changeService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ColorGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (gui.isResetSlot(slot)) {
            changeService.resetColor(player);
            config.messages().send(player, "color-reset");
            player.closeInventory();
            return;
        }
        gui.choiceAt(slot).ifPresent(choice -> {
            changeService.setColor(player, choice);
            config.messages().send(player, "color-applied");
            player.closeInventory();
        });
    }
}

package dev.sirbepy.mc.endfight.altar;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking the altar's lodestone (main hand only, so the off-hand's mirrored event on the
 * same swing doesn't open the menu twice) opens {@link AltarMenu} instead of letting it behave
 * like a normal lodestone. */
public final class AltarInteractListener implements Listener {

    private final AltarLocator altar;
    private final AltarMenu menu;

    public AltarInteractListener(AltarLocator altar, AltarMenu menu) {
        this.altar = altar;
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getWorld().getEnvironment() != World.Environment.THE_END
                || !altar.isClickBlock(clicked.getX(), clicked.getY(), clicked.getZ())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        menu.open(player);
    }
}

package dev.sirbepy.mc.safechests.listener;

import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Lets copper golems sort into and out of locked chests. Bolt denies every golem on a protected
 * container (golems carry no owner, so Bolt can't match them to an access list), and a Bolt config
 * change would open those chests to players too. Golems are owner-blind here as well, by Joe's
 * choice (2026-10-04): any golem may use any locked chest.
 */
public final class CopperGolemAccessListener implements Listener {

    private final BoltAccess bolt;

    public CopperGolemAccessListener(BoltAccess bolt) {
        this.bolt = bolt;
    }

    // HIGH: after Bolt's own NORMAL-priority handler. Paper fires this up to every tick per golem,
    // so only Bolt-denied targets pay for the protection lookup, and vanilla's own refusals of
    // non-Bolt blocks stay refused.
    @EventHandler(priority = EventPriority.HIGH)
    public void onValidateTarget(ItemTransportingEntityValidateTargetEvent event) {
        if (event.isAllowed() || event.getEntity().getType() != EntityType.COPPER_GOLEM) return;
        if (bolt.api().findProtection(event.getBlock()) != null) {
            event.setAllowed(true);
        }
    }
}

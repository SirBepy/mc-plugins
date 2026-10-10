package dev.sirbepy.mc.classes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Join: Frail Body, footer and the greeting. Sneak + swap hands (F): the ability, items stay put. */
public final class ClassSessions implements Listener {

    private final Classes classes;
    private final ClassPassives passives;
    private final ClassXp xp;
    private final ClassDisplay display;
    private final Abilities abilities;

    public ClassSessions(Classes classes, ClassPassives passives, ClassXp xp, ClassDisplay display, Abilities abilities) {
        this.classes = classes;
        this.passives = passives;
        this.xp = xp;
        this.display = display;
        this.abilities = abilities;
    }

    public void sync(Player player) {
        passives.applyFrailBody(player);
        display.refresh(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sync(event.getPlayer());
        display.greet(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        passives.forget(event.getPlayer().getUniqueId());
        xp.forget(event.getPlayer().getUniqueId());
    }

    // ignoreCancelled: Carry and Tag's head lock cancel swaps that mean something to them.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || classes.active(player.getUniqueId()) == null) return;
        event.setCancelled(true);
        abilities.use(player);
    }
}

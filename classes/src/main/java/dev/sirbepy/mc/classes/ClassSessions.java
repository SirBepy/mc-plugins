package dev.sirbepy.mc.classes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join: Frail Body, footer and the greeting. Ability casting lives in the talent engine's ComboListener. */
public final class ClassSessions implements Listener {

    private final ClassPassives passives;
    private final ClassXp xp;
    private final ClassDisplay display;

    public ClassSessions(ClassPassives passives, ClassXp xp, ClassDisplay display) {
        this.passives = passives;
        this.xp = xp;
        this.display = display;
    }

    public void sync(Player player) {
        passives.applyFrailBody(player);
        passives.applyHeavyPlate(player);
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
}

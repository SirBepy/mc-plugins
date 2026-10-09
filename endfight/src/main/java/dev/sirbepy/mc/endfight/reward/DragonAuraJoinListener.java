package dev.sirbepy.mc.endfight.reward;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Re-applies the joining player's worn dragon aura (if any) to {@link DragonAuraService}'s tick
 * cache, so it survives a relog without re-reading {@code rewards.yml} every tick. */
public final class DragonAuraJoinListener implements Listener {

    private final DragonAuraService auras;

    public DragonAuraJoinListener(DragonAuraService auras) {
        this.auras = auras;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        auras.reapplyOnJoin(event.getPlayer());
    }
}

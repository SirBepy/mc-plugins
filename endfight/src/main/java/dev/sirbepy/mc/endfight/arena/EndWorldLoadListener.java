package dev.sirbepy.mc.endfight.arena;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.function.Consumer;

/**
 * Live keeps {@code allow-end: false} until Joe opens it, so the End world doesn't exist there at
 * all until then - a plugin reload on enable never sees it. This catches the moment it DOES load
 * (Joe flips the setting and restarts, or a late world creation), so the first boot with the End
 * open can auto-build before any player gets there.
 */
public final class EndWorldLoadListener implements Listener {

    private final Consumer<World> onEndLoaded;

    public EndWorldLoadListener(Consumer<World> onEndLoaded) {
        this.onEndLoaded = onEndLoaded;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (event.getWorld().getEnvironment() == World.Environment.THE_END) {
            onEndLoaded.accept(event.getWorld());
        }
    }
}

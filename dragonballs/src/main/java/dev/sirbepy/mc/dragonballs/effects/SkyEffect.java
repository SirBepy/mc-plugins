package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

/**
 * Client-side-only night + storm for every player in the summon's world, applied/restored as
 * players join or move in and out of that world mid-session. Never touches the world's own
 * real time or weather. Only ever started for the Overworld (see SpectacleEffects) - the Nether
 * and End have no sky to darken.
 */
final class SkyEffect implements Listener {

    private final JavaPlugin plugin;
    private World activeWorld;

    SkyEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dark, stormy sky for every current player in {@code world}, and for anyone who joins or
     * wanders into it before {@link #end()}. */
    void begin(World world) {
        this.activeWorld = world;
        applyTo(world.getPlayers());
    }

    /** Restores every player currently in the active world, then stops tracking it. */
    void end() {
        if (activeWorld == null) {
            return;
        }
        restore(activeWorld.getPlayers());
        activeWorld = null;
    }

    /** Plugin disable / safety reset: same as {@link #end()}, nothing to animate either way. */
    void shutdown() {
        end();
    }

    private void applyTo(Collection<? extends Player> players) {
        for (Player player : players) {
            player.setPlayerTime(18000, false);
            player.setPlayerWeather(WeatherType.DOWNFALL);
        }
    }

    private void restore(Collection<? extends Player> players) {
        for (Player player : players) {
            player.resetPlayerTime();
            player.resetPlayerWeather();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (activeWorld != null && activeWorld.equals(event.getPlayer().getWorld())) {
            applyTo(List.of(event.getPlayer()));
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (activeWorld != null && activeWorld.equals(player.getWorld())) {
            applyTo(List.of(player));
        } else if (activeWorld != null && activeWorld.equals(event.getFrom())) {
            player.resetPlayerTime();
            player.resetPlayerWeather();
        }
    }
}

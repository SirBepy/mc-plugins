package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Location;
import org.bukkit.Sound;
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
import java.util.Map;

/**
 * Client-side-only night + storm for every player in the summon's world, applied/restored as
 * players join or move in and out of that world mid-session. Never touches the world's own
 * real time or weather.
 */
final class SkyEffect implements Listener {

    private static final long THUNDER_STRIKE_SPAN_TICKS = 40; // ~2s across 7 strikes

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

    /** 7 harmless lightning strikes (no damage, no fire) spread over ~2s, one per ball origin,
     * plus a single thunder sound for the whole world. */
    void thunderOverBalls(World world, Map<Integer, Location> ballOrigins) {
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        }
        List<Location> origins = List.copyOf(ballOrigins.values());
        if (origins.isEmpty()) {
            return;
        }
        long step = Math.max(1, THUNDER_STRIKE_SPAN_TICKS / origins.size());
        for (int i = 0; i < origins.size(); i++) {
            Location at = origins.get(i).clone().add(0, 1, 0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> world.strikeLightningEffect(at), step * i);
        }
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

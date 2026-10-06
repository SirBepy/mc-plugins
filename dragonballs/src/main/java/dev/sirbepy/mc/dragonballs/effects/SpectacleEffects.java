package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * The real {@link SummonEffects}: dark stormy sky, a harmless scaled-up dragon circling above the
 * balls, and a flight of glowing heads when the wish is done. Split into {@link SkyEffect},
 * {@link DragonActor} and {@link FlyingBalls} so each piece stays small; this class is just the
 * wiring and the {@link SummonEffects} contract.
 */
public final class SpectacleEffects implements SummonEffects {

    private final JavaPlugin plugin;
    private final SkyEffect sky;
    private final DragonActor dragon;
    private final FlyingBalls flyingBalls;

    public SpectacleEffects(JavaPlugin plugin, BallItems ballItems) {
        this.plugin = plugin;
        this.sky = new SkyEffect(plugin);
        this.dragon = new DragonActor(plugin);
        this.flyingBalls = new FlyingBalls(plugin, ballItems);

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(sky, plugin);
        pm.registerEvents(new DragonGuardListener(), plugin);
        pm.registerEvents(new StrayEntityListener(plugin, dragon, flyingBalls), plugin);
    }

    @Override
    public void onSummon(SummonScene scene) {
        World world = scene.center().getWorld();
        if (world == null) {
            return;
        }
        sky.begin(world);
        sky.thunderOverBalls(world, scene.ballOrigins());
        dragon.spawn(scene.center(), scene.dragon(), EffectsConfig.from(plugin.getConfig()));
    }

    @Override
    public void onSpeak(SummonScene scene, String text) {
        dragon.speak();
    }

    @Override
    public void onEnd(SummonScene scene, Map<Integer, Location> destinations) {
        dragon.despawnWithEffects();
        sky.end();
        flyingBalls.launch(scene.ballOrigins(), destinations, scene.set());
    }

    @Override
    public void shutdown() {
        dragon.despawnImmediate();
        flyingBalls.removeAllImmediately();
        sky.shutdown();
    }
}

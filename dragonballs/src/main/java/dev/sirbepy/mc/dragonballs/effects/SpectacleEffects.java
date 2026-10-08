package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.bedrock.BedrockDragons;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The real {@link SummonEffects}: a per-ball strike sequence, a dark stormy sky (Overworld only -
 * the Nether and End have no sky to darken), a harmless scaled-up dragon circling above the balls
 * with a per-set look, and a flight of glowing heads when the wish is done. Split into
 * {@link SkyEffect}, {@link BallStrikeSequence}, {@link DragonActor} and {@link FlyingBalls} so
 * each piece stays small; this class is just the wiring and the {@link SummonEffects} contract.
 */
public final class SpectacleEffects implements SummonEffects {

    private final JavaPlugin plugin;
    private final SkyEffect sky;
    private final DragonActor dragon;
    private final FlyingBalls flyingBalls;
    private final BallStrikeSequence strikes;

    public SpectacleEffects(JavaPlugin plugin, BallItems ballItems, BedrockDragons bedrockDragons) {
        this(plugin, ballItems, new DragonActor(plugin, BedrockForms.create(plugin)::isBedrock, bedrockDragons));
    }

    /** For tests: MockBukkit has no Floodgate and no {@code Display#setBillboard}. */
    SpectacleEffects(JavaPlugin plugin, BallItems ballItems, Predicate<UUID> isBedrock, Consumer<TextDisplay> faceViewers) {
        this(plugin, ballItems, new DragonActor(plugin, isBedrock, faceViewers));
    }

    private SpectacleEffects(JavaPlugin plugin, BallItems ballItems, DragonActor dragon) {
        this.plugin = plugin;
        this.sky = new SkyEffect(plugin);
        this.dragon = dragon;
        this.flyingBalls = new FlyingBalls(plugin, ballItems);
        this.strikes = new BallStrikeSequence(plugin);

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
        // The Nether and End render no sky, so darkening one there would be a no-op anyway.
        if (scene.set() == BallSet.OVERWORLD) {
            sky.begin(world);
        }
        EffectsConfig config = EffectsConfig.from(plugin.getConfig());
        strikes.play(world, scene.ballOrigins(), scene.set(),
                () -> dragon.spawn(scene.center(), scene.dragon(), scene.set(), config, scene.summoner()));
    }

    @Override
    public void onSpeak(SummonScene scene, String text) {
        dragon.speak();
    }

    @Override
    public void onEnd(SummonScene scene, Map<Integer, Location> destinations) {
        strikes.cancel();
        dragon.despawnWithEffects();
        sky.end();
        flyingBalls.launch(scene.ballOrigins(), destinations, scene.set());
    }

    @Override
    public void shutdown() {
        strikes.cancel();
        dragon.despawnImmediate();
        flyingBalls.removeAllImmediately();
        sky.shutdown();
    }
}

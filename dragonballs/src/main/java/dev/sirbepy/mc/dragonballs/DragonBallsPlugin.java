package dev.sirbepy.mc.dragonballs;

import dev.sirbepy.mc.dragonballs.command.DragonBallsCommand;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.SpectacleEffects;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.listener.BallChunkLoadListener;
import dev.sirbepy.mc.dragonballs.listener.BallContainerGuardListener;
import dev.sirbepy.mc.dragonballs.listener.BallDeathListener;
import dev.sirbepy.mc.dragonballs.listener.BallItemLifecycleListener;
import dev.sirbepy.mc.dragonballs.listener.BallJoinDupeListener;
import dev.sirbepy.mc.dragonballs.listener.BallPickupListener;
import dev.sirbepy.mc.dragonballs.listener.BallPlaceBreakListener;
import dev.sirbepy.mc.dragonballs.listener.BallProtectionListener;
import dev.sirbepy.mc.dragonballs.listener.BallSlowness;
import dev.sirbepy.mc.dragonballs.listener.BallSlownessListener;
import dev.sirbepy.mc.dragonballs.radar.RadarModule;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.scatter.ScatterService;
import dev.sirbepy.mc.dragonballs.station.StationModule;
import dev.sirbepy.mc.dragonballs.summon.SummonModule;
import dev.sirbepy.mc.dragonballs.task.BallMaintenanceTask;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class DragonBallsPlugin extends JavaPlugin {

    private final AtomicReference<DragonBallsConfig> config = new AtomicReference<>();
    private BallRegistry registry;
    private SpectacleEffects effects;
    private SummonModule.Handle summon;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(new DragonBallsConfig(getConfig()));

        BallItems items = new BallItems(this, config::get);
        registry = new BallRegistry(new File(getDataFolder(), "balls.yml"), getLogger(), items);
        registry.setScatterer(new ScatterService(this, config::get, items));

        BallSlowness slowness = new BallSlowness(this, registry, config::get);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new BallPickupListener(registry, config::get), this);
        pm.registerEvents(new BallSlownessListener(slowness), this);
        pm.registerEvents(new BallContainerGuardListener(registry), this);
        pm.registerEvents(new BallDeathListener(registry), this);
        pm.registerEvents(new BallPlaceBreakListener(registry), this);
        pm.registerEvents(new BallProtectionListener(registry), this);
        pm.registerEvents(new BallItemLifecycleListener(registry), this);
        pm.registerEvents(new BallChunkLoadListener(registry), this);
        pm.registerEvents(new BallJoinDupeListener(registry), this);

        getServer().getScheduler().runTaskTimer(this, new BallMaintenanceTask(registry, config::get),
                BallMaintenanceTask.PERIOD_TICKS, BallMaintenanceTask.PERIOD_TICKS);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new DragonBallsCommand(registry, config::get, this::reload).registerInto(event.registrar()));

        RadarModule.register(this, registry, config::get);
        StationModule.register(this, registry, config::get);
        effects = new SpectacleEffects(this, items);
        summon = SummonModule.register(this, registry, config::get, effects);

        bootstrapInitialScatter();
    }

    @Override
    public void onDisable() {
        if (summon != null) {
            summon.shutdown();
        }
        if (effects != null) {
            effects.shutdown();
        }
    }

    /** First-ever enable (no balls.yml yet): scatter all 7 around the overworld's own spawn, read
     * live rather than hardcoded, since the owner's spawn can move. */
    private void bootstrapInitialScatter() {
        World world = getServer().getWorld(config.get().world());
        if (world == null) {
            getLogger().warning("World '" + config.get().world() + "' is not loaded; initial scatter deferred");
            return;
        }
        // Captured before setLastSummon below, which itself writes balls.yml and would otherwise
        // make hasSavedState() look "already set up" even on this very first enable.
        boolean freshInstall = !registry.hasSavedState();
        if (registry.lastSummon() == null) {
            registry.setLastSummon(world.getSpawnLocation());
        }
        if (freshInstall) {
            registry.scatterAll(registry.lastSummon(), false);
        }
    }

    private void reload() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(new DragonBallsConfig(getConfig()));
    }
}

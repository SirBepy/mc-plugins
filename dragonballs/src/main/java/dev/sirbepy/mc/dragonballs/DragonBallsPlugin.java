package dev.sirbepy.mc.dragonballs;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.command.DragonBallsCommand;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.SpectacleEffects;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.listener.BallContainerGuardListener;
import dev.sirbepy.mc.dragonballs.listener.BallDeathListener;
import dev.sirbepy.mc.dragonballs.listener.BallDimensionGuardListener;
import dev.sirbepy.mc.dragonballs.listener.BallItemLifecycleListener;
import dev.sirbepy.mc.dragonballs.listener.BallJoinDupeListener;
import dev.sirbepy.mc.dragonballs.listener.BallLogoutDropListener;
import dev.sirbepy.mc.dragonballs.listener.BallPickupListener;
import dev.sirbepy.mc.dragonballs.listener.BallPlaceBreakListener;
import dev.sirbepy.mc.dragonballs.listener.BallSlowness;
import dev.sirbepy.mc.dragonballs.listener.BallSlownessListener;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.placed.PickupGuard;
import dev.sirbepy.mc.dragonballs.radar.RadarModule;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
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
    private BallSets sets;
    private SpectacleEffects effects;
    private SummonModule.Handle summon;
    private BallLogoutDropListener logoutDrop;
    private FloatingBalls floatingBalls;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(new DragonBallsConfig(getConfig()));

        BallItems items = new BallItems(this, config::get);
        BallRegistry overworldRegistry = new BallRegistry(new File(getDataFolder(), "balls.yml"), getLogger(), items,
                BallSet.OVERWORLD);
        BallRegistry netherRegistry = new BallRegistry(new File(getDataFolder(), "balls-nether.yml"), getLogger(),
                items, BallSet.NETHER);
        BallRegistry endRegistry = new BallRegistry(new File(getDataFolder(), "balls-end.yml"), getLogger(), items,
                BallSet.END);
        ScatterService scatterService = new ScatterService(this, config::get);
        overworldRegistry.setScatterer(scatterService);
        netherRegistry.setScatterer(scatterService);
        endRegistry.setScatterer(scatterService);
        sets = new BallSets(overworldRegistry, netherRegistry, endRegistry, items, config::get);

        BallSlowness slowness = new BallSlowness(this, overworldRegistry, config::get);

        floatingBalls = new FloatingBalls(this, items, BedrockForms.create(this)::isBedrock);
        for (BallRegistry registry : sets.all()) {
            registry.setLook(floatingBalls);
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(floatingBalls, this);
        pm.registerEvents(new BallPickupListener(sets, config::get), this);
        pm.registerEvents(new BallSlownessListener(slowness), this);
        pm.registerEvents(new BallContainerGuardListener(overworldRegistry), this);
        pm.registerEvents(new BallDeathListener(sets), this);
        pm.registerEvents(new BallPlaceBreakListener(sets, floatingBalls, PickupGuard.load(this)), this);
        pm.registerEvents(new BallItemLifecycleListener(sets), this);
        pm.registerEvents(new BallJoinDupeListener(sets), this);
        pm.registerEvents(new BallDimensionGuardListener(sets, config::get, slowness), this);
        logoutDrop = new BallLogoutDropListener(sets, config::get, slowness);
        pm.registerEvents(logoutDrop, this);

        getServer().getScheduler().runTaskTimer(this, new BallMaintenanceTask(sets, config::get,
                logoutDrop::dropForOfflineHolders), 1L, BallMaintenanceTask.PERIOD_TICKS);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new DragonBallsCommand(sets, config::get, this::reload).registerInto(event.registrar()));

        RadarModule.register(this, sets, config::get);
        StationModule.register(this, sets, floatingBalls, config::get);
        effects = new SpectacleEffects(this, items);
        summon = SummonModule.register(this, sets, floatingBalls, config::get, effects);

        floatingBalls.start();
        getServer().getScheduler().runTask(this, () -> sets.all().forEach(BallRegistry::showPlaced));
        bootstrapInitialScatter();
    }

    @Override
    public void onDisable() {
        if (logoutDrop != null && getServer().isStopping()) {
            getServer().getOnlinePlayers().forEach(logoutDrop::dropCarried);
        }
        if (summon != null) {
            summon.shutdown();
        }
        if (effects != null) {
            effects.shutdown();
        }
        if (floatingBalls != null) {
            floatingBalls.shutdown();
        }
    }

    /**
     * First-ever enable of a set (no balls file yet): scatter all 7 around that set's own home
     * world's spawn, read live rather than hardcoded, since the owner's spawn can move. Runs for
     * every set every enable (not just the server's first ever): the End starts out not-yet-loaded
     * on most installs, so this is also how it gets its first scatter the moment it opens.
     */
    private void bootstrapInitialScatter() {
        for (BallSet set : BallSet.values()) {
            bootstrapInitialScatter(set);
        }
    }

    private void bootstrapInitialScatter(BallSet set) {
        if (!sets.isEnabled(set)) {
            return;
        }
        BallRegistry registry = sets.registry(set);
        World world = set.world(config.get());
        if (world == null) {
            getLogger().warning("World for the " + set.id() + " Dragon Balls is not loaded; initial scatter deferred");
            return;
        }
        // Captured before setLastSummon below, which itself writes the balls file and would
        // otherwise make hasSavedState() look "already set up" even on this very first enable.
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

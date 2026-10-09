package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.endfight.arena.ArenaListener;
import dev.sirbepy.mc.endfight.arena.ArenaProtection;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.ArenaService;
import dev.sirbepy.mc.endfight.arena.EndWorldLoadListener;
import dev.sirbepy.mc.endfight.arena.RespawnRebuildListener;
import dev.sirbepy.mc.endfight.command.EndFightCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicReference;

public final class EndFightPlugin extends JavaPlugin {

    private final AtomicReference<EndFightConfig> config = new AtomicReference<>();
    private ArenaRegistry registry;
    private ArenaService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();

        registry = new ArenaRegistry();
        service = new ArenaService(this, registry);
        service.restoreFromState();

        getServer().getPluginManager().registerEvents(new ArenaProtection(registry), this);
        getServer().getPluginManager().registerEvents(
                new RespawnRebuildListener(this, () -> service.buildNow()), this);
        getServer().getPluginManager().registerEvents(
                new EndWorldLoadListener(world -> autoBuildIfEnabled()), this);

        if (config.get().autoBuild()) {
            autoBuildIfEnabled();
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new EndFightCommand(service, config::get, this::reload).registerInto(event.registrar()));
    }

    /** Lets a later phase (the illager cult) hear about every (re)build without this plugin
     * knowing it exists. */
    public void addArenaListener(ArenaListener listener) {
        registry.registerListener(listener);
    }

    private void autoBuildIfEnabled() {
        if (config.get().autoBuild()) {
            service.autoBuildIfNeeded();
        }
    }

    private void reload() {
        reloadConfig();
        config.set(new EndFightConfig(getConfig()));
    }
}

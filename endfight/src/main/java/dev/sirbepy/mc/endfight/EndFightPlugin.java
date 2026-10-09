package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.endfight.arena.ArenaListener;
import dev.sirbepy.mc.endfight.arena.ArenaProtection;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.ArenaService;
import dev.sirbepy.mc.endfight.arena.EndWorldLoadListener;
import dev.sirbepy.mc.endfight.arena.RespawnRebuildListener;
import dev.sirbepy.mc.endfight.command.EndFightCommand;
import dev.sirbepy.mc.endfight.reward.DragonDeathListener;
import dev.sirbepy.mc.endfight.reward.PendingRewardsStore;
import dev.sirbepy.mc.endfight.reward.RewardJoinListener;
import dev.sirbepy.mc.endfight.reward.RewardService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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

        // --- Rewards (phase 3): who gets paid for fighting the dragon - see docs/endfight.md -----
        PendingRewardsStore pendingRewards = new PendingRewardsStore(new File(getDataFolder(), "pending-rewards.yml"), getLogger());
        RewardService rewards = new RewardService(this, config::get, pendingRewards);
        getServer().getPluginManager().registerEvents(new DragonDeathListener(rewards), this);
        getServer().getPluginManager().registerEvents(new RewardJoinListener(this, rewards), this);
        // --- end rewards ---------------------------------------------------------------------------

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new EndFightCommand(service, config::get, this::reload, rewards).registerInto(event.registrar()));
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

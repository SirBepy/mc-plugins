package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.endfight.altar.AltarInteractListener;
import dev.sirbepy.mc.endfight.altar.AltarLocator;
import dev.sirbepy.mc.endfight.altar.AltarMenu;
import dev.sirbepy.mc.endfight.altar.AuraMenu;
import dev.sirbepy.mc.endfight.altar.WeeklyHoardInfo;
import dev.sirbepy.mc.endfight.arena.ArenaListener;
import dev.sirbepy.mc.endfight.arena.ArenaProtection;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.ArenaService;
import dev.sirbepy.mc.endfight.arena.EndWorldLoadListener;
import dev.sirbepy.mc.endfight.arena.RespawnRebuildListener;
import dev.sirbepy.mc.endfight.command.EndFightCommand;
import dev.sirbepy.mc.endfight.cult.CultCommand;
import dev.sirbepy.mc.endfight.cult.CultFriendlyFire;
import dev.sirbepy.mc.endfight.cult.CultGuards;
import dev.sirbepy.mc.endfight.cult.CultWaves;
import dev.sirbepy.mc.endfight.perch.PerchGateListener;
import dev.sirbepy.mc.endfight.reward.DragonDeathListener;
import dev.sirbepy.mc.endfight.reward.PendingRewardsStore;
import dev.sirbepy.mc.endfight.reward.RewardJoinListener;
import dev.sirbepy.mc.endfight.reward.RewardService;
import dev.sirbepy.mc.endfight.tier.BukkitDkePresence;
import dev.sirbepy.mc.endfight.tier.BukkitEndDragonBattleHistory;
import dev.sirbepy.mc.endfight.tier.ConsoleCommandSink;
import dev.sirbepy.mc.endfight.tier.DkeTierPusher;
import dev.sirbepy.mc.endfight.tier.TierApplyListener;
import dev.sirbepy.mc.endfight.tier.TierKillTracker;
import dev.sirbepy.mc.endfight.tier.TierService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class EndFightPlugin extends JavaPlugin {

    private final AtomicReference<EndFightConfig> config = new AtomicReference<>();
    private ArenaRegistry registry;
    private ArenaService service;
    private CultGuards cultGuards;

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

        // Phase 2: the illager cult guarding the arena, and the perch gate keeping the dragon off
        // the exit portal while any pillar crystal survives. Each has its own config.yml switch.
        cultGuards = new CultGuards(this, registry, () -> config.get().cult());
        cultGuards.start();
        getServer().getPluginManager().registerEvents(cultGuards, this);
        getServer().getPluginManager().registerEvents(
                new CultWaves(registry, () -> config.get().cult(), getLogger()), this);
        getServer().getPluginManager().registerEvents(new CultFriendlyFire(), this);
        getServer().getPluginManager().registerEvents(
                new PerchGateListener(registry, () -> config.get().perchGate()), this);

        // Phase 3: who gets paid for fighting the dragon, see docs/endfight.md.
        PendingRewardsStore pendingRewards = new PendingRewardsStore(new File(getDataFolder(), "pending-rewards.yml"), getLogger());
        RewardService rewards = new RewardService(this, config::get, pendingRewards);
        getServer().getPluginManager().registerEvents(new DragonDeathListener(rewards), this);
        getServer().getPluginManager().registerEvents(new RewardJoinListener(this, rewards), this);

        // --- Altar & tiers: the altar picks the next fight's DKE/HP tier - see docs/endfight.md --
        TierService tiers = new TierService(new File(getDataFolder(), "tier.yml"), config.get().defaultTier(),
                new BukkitEndDragonBattleHistory(service::endWorld), getLogger());
        DkeTierPusher dkePusher = new DkeTierPusher(
                new BukkitDkePresence(() -> config.get().dkeForceInstalled(), getLogger()),
                new ConsoleCommandSink(), getLogger());
        TierApplyListener tierApplyListener = new TierApplyListener(this, tiers, dkePusher, config.get().tierApplyDelayTicks());
        getServer().getPluginManager().registerEvents(tierApplyListener, this);
        getServer().getPluginManager().registerEvents(new TierKillTracker(tiers), this);
        tierApplyListener.applyToExistingDragon();

        AltarLocator altarLocator = new AltarLocator(registry);
        AltarMenu altarMenu = new AltarMenu(tiers, config::get, WeeklyHoardInfo.NONE_TAKEN, AuraMenu.NONE);
        getServer().getPluginManager().registerEvents(new AltarInteractListener(altarLocator, altarMenu), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        // --- end altar & tiers -----------------------------------------------------------------

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            EndFightCommand command = new EndFightCommand(service, config::get, this::reload, rewards, tiers);
            command.registerInto(event.registrar(), new CultCommand(cultGuards));
        });
    }

    @Override
    public void onDisable() {
        if (cultGuards != null) {
            cultGuards.stop();
        }
    }

    /** Lets a later phase hear about every (re)build without this plugin knowing it exists. */
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

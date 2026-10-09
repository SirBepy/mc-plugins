package dev.sirbepy.mc.endfight;

import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.endfight.altar.AltarAuraMenu;
import dev.sirbepy.mc.endfight.altar.AltarInteractListener;
import dev.sirbepy.mc.endfight.altar.AltarLocator;
import dev.sirbepy.mc.endfight.altar.AltarMenu;
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
import dev.sirbepy.mc.endfight.reward.DragonAuraCommand;
import dev.sirbepy.mc.endfight.reward.DragonAuraJoinListener;
import dev.sirbepy.mc.endfight.reward.DragonAuraService;
import dev.sirbepy.mc.endfight.reward.DragonDeathListener;
import dev.sirbepy.mc.endfight.reward.PendingRewardsStore;
import dev.sirbepy.mc.endfight.reward.RewardJoinListener;
import dev.sirbepy.mc.endfight.reward.RewardProgressStore;
import dev.sirbepy.mc.endfight.reward.RewardService;
import dev.sirbepy.mc.endfight.reward.WeekKeys;
import dev.sirbepy.mc.endfight.tier.BukkitDkePresence;
import dev.sirbepy.mc.endfight.tier.BukkitEndDragonBattleHistory;
import dev.sirbepy.mc.endfight.tier.ConsoleCommandSink;
import dev.sirbepy.mc.endfight.tier.DkeTierPusher;
import dev.sirbepy.mc.endfight.tier.TierApplyListener;
import dev.sirbepy.mc.endfight.tier.TierKillTracker;
import dev.sirbepy.mc.endfight.tier.TierService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class EndFightPlugin extends JavaPlugin {

    private static final long AURA_TICK_PERIOD = 10L;

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

        // Tiers: the altar picks the next fight's tier, pushed into Dragonkind Evolved (or the
        // dragon's health) when it spawns, and read by the rewards when it dies.
        TierService tiers = new TierService(new File(getDataFolder(), "tier.yml"), config.get().defaultTier(),
                new BukkitEndDragonBattleHistory(service::endWorld), getLogger());
        DkeTierPusher dkePusher = new DkeTierPusher(
                new BukkitDkePresence(() -> config.get().dkeForceInstalled(), getLogger()),
                new ConsoleCommandSink(), getLogger());
        TierApplyListener tierApplyListener = new TierApplyListener(this, tiers, dkePusher, config.get().tierApplyDelayTicks());
        getServer().getPluginManager().registerEvents(tierApplyListener, this);
        getServer().getPluginManager().registerEvents(
                new TierKillTracker(tiers, task -> getServer().getScheduler().runTask(this, task)), this);
        tierApplyListener.applyToExistingDragon();

        // Phase 3: who gets paid for fighting the dragon, see docs/endfight.md.
        PendingRewardsStore pendingRewards = new PendingRewardsStore(new File(getDataFolder(), "pending-rewards.yml"), getLogger());
        RewardProgressStore rewardProgress = new RewardProgressStore(new File(getDataFolder(), "rewards.yml"), getLogger());
        Clock clock = Clock.systemUTC();
        RewardService rewards = new RewardService(this, config::get, pendingRewards, rewardProgress, tiers,
                new Random(), clock);
        getServer().getPluginManager().registerEvents(new DragonDeathListener(rewards), this);
        getServer().getPluginManager().registerEvents(new RewardJoinListener(this, rewards), this);

        DragonAuraService auras = new DragonAuraService(rewardProgress);
        getServer().getPluginManager().registerEvents(new DragonAuraJoinListener(auras), this);
        getServer().getScheduler().runTaskTimer(this, () -> auras.tickAll(Bukkit.getOnlinePlayers()),
                AURA_TICK_PERIOD, AURA_TICK_PERIOD);

        // The altar: tier menu, each tier's weekly hoard status for the player, and the aura menu.
        WeeklyHoardInfo weeklyHoards = (player, tier) -> WeekKeys.current(clock)
                .equals(rewardProgress.load(player.getUniqueId()).weeklyHoards().get(tier));
        AltarMenu altarMenu = new AltarMenu(tiers, config::get, weeklyHoards, new AltarAuraMenu(auras));
        getServer().getPluginManager().registerEvents(new AltarInteractListener(new AltarLocator(registry), altarMenu), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            EndFightCommand command = new EndFightCommand(service, config::get, this::reload, rewards, tiers);
            command.registerInto(event.registrar(), new CultCommand(cultGuards));
            new DragonAuraCommand(auras).registerInto(event.registrar());
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

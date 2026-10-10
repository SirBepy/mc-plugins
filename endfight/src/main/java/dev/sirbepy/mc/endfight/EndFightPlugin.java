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
import dev.sirbepy.mc.endfight.cult.CrystalWardens;
import dev.sirbepy.mc.endfight.cult.CultCounter;
import dev.sirbepy.mc.endfight.cult.CultFriendlyFire;
import dev.sirbepy.mc.endfight.cult.CultGuards;
import dev.sirbepy.mc.endfight.cult.CultWaves;
import dev.sirbepy.mc.endfight.cult.WaveSpawner;
import dev.sirbepy.mc.endfight.fight.FightStatsListener;
import dev.sirbepy.mc.endfight.opening.EndGateListener;
import dev.sirbepy.mc.endfight.opening.EndOpening;
import dev.sirbepy.mc.endfight.opening.OpeningCommand;
import dev.sirbepy.mc.endfight.opening.StrongholdWarp;
import dev.sirbepy.mc.endfight.perch.PerchGateListener;
import dev.sirbepy.mc.endfight.reset.BukkitDragonBattleView;
import dev.sirbepy.mc.endfight.reset.IslandResetService;
import dev.sirbepy.mc.endfight.reset.IslandRestorer;
import dev.sirbepy.mc.endfight.reset.IslandSnapshotStore;
import dev.sirbepy.mc.endfight.reset.IslandSnapshotTaker;
import dev.sirbepy.mc.endfight.reset.ResetCommand;
import dev.sirbepy.mc.endfight.reset.RespawnWatcher;
import dev.sirbepy.mc.endfight.reward.DragonAuraCommand;
import dev.sirbepy.mc.endfight.reward.DragonAuraJoinListener;
import dev.sirbepy.mc.endfight.reward.DragonAuraService;
import dev.sirbepy.mc.endfight.reward.DragonDeathListener;
import dev.sirbepy.mc.endfight.reward.DragonPresence;
import dev.sirbepy.mc.endfight.reward.DragonPresenceStore;
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
import org.bukkit.World;
import org.bukkit.entity.Player;
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
    private WaveSpawner waveSpawner;
    private CultCounter cultCounter;
    private CrystalWardens crystalWardens;
    private RespawnWatcher respawnWatcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();

        registry = new ArenaRegistry();
        service = new ArenaService(this, registry);
        service.restoreFromState();

        // Island reset (Joe, 2026-10-09): every new dragon summon gets the whole main island back
        // to right after the first build, not just a rebuild of our own structures. See
        // docs/endfight.md's "Summoning a new dragon" section.
        IslandSnapshotStore snapshotStore = new IslandSnapshotStore(
                new File(getDataFolder(), "island-snapshot.bin"), getLogger());
        IslandSnapshotTaker snapshotTaker = new IslandSnapshotTaker(this, snapshotStore);
        IslandResetService resetService = new IslandResetService(snapshotTaker, snapshotStore, getLogger());
        service.setOnFirstBuild(resetService::onFirstBuild);
        AtomicReference<RespawnWatcher> watcherRef = new AtomicReference<>();

        getServer().getPluginManager().registerEvents(new ArenaProtection(registry), this);
        getServer().getPluginManager().registerEvents(
                new RespawnRebuildListener(this, () -> service.buildNow(),
                        () -> config.get().reset().enabled() && snapshotStore.exists()), this);
        getServer().getPluginManager().registerEvents(
                new EndWorldLoadListener(world -> autoBuildIfEnabled()), this);

        if (config.get().autoBuild()) {
            autoBuildIfEnabled();
        }

        // Phase 2: the illager cult guarding the arena, and the perch gate keeping the dragon off
        // the exit portal while any pillar crystal survives. Each has its own config.yml switch.
        cultGuards = new CultGuards(this, registry, () -> config.get().cult(),
                () -> watcherRef.get() != null && watcherRef.get().busy());
        cultGuards.start();
        getServer().getPluginManager().registerEvents(cultGuards, this);
        // Waves come out a few mobs a tick, and wait while the server is over its tick budget.
        waveSpawner = new WaveSpawner(this, registry, () -> config.get().cult(), () -> getServer().getAverageTickTime());
        waveSpawner.start();
        getServer().getPluginManager().registerEvents(waveSpawner, this);
        getServer().getPluginManager().registerEvents(new CultWaves(() -> config.get().cult(), waveSpawner), this);
        crystalWardens = new CrystalWardens(this, registry, () -> config.get().cult(),
                () -> watcherRef.get() != null && watcherRef.get().busy());
        crystalWardens.start();
        cultCounter = new CultCounter(this, () -> config.get().cult());
        cultCounter.start();
        getServer().getPluginManager().registerEvents(cultCounter, this);
        getServer().getPluginManager().registerEvents(new CultFriendlyFire(), this);
        getServer().getPluginManager().registerEvents(
                new PerchGateListener(registry, () -> config.get().perchGate()), this);

        // The reset's own restore + watcher: replaces RespawnRebuildListener's rebuild whenever a
        // snapshot exists and reset is enabled (see RespawnRebuildListener's skipIfResetActive
        // guard above); otherwise it stays inert and that listener behaves exactly as before.
        IslandRestorer restorer = new IslandRestorer(this, () -> config.get().reset(), cultGuards::clearAll,
                service::restoreFromState, world -> announceToEnd(world, "arena-ready"));
        respawnWatcher = new RespawnWatcher(this, new BukkitDragonBattleView(), () -> config.get().reset(),
                snapshotStore::exists, snapshotStore::load, restorer, world -> announceToEnd(world, "lockout-start"));
        watcherRef.set(respawnWatcher);
        getServer().getPluginManager().registerEvents(respawnWatcher, this);
        respawnWatcher.start();

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
        // Per-player damage and kills this fight: the 50% wave's phantoms and the extra rewards.
        getServer().getPluginManager().registerEvents(new FightStatsListener(rewards.fighters(), registry::pillars), this);
        getServer().getPluginManager().registerEvents(new RewardJoinListener(this, rewards), this);

        DragonAuraService auras = new DragonAuraService(rewardProgress);
        getServer().getPluginManager().registerEvents(new DragonAuraJoinListener(auras), this);
        // Dragon's Presence (Joe, 2026-10-09): the automatic full-set aura, independent of the won
        // auras above - ticks alongside them, on its own opt-out store since it isn't unlocked.
        DragonPresence presence = new DragonPresence(
                new DragonPresenceStore(new File(getDataFolder(), "presence.yml"), getLogger()),
                new Random(), DragonPresence.ParticleSink.BUKKIT);
        getServer().getScheduler().runTaskTimer(this, () -> {
            auras.tickAll(Bukkit.getOnlinePlayers());
            presence.tickAll(Bukkit.getOnlinePlayers());
        }, AURA_TICK_PERIOD, AURA_TICK_PERIOD);

        // The altar: tier menu, each tier's weekly hoard status for the player, and the aura menu.
        WeeklyHoardInfo weeklyHoards = (player, tier) -> WeekKeys.current(clock)
                .equals(rewardProgress.load(player.getUniqueId()).weeklyHoards().get(tier));
        AltarMenu altarMenu = new AltarMenu(tiers, config::get, weeklyHoards, new AltarAuraMenu(auras, presence));
        getServer().getPluginManager().registerEvents(new AltarInteractListener(new AltarLocator(registry), altarMenu), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        // The End's opening: portals shut until opening.yml's time, a countdown, and /warp end.
        EndOpening opening = new EndOpening(new File(getDataFolder(), "opening.yml"), config.get().countdownMinutes(),
                config.get().warpHintMinutes(), config.get().openingZone(), getLogger());
        EndGateListener gate = new EndGateListener(opening, () -> config.get().openingMessages(), clock);
        getServer().getPluginManager().registerEvents(gate, this);
        getServer().getScheduler().runTaskTimer(this, gate::tick, 20L, 20L);
        StrongholdWarp warp = new StrongholdWarp(this, () -> config.get().openingMessages());
        getServer().getPluginManager().registerEvents(warp, this);
        // Finds the portal room once at boot, so the first /warp end doesn't wait on chunk loads.
        getServer().getScheduler().runTaskLater(this, () -> warp.locate(), 200L);
        OpeningCommand openingCommand = new OpeningCommand(opening, clock);

        ResetCommand resetCommand = new ResetCommand(resetService, respawnWatcher, restorer, snapshotStore, service::endWorld);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            EndFightCommand command = new EndFightCommand(service, config::get, this::reload, rewards, tiers);
            command.registerInto(event.registrar(), new CultCommand(cultGuards, cultCounter, waveSpawner), resetCommand,
                    openingCommand);
            new DragonAuraCommand(auras, presence).registerInto(event.registrar());
        });
    }

    @Override
    public void onDisable() {
        if (cultGuards != null) {
            cultGuards.stop();
        }
        if (waveSpawner != null) {
            waveSpawner.stop();
        }
        if (cultCounter != null) {
            cultCounter.stop();
        }
        if (crystalWardens != null) {
            crystalWardens.stop();
        }
        if (respawnWatcher != null) {
            respawnWatcher.stop();
        }
    }

    private void announceToEnd(World world, String messageKey) {
        for (Player player : world.getPlayers()) {
            config.get().reset().messages().send(player, messageKey);
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

package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.names.api.NamesApi;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.command.TagCommands;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadProtectionListener;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.listener.AfkActivityListener;
import dev.sirbepy.mc.tag.listener.PlayerConnectionListener;
import dev.sirbepy.mc.tag.listener.TagDamageListener;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import dev.sirbepy.mc.tag.task.AfkSamplingTask;
import dev.sirbepy.mc.tag.task.HeadWatchdogTask;
import dev.sirbepy.mc.tag.task.PointsTask;
import dev.sirbepy.mc.tag.task.TrackerTask;
import dev.sirbepy.mc.tag.tracker.TrackerListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public final class TagPlugin extends JavaPlugin {

    private NamesApi names;
    private HeadService heads;
    private PrefixService prefix;
    private ScoreboardService boards;
    private LeaderboardEntityService leaderboard;
    private LogService logService;
    private TagService service;

    private BukkitTask pointsTask;
    private BukkitTask watchdogTask;
    private BukkitTask afkTask;
    private BukkitTask trackerTask;

    @Override
    public void onLoad() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(new TagCommands(this).build(), "Tag: a server-wide game of tag"));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ServicesManager servicesManager = Bukkit.getServicesManager();
        names = servicesManager.load(NamesApi.class);
        if (names == null) {
            getLogger().severe("Names API not found (Names did not enable?). Disabling Tag.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        heads = new HeadService(this);
        prefix = new PrefixService(names);
        boards = new ScoreboardService(names);
        logService = new LogService(getConfig().getConfigurationSection("logs"));
        TagConfig config = TagConfig.load(getConfig(), logService);
        // service isn't constructed yet, but the lambda reads the field lazily on first event
        // fire (chunk load, plugin enable below), by which point it's always set.
        leaderboard = new LeaderboardEntityService(this, config.leaderboard, () -> service.state());

        service = new TagService(this, new File(getDataFolder(), "state.yml"), heads, prefix, boards, leaderboard, config);

        getServer().getPluginManager().registerEvents(new TagDamageListener(service), this);
        getServer().getPluginManager().registerEvents(new HeadProtectionListener(heads), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(service), this);
        getServer().getPluginManager().registerEvents(new AfkActivityListener(service), this);
        getServer().getPluginManager().registerEvents(new TrackerListener(service, service.tracker()), this);
        getServer().getPluginManager().registerEvents(leaderboard, this);

        reapplyCurrentIt();
        leaderboard.ensureSpawned();
        scheduleTasks(config);
    }

    @Override
    public void onDisable() {
        if (pointsTask != null) {
            pointsTask.cancel();
        }
        if (watchdogTask != null) {
            watchdogTask.cancel();
        }
        if (afkTask != null) {
            afkTask.cancel();
        }
        if (trackerTask != null) {
            trackerTask.cancel();
        }
        if (leaderboard != null) {
            leaderboard.despawn();
        }
        if (service != null) {
            service.save();
        }
    }

    public void reload() {
        reloadConfig();
        TagConfig config = TagConfig.load(getConfig(), logService);
        service.setConfig(config);
        leaderboard.setConfig(config.leaderboard);
        leaderboard.refresh(service.state());
        reapplyCurrentIt();
        if (pointsTask != null) {
            pointsTask.cancel();
        }
        if (watchdogTask != null) {
            watchdogTask.cancel();
        }
        if (afkTask != null) {
            afkTask.cancel();
            afkTask = null;
        }
        if (trackerTask != null) {
            trackerTask.cancel();
            trackerTask = null;
        }
        scheduleTasks(config);
    }

    private void reapplyCurrentIt() {
        var currentIt = service != null ? service.state().currentIt : null;
        if (currentIt == null) {
            return;
        }
        var online = getServer().getPlayer(currentIt);
        if (online != null) {
            prefix.apply(currentIt, service.config().prefixTemplate, service.config().prefixPriority);
            heads.ensureWorn(online, service.config().head);
            service.syncTracker(online);
        }
    }

    private void scheduleTasks(TagConfig config) {
        long pointsTicks = Math.max(1, config.pointsIntervalSeconds * 20L);
        pointsTask = getServer().getScheduler().runTaskTimer(this, new PointsTask(service), pointsTicks, pointsTicks);

        long watchdogTicks = Math.max(1, config.headWatchdogSeconds * 20L);
        watchdogTask = getServer().getScheduler().runTaskTimer(this, new HeadWatchdogTask(service), watchdogTicks, watchdogTicks);

        if (config.afk.enabled) {
            long afkTicks = Math.max(1, config.afk.checkIntervalSeconds * 20L);
            afkTask = getServer().getScheduler().runTaskTimer(this, new AfkSamplingTask(service), afkTicks, afkTicks);
        }

        if (config.tracker.enabled) {
            long trackerTicks = Math.max(1, config.tracker.updateIntervalTicks);
            trackerTask = getServer().getScheduler().runTaskTimer(this, new TrackerTask(service), trackerTicks, trackerTicks);
        }
    }

    public TagService service() {
        return service;
    }

    public ScoreboardService boards() {
        return boards;
    }
}

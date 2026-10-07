package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.tag.afk.AfkService;
import dev.sirbepy.mc.tag.afk.AfkTracker;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.event.TagEventService;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.logic.PendingAction;
import dev.sirbepy.mc.tag.logic.PendingActions;
import dev.sirbepy.mc.tag.points.PointsTickService;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import dev.sirbepy.mc.tag.state.PlayerStats;
import dev.sirbepy.mc.tag.state.StateRepository;
import dev.sirbepy.mc.tag.state.TagState;
import dev.sirbepy.mc.tag.tracker.TrackerService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Central orchestrator: the single place that mutates {@link TagState} and keeps it persisted. */
public final class TagService {

    private final JavaPlugin plugin;
    private final File stateFile;
    private final HeadService heads;
    private final PrefixService prefix;
    private final ScoreboardService boards;
    private final LeaderboardEntityService leaderboard;

    private TagState state;
    private TagConfig config;
    private final AfkService afkService;
    private final TrackerService tracker;
    private final TagEventService tagEvents = new TagEventService();
    private final PointsTickService pointsTickService = new PointsTickService();

    public TagService(JavaPlugin plugin, File stateFile, HeadService heads, PrefixService prefix,
                       ScoreboardService boards, LeaderboardEntityService leaderboard, TagConfig config) {
        this(plugin, stateFile, heads, prefix, boards, leaderboard, config,
                new AfkTracker(System::currentTimeMillis, config.afk.thresholdSeconds * 1000L));
    }

    // Package-private: lets tests inject a fake-clock AfkTracker instead of sleeping through real `afk.*-seconds` durations.
    TagService(JavaPlugin plugin, File stateFile, HeadService heads, PrefixService prefix,
               ScoreboardService boards, LeaderboardEntityService leaderboard, TagConfig config, AfkTracker afkTracker) {
        this.plugin = plugin;
        this.stateFile = stateFile;
        this.heads = heads;
        this.prefix = prefix;
        this.boards = boards;
        this.leaderboard = leaderboard;
        this.config = config;
        this.afkService = new AfkService(afkTracker);
        this.tracker = new TrackerService(plugin);
        this.state = loadOrNew();
    }

    private TagState loadOrNew() {
        try {
            return StateRepository.load(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load state.yml, starting fresh", e);
            return new TagState();
        }
    }

    public TagState state() {
        return state;
    }

    public TagConfig config() {
        return config;
    }

    public TrackerService tracker() {
        return tracker;
    }

    public void setConfig(TagConfig config) {
        this.config = config;
        afkService.setThresholdMillis(config.afk.thresholdSeconds * 1000L);
    }

    public void save() {
        try {
            StateRepository.save(state, stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save state.yml", e);
        }
    }

    public boolean isIt(UUID uuid) {
        return uuid != null && uuid.equals(state.currentIt);
    }

    public boolean isOptedOut(UUID uuid) {
        return state.optedOut.contains(uuid);
    }

    // --- opt in/out -----------------------------------------------------------------------

    public OptResult optOut(Player player) {
        if (isIt(player.getUniqueId())) {
            return OptResult.IS_IT;
        }
        if (!state.optedOut.add(player.getUniqueId())) {
            return OptResult.ALREADY;
        }
        save();
        config.logs.fire(LogService.Event.OPT_OUT, Placeholder.unparsed("player", player.getName()));
        return OptResult.OK;
    }

    public OptResult optIn(Player player) {
        if (!state.optedOut.remove(player.getUniqueId())) {
            return OptResult.ALREADY;
        }
        save();
        config.logs.fire(LogService.Event.OPT_IN, Placeholder.unparsed("player", player.getName()));
        return OptResult.OK;
    }

    public enum OptResult { OK, ALREADY, IS_IT }

    // --- admin set/clear --------------------------------------------------------------------

    public void adminSet(CommandSender admin, OfflinePlayer target) {
        UUID oldIt = state.currentIt;
        UUID newIt = target.getUniqueId();
        if (oldIt != null && oldIt.equals(newIt)) {
            return;
        }
        transferIt(oldIt, newIt, null, 0L, () -> config.logs.fire(LogService.Event.IT_SET,
                Placeholder.unparsed("it", nameOf(newIt)), Placeholder.unparsed("player", admin.getName())));
    }

    public void adminClear(CommandSender admin) {
        UUID oldIt = state.currentIt;
        if (oldIt == null) {
            return;
        }
        transferIt(oldIt, null, null, 0L,
                () -> config.logs.fire(LogService.Event.IT_CLEARED, Placeholder.unparsed("player", admin.getName())));
    }

    /**
     * Revoke/reassign/crown-or-queue/persist/log/refresh: the shared mutation path for every IT
     * change (tag, admin set/clear, AFK auto-pass). Either id may be null.
     */
    private void transferIt(UUID oldIt, UUID newIt, UUID lastTagger, long tagbackExpiryMillis, Runnable logEvent) {
        if (oldIt != null) {
            retireIt(oldIt);
        }
        state.currentIt = newIt;
        state.lastTagger = lastTagger;
        state.tagbackExpiryMillis = tagbackExpiryMillis;
        save();

        if (newIt != null) {
            Player online = Bukkit.getPlayer(newIt);
            if (online != null) {
                crownIt(online);
            } else {
                state.pending.put(newIt, PendingAction.BECOME_IT);
                save();
            }
        }

        logEvent.run();
        boards.refreshAll(state);
        leaderboard.refresh(state);
    }

    private void retireIt(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            revokeItTrappings(online);
        } else {
            state.pending.put(uuid, PendingAction.CLEAR_IT);
        }
    }

    private void crownIt(Player player) {
        heads.grant(player, config.head);
        syncTracker(player);
        prefix.apply(player.getUniqueId(), config.prefixTemplate, config.prefixPriority);
    }

    private void revokeItTrappings(Player player) {
        heads.revoke(player);
        tracker.revoke(player);
        prefix.remove(player.getUniqueId());
    }

    // --- tag event ---------------------------------------------------------------------------

    /** Checks (not-it/opted-out/self/AFK/tagback) live in {@link TagEventService}; this applies the result. */
    public TagResult attemptTag(Player it, Player victim) {
        return tagEvents.attempt(it, victim, config, this::isIt, this::isOptedOut, this::isAfk,
                state.lastTagger, state.tagbackExpiryMillis, this::performTag);
    }

    /** Mutates TagState for a landed tag; {@link TagEventService} calls this once every check passes. */
    private void performTag(Player it, Player victim, long now) {
        UUID oldIt = it.getUniqueId();
        UUID newIt = victim.getUniqueId();

        PlayerStats victimStats = state.statsFor(newIt);
        victimStats.timesTagged++;
        victimStats.points += config.pointsOnTag;
        state.statsFor(oldIt).tagsMade++;

        transferIt(oldIt, newIt, oldIt, now + config.tagbackSeconds * 1000L,
                () -> config.logs.fire(LogService.Event.TAG,
                        Placeholder.unparsed("tagger", it.getName()),
                        Placeholder.unparsed("tagged", victim.getName()),
                        Placeholder.unparsed("it", victim.getName())));
    }

    public enum TagResult { OK, NOT_IT, OPTED_OUT, SELF, AFK, TAGBACK_BLOCKED }

    // --- join/quit -----------------------------------------------------------------------

    public void onJoin(Player player) {
        // A relogging IT must never be instantly treated as idle: joining always counts as activity.
        markActive(player.getUniqueId());

        Optional<PendingAction> pending = PendingActions.take(state.pending, player.getUniqueId());
        pending.ifPresent(action -> {
            if (action == PendingAction.BECOME_IT) {
                crownIt(player);
            } else {
                revokeItTrappings(player);
            }
            save();
        });

        if (isIt(player.getUniqueId())) {
            prefix.apply(player.getUniqueId(), config.prefixTemplate, config.prefixPriority);
            heads.ensureWorn(player, config.head);
            syncTracker(player);
            config.logs.fire(LogService.Event.IT_JOINED, Placeholder.unparsed("it", player.getName()));
        } else {
            tracker.purge(player);
        }
    }

    public void onQuit(Player player) {
        if (isIt(player.getUniqueId())) {
            config.logs.fire(LogService.Event.IT_QUIT, Placeholder.unparsed("it", player.getName()));
        }
        boards.disable(player);
        tagEvents.forget(player.getUniqueId());
        afkService.remove(player.getUniqueId());
        tracker.forget(player.getUniqueId());
    }

    // --- tracker compass -----------------------------------------------------------------------

    public void syncTracker(Player it) {
        tracker.sync(it, config.tracker);
    }

    public void trackerTick() {
        Player it = onlineIt();
        if (it == null || !config.tracker.enabled) {
            return;
        }
        tracker.tick(it, config.tracker, this::isAfk, this::isOptedOut);
    }

    public void cycleTracker(Player player) {
        if (!isIt(player.getUniqueId()) || !config.tracker.enabled) {
            return;
        }
        tracker.cycleAndUpdate(player, config.tracker, this::isAfk, this::isOptedOut);
    }

    // --- teleport block ----------------------------------------------------------------------

    /**
     * The IT that pins {@code player} in place for a teleport starting at {@code from}, or null when
     * they're free to go. An AFK or non-playing IT can't chase anyone, so it pins nobody.
     */
    public Player itBlockingTeleport(Player player, Location from) {
        UUID id = player.getUniqueId();
        if (!config.teleportBlock.enabled || isIt(id) || isOptedOut(id) || !playable(player)) {
            return null;
        }
        Player it = onlineIt();
        if (it == null || !playable(it) || isAfk(it.getUniqueId()) || !it.getWorld().equals(from.getWorld())) {
            return null;
        }
        double radius = config.teleportBlock.radius;
        return it.getLocation().distanceSquared(from) <= radius * radius ? it : null;
    }

    private static boolean playable(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private Player onlineIt() {
        UUID itId = state.currentIt;
        return itId == null ? null : Bukkit.getPlayer(itId);
    }

    // --- AFK -----------------------------------------------------------------------------------

    /** Records player activity (movement, chat, commands, blocks, interaction, inventory). */
    public void markActive(UUID uuid) {
        if (!config.afk.enabled) {
            return;
        }
        afkService.markActive(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return config.afk.enabled && afkService.isAfk(uuid);
    }

    /**
     * No-op when AFK/auto-pass is off, IT is offline (owner decision: logging off keeps IT), or
     * nobody is IT. Idle-time math and receiver selection live in {@link AfkService}, which calls
     * back into {@link #autoPassIt}.
     */
    public void checkItAfk() {
        if (!config.afk.enabled || !config.afk.autoPass) {
            return;
        }
        Player it = onlineIt();
        if (it == null) {
            return;
        }
        afkService.checkItAfk(it, config.afk, config.messages, state.lastTagger, this::isOptedOut, this::autoPassIt);
    }

    /**
     * Passes IT from an idle player to {@code to} without the usual tag rewards: no +10 for the
     * receiver and no tagback cooldown, since nobody actually tagged anybody.
     */
    public void autoPassIt(Player from, Player to) {
        transferIt(from.getUniqueId(), to.getUniqueId(), null, 0L, () -> config.logs.fire(LogService.Event.IT_AFK_PASSED,
                Placeholder.unparsed("old_it", from.getName()),
                Placeholder.unparsed("new_it", to.getName())));
    }

    // --- points tick -----------------------------------------------------------------------

    public void pointsTick() {
        UUID it = state.currentIt;
        if (it == null) {
            return;
        }
        PointsTickService.Result result = pointsTickService.tick(it, config, this::isOptedOut, this::isAfk);

        PlayerStats stats = state.statsFor(it);
        stats.points += result.gain();
        double minutes = config.pointsIntervalSeconds / 60.0;
        if (result.online()) {
            stats.minutesItOnline += minutes;
        } else {
            stats.minutesItOffline += minutes;
        }
        save();
        boards.refreshAll(state);
        leaderboard.refresh(state);
    }

    // --- in-world leaderboard -----------------------------------------------------------------

    /** Placement math (block-centered, head-height) lives in {@link LeaderboardEntityService#locationFor}. */
    public void placeLeaderboard(Location adminLocation) {
        state.leaderboardLocation = leaderboard.locationFor(adminLocation);
        save();
        leaderboard.ensureSpawned();
    }

    /** Returns false (and leaves everything untouched) when there was no board to remove. */
    public boolean removeLeaderboard() {
        if (state.leaderboardLocation == null) {
            return false;
        }
        state.leaderboardLocation = null;
        save();
        leaderboard.despawn();
        return true;
    }

    public void ensureHeadWorn() {
        Player it = onlineIt();
        if (it != null) {
            heads.ensureWorn(it, config.head);
        }
    }

    public String nameOf(UUID uuid) {
        if (uuid == null) {
            return "nobody";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

    public static String formatPoints(double points) {
        return String.format(Locale.ROOT, "%.1f", points);
    }
}

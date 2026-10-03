package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.tag.afk.AfkTracker;
import dev.sirbepy.mc.tag.board.LeaderboardEntityService;
import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.logic.AfkEligibility;
import dev.sirbepy.mc.tag.logic.PendingAction;
import dev.sirbepy.mc.tag.logic.PendingActions;
import dev.sirbepy.mc.tag.logic.TagbackRule;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import dev.sirbepy.mc.tag.state.PlayerStats;
import dev.sirbepy.mc.tag.state.StateRepository;
import dev.sirbepy.mc.tag.state.TagState;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
    private final AfkTracker afkTracker;

    // Not persisted: just a spam guard for the tagback-blocked message.
    private final Map<UUID, Long> tagbackMessageCooldown = new HashMap<>();

    public TagService(JavaPlugin plugin, File stateFile, HeadService heads, PrefixService prefix,
                       ScoreboardService boards, LeaderboardEntityService leaderboard, TagConfig config) {
        this(plugin, stateFile, heads, prefix, boards, leaderboard, config,
                new AfkTracker(System::currentTimeMillis, config.afk.thresholdSeconds * 1000L));
    }

    // Package-private: lets tests inject a fake-clock AfkTracker so idle/threshold/warning
    // behavior is deterministic without sleeping through real `afk.*-seconds` durations.
    TagService(JavaPlugin plugin, File stateFile, HeadService heads, PrefixService prefix,
               ScoreboardService boards, LeaderboardEntityService leaderboard, TagConfig config, AfkTracker afkTracker) {
        this.plugin = plugin;
        this.stateFile = stateFile;
        this.heads = heads;
        this.prefix = prefix;
        this.boards = boards;
        this.leaderboard = leaderboard;
        this.config = config;
        this.afkTracker = afkTracker;
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

    public void setConfig(TagConfig config) {
        this.config = config;
        afkTracker.setThresholdMillis(config.afk.thresholdSeconds * 1000L);
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
        if (oldIt != null) {
            retireIt(oldIt);
        }

        state.currentIt = newIt;
        state.lastTagger = null;
        state.tagbackExpiryMillis = 0L;
        save();

        Player online = target.getPlayer();
        if (online != null) {
            crownIt(online);
        } else {
            state.pending.put(newIt, PendingAction.BECOME_IT);
            save();
        }

        config.logs.fire(LogService.Event.IT_SET,
                Placeholder.unparsed("it", nameOf(newIt)),
                Placeholder.unparsed("player", admin.getName()));
        boards.refreshAll(state);
        leaderboard.refresh(state);
    }

    public void adminClear(CommandSender admin) {
        UUID oldIt = state.currentIt;
        if (oldIt == null) {
            return;
        }
        retireIt(oldIt);
        state.currentIt = null;
        state.lastTagger = null;
        state.tagbackExpiryMillis = 0L;
        save();

        config.logs.fire(LogService.Event.IT_CLEARED, Placeholder.unparsed("player", admin.getName()));
        boards.refreshAll(state);
        leaderboard.refresh(state);
    }

    private void retireIt(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            heads.revoke(online);
            prefix.remove(uuid);
        } else {
            state.pending.put(uuid, PendingAction.CLEAR_IT);
        }
    }

    private void crownIt(Player player) {
        heads.grant(player, config.head);
        prefix.apply(player.getUniqueId(), config.prefixTemplate, config.prefixPriority);
    }

    // --- tag event ---------------------------------------------------------------------------

    public TagResult attemptTag(Player it, Player victim) {
        if (!isIt(it.getUniqueId())) {
            return TagResult.NOT_IT;
        }
        if (isOptedOut(victim.getUniqueId())) {
            return TagResult.OPTED_OUT;
        }
        if (victim.getUniqueId().equals(it.getUniqueId())) {
            return TagResult.SELF;
        }
        long now = System.currentTimeMillis();
        if (TagbackRule.isProtected(victim.getUniqueId(), state.lastTagger, state.tagbackExpiryMillis, now)) {
            maybeWarnTagback(it, victim, now);
            return TagResult.TAGBACK_BLOCKED;
        }

        UUID oldIt = it.getUniqueId();
        UUID newIt = victim.getUniqueId();

        heads.revoke(it);
        prefix.remove(oldIt);

        state.currentIt = newIt;
        state.lastTagger = oldIt;
        state.tagbackExpiryMillis = now + config.tagbackSeconds * 1000L;

        PlayerStats victimStats = state.statsFor(newIt);
        victimStats.timesTagged++;
        victimStats.points += config.pointsOnTag;
        state.statsFor(oldIt).tagsMade++;

        crownIt(victim);
        save();

        config.logs.fire(LogService.Event.TAG,
                Placeholder.unparsed("tagger", it.getName()),
                Placeholder.unparsed("tagged", victim.getName()),
                Placeholder.unparsed("it", victim.getName()));
        boards.refreshAll(state);
        leaderboard.refresh(state);
        return TagResult.OK;
    }

    private void maybeWarnTagback(Player it, Player victim, long now) {
        Long last = tagbackMessageCooldown.get(it.getUniqueId());
        long cooldownMillis = config.tagbackMessageCooldownSeconds * 1000L;
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        tagbackMessageCooldown.put(it.getUniqueId(), now);
        config.logs.toPlayer(LogService.Event.TAGBACK_BLOCKED, it,
                Placeholder.unparsed("player", victim.getName()));
    }

    public enum TagResult { OK, NOT_IT, OPTED_OUT, SELF, TAGBACK_BLOCKED }

    // --- join/quit -----------------------------------------------------------------------

    public void onJoin(Player player) {
        // A relogging IT must never be instantly treated as idle, and offline time never counts
        // as idle: joining always counts as activity.
        markActive(player.getUniqueId());

        Optional<PendingAction> pending = PendingActions.take(state.pending, player.getUniqueId());
        pending.ifPresent(action -> {
            if (action == PendingAction.BECOME_IT) {
                crownIt(player);
            } else {
                heads.revoke(player);
                prefix.remove(player.getUniqueId());
            }
            save();
        });

        if (isIt(player.getUniqueId())) {
            prefix.apply(player.getUniqueId(), config.prefixTemplate, config.prefixPriority);
            heads.ensureWorn(player, config.head);
            config.logs.fire(LogService.Event.IT_JOINED, Placeholder.unparsed("it", player.getName()));
        }
    }

    public void onQuit(Player player) {
        if (isIt(player.getUniqueId())) {
            config.logs.fire(LogService.Event.IT_QUIT, Placeholder.unparsed("it", player.getName()));
        }
        boards.disable(player);
        // Spam guard, not state: nothing left to debounce once the player is gone, and keeping it
        // around would leak one UUID per player forever.
        tagbackMessageCooldown.remove(player.getUniqueId());
        afkTracker.remove(player.getUniqueId());
    }

    // --- AFK -----------------------------------------------------------------------------------

    /** Records player activity (movement, chat, commands, blocks, interaction, inventory). */
    public void markActive(UUID uuid) {
        if (!config.afk.enabled) {
            return;
        }
        afkTracker.markActive(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return config.afk.enabled && afkTracker.isAfk(uuid);
    }

    /**
     * Warns and/or auto-passes IT for being idle too long. Called once per {@code
     * afk.check-interval-seconds} tick; a no-op whenever AFK handling or auto-pass is off, IT is
     * offline (owner decision: IT who logs off stays IT), or nobody is currently IT.
     */
    public void checkItAfk() {
        if (!config.afk.enabled || !config.afk.autoPass) {
            return;
        }
        UUID itId = state.currentIt;
        if (itId == null) {
            return;
        }
        Player it = Bukkit.getPlayer(itId);
        if (it == null) {
            return;
        }

        long idleMillis = afkTracker.idleMillis(itId);
        long passAfterMillis = config.afk.itPassAfterSeconds * 1000L;
        long warnSeconds = config.afk.itPassWarningSeconds;

        if (warnSeconds > 0 && idleMillis < passAfterMillis) {
            long warnAtMillis = passAfterMillis - warnSeconds * 1000L;
            if (idleMillis >= warnAtMillis && afkTracker.warnOnceIfNotAlready(itId)) {
                config.messages.send(it, "afk-warning", Placeholder.unparsed("seconds", String.valueOf(warnSeconds)));
            }
        }

        if (idleMillis >= passAfterMillis) {
            tryAutoPassIt(it);
        }
    }

    private void tryAutoPassIt(Player it) {
        UUID previous = state.lastTagger;
        List<AfkEligibility.Candidate> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(it.getUniqueId()))
                .map(p -> new AfkEligibility.Candidate(p.getUniqueId(), afkTracker.isAfk(p.getUniqueId()),
                        isOptedOut(p.getUniqueId()),
                        p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE))
                .toList();
        List<UUID> eligible = AfkEligibility.eligible(candidates, previous);
        if (eligible.isEmpty()) {
            return;
        }
        UUID chosenId = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        Player chosen = Bukkit.getPlayer(chosenId);
        if (chosen == null) {
            return;
        }
        autoPassIt(it, chosen);
    }

    /**
     * Passes IT from an idle player to {@code to} without the usual tag rewards: no +10 for the
     * receiver and no tagback cooldown, since nobody actually tagged anybody.
     */
    public void autoPassIt(Player from, Player to) {
        retireIt(from.getUniqueId());

        state.currentIt = to.getUniqueId();
        state.lastTagger = null;
        state.tagbackExpiryMillis = 0L;
        save();

        crownIt(to);

        config.logs.fire(LogService.Event.IT_AFK_PASSED,
                Placeholder.unparsed("old_it", from.getName()),
                Placeholder.unparsed("new_it", to.getName()));
        boards.refreshAll(state);
        leaderboard.refresh(state);
    }

    // --- points tick -----------------------------------------------------------------------

    public void pointsTick() {
        UUID it = state.currentIt;
        if (it == null) {
            return;
        }
        int n = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(it))
                .filter(p -> !isOptedOut(p.getUniqueId()))
                .filter(p -> !(config.afk.excludeFromMultiplier && isAfk(p.getUniqueId())))
                .count();
        double multiplier = dev.sirbepy.mc.tag.logic.PointsMath.multiplier(n, config.pointsCurve, config.pointsStep);
        boolean online = Bukkit.getPlayer(it) != null;
        double gain = dev.sirbepy.mc.tag.logic.PointsMath.tickGain(online, config.pointsBase, config.pointsOfflineFactor, multiplier);

        PlayerStats stats = state.statsFor(it);
        stats.points += gain;
        double minutes = config.pointsIntervalSeconds / 60.0;
        if (online) {
            stats.minutesItOnline += minutes;
        } else {
            stats.minutesItOffline += minutes;
        }
        save();
        boards.refreshAll(state);
        leaderboard.refresh(state);
    }

    // --- in-world leaderboard -----------------------------------------------------------------

    /**
     * Block-centered x/z so the board reads as "over this block" instead of drifting toward
     * whichever corner the admin happened to be standing on; y + 1.5 puts the text around head
     * height rather than at floor level.
     */
    public void placeLeaderboard(Location adminLocation) {
        state.leaderboardLocation = new TagState.LeaderboardLocation(
                adminLocation.getWorld().getName(),
                Math.floor(adminLocation.getX()) + 0.5,
                Math.floor(adminLocation.getY()) + 1.5,
                Math.floor(adminLocation.getZ()) + 0.5);
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
        UUID it = state.currentIt;
        if (it == null) {
            return;
        }
        Player online = Bukkit.getPlayer(it);
        if (online != null) {
            heads.ensureWorn(online, config.head);
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

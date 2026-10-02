package dev.sirbepy.mc.tag;

import dev.sirbepy.mc.tag.board.ScoreboardService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.head.HeadService;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.logic.PendingAction;
import dev.sirbepy.mc.tag.logic.PendingActions;
import dev.sirbepy.mc.tag.logic.TagbackRule;
import dev.sirbepy.mc.tag.prefix.PrefixService;
import dev.sirbepy.mc.tag.state.PlayerStats;
import dev.sirbepy.mc.tag.state.StateRepository;
import dev.sirbepy.mc.tag.state.TagState;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

    private TagState state;
    private TagConfig config;

    // Not persisted: just a spam guard for the tagback-blocked message.
    private final Map<UUID, Long> tagbackMessageCooldown = new HashMap<>();

    public TagService(JavaPlugin plugin, File stateFile, HeadService heads, PrefixService prefix,
                       ScoreboardService boards, TagConfig config) {
        this.plugin = plugin;
        this.stateFile = stateFile;
        this.heads = heads;
        this.prefix = prefix;
        this.boards = boards;
        this.config = config;
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
            maybeWarnTagback(it, now);
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
        return TagResult.OK;
    }

    private void maybeWarnTagback(Player it, long now) {
        Long last = tagbackMessageCooldown.get(it.getUniqueId());
        long cooldownMillis = config.tagbackMessageCooldownSeconds * 1000L;
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        tagbackMessageCooldown.put(it.getUniqueId(), now);
        config.logs.toPlayer(LogService.Event.TAGBACK_BLOCKED, it,
                Placeholder.unparsed("player", it.getName()));
    }

    public enum TagResult { OK, NOT_IT, OPTED_OUT, SELF, TAGBACK_BLOCKED }

    // --- join/quit -----------------------------------------------------------------------

    public void onJoin(Player player) {
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

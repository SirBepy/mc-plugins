package dev.sirbepy.mc.tag.afk;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.config.AfkConfig;
import dev.sirbepy.mc.tag.logic.AfkEligibility;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Owns the {@link AfkTracker} clock and the per-tick IT idle check (warn, then auto-pass), the way
 * {@code TrackerService} owns the tracker compass. The actual TagState mutation for an auto-pass
 * stays in TagService (the single place allowed to mutate it); this calls back into it via {@code
 * passIt} once it has picked a receiver.
 */
public final class AfkService {

    private final AfkTracker tracker;

    public AfkService(AfkTracker tracker) {
        this.tracker = tracker;
    }

    /** Re-read on every `/tag reload` so a changed `afk.threshold-seconds` takes effect immediately. */
    public void setThresholdMillis(long thresholdMillis) {
        tracker.setThresholdMillis(thresholdMillis);
    }

    /** Records player activity (movement, chat, commands, blocks, interaction, inventory). */
    public void markActive(UUID uuid) {
        tracker.markActive(uuid);
    }

    /** Call on quit: no leak, and avoids a stale entry answering for a different play session. */
    public void remove(UUID uuid) {
        tracker.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return tracker.isAfk(uuid);
    }

    /**
     * Warns and/or auto-passes IT for being idle too long. Caller (TagService#checkItAfk) has
     * already confirmed AFK handling and auto-pass are on, and that {@code it} is online; this
     * owns the idle-time math, the once-per-idle-stretch warning, and picking an eligible
     * receiver. {@code isOptedOut} and {@code passIt} are callbacks into TagService/TagState,
     * which this class never touches directly.
     */
    public void checkItAfk(Player it, AfkConfig config, Messages messages, UUID previousTagger,
                            Predicate<UUID> isOptedOut, BiConsumer<Player, Player> passIt) {
        long idleMillis = tracker.idleMillis(it.getUniqueId());
        long passAfterMillis = config.itPassAfterSeconds * 1000L;
        long warnSeconds = config.itPassWarningSeconds;

        if (warnSeconds > 0 && idleMillis < passAfterMillis) {
            long warnAtMillis = passAfterMillis - warnSeconds * 1000L;
            if (idleMillis >= warnAtMillis && tracker.warnOnceIfNotAlready(it.getUniqueId())) {
                messages.send(it, "afk-warning", Placeholder.unparsed("seconds", String.valueOf(warnSeconds)));
            }
        }

        if (idleMillis >= passAfterMillis) {
            tryAutoPassIt(it, previousTagger, isOptedOut, passIt);
        }
    }

    private void tryAutoPassIt(Player it, UUID previousTagger, Predicate<UUID> isOptedOut,
                                BiConsumer<Player, Player> passIt) {
        List<AfkEligibility.Candidate> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(it.getUniqueId()))
                .map(p -> new AfkEligibility.Candidate(p.getUniqueId(), tracker.isAfk(p.getUniqueId()),
                        isOptedOut.test(p.getUniqueId()), playable(p)))
                .toList();
        List<UUID> eligible = AfkEligibility.eligible(candidates, previousTagger);
        if (eligible.isEmpty()) {
            return;
        }
        UUID chosenId = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        Player chosen = Bukkit.getPlayer(chosenId);
        if (chosen == null) {
            return;
        }
        passIt.accept(it, chosen);
    }

    // Mirrors TagService#playable: survival/adventure is "can actually play tag", needed both
    // here and for tracker candidates. Duplicated rather than shared across packages for one line.
    private static boolean playable(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
}

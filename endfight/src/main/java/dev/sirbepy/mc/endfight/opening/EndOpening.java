package dev.sirbepy.mc.endfight.opening;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * When the End opens (Joe, 2026-10-10): live restarts with the End world loaded well before the
 * dragon fight, and portals into it stay shut for everyone until {@code opens-at}, with a chat
 * countdown on the way. No opening time means no gate at all, which is the state every boot after
 * tonight starts from once the time has passed.
 *
 * <p>The time lives in {@code plugins/EndFight/opening.yml}, not config.yml: it's a one-off date
 * set at deploy time (or by {@code /endfight opening set}), and live's config.yml never picks up
 * new keys from the jar anyway.
 */
public final class EndOpening {

    /** One countdown line, or the opening itself when {@code minutesLeft} is 0. */
    public record Step(Instant at, int minutesLeft, boolean warpHint) {
        public boolean opening() {
            return minutesLeft == 0;
        }
    }

    /** A step only fires this long after its time, so a restart doesn't replay old countdown
     * lines (or the opening title at 22:00). */
    static final Duration GRACE = Duration.ofMinutes(2);

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm z", Locale.ENGLISH);

    private final File file;
    private final List<Integer> countdownMinutes;
    private final Set<Integer> warpHintMinutes;
    private final ZoneId zone;
    private final Logger logger;
    private final Set<Step> fired = new HashSet<>();
    private Instant opensAt;
    private List<Step> steps = List.of();

    public EndOpening(File file, List<Integer> countdownMinutes, Set<Integer> warpHintMinutes, ZoneId zone,
                      Logger logger) {
        this.file = file;
        this.countdownMinutes = List.copyOf(countdownMinutes);
        this.warpHintMinutes = Set.copyOf(warpHintMinutes);
        this.zone = zone;
        this.logger = logger;
        load();
    }

    private void load() {
        if (file == null || !file.exists()) {
            setOpensAtInMemory(null);
            return;
        }
        String raw = YamlConfiguration.loadConfiguration(file).getString("opens-at", "");
        if (raw == null || raw.isBlank()) {
            setOpensAtInMemory(null);
            return;
        }
        try {
            setOpensAtInMemory(OffsetDateTime.parse(raw.trim()).toInstant());
        } catch (DateTimeParseException e) {
            logger.warning("opening.yml opens-at isn't an ISO date-time with an offset (" + raw + "); End portals stay ungated.");
            setOpensAtInMemory(null);
        }
    }

    /** Sets (or with {@code null}, clears) the opening time and writes it to opening.yml. */
    public void setOpensAt(Instant at) {
        setOpensAtInMemory(at);
        if (file == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("opens-at", at == null ? "" : at.atZone(zone).toOffsetDateTime().toString());
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.warning("Could not save opening.yml: " + e.getMessage());
        }
    }

    private void setOpensAtInMemory(Instant at) {
        opensAt = at;
        fired.clear();
        if (at == null) {
            steps = List.of();
            return;
        }
        List<Step> built = new ArrayList<>();
        for (int minutes : countdownMinutes) {
            if (minutes > 0) {
                built.add(new Step(at.minus(Duration.ofMinutes(minutes)), minutes, warpHintMinutes.contains(minutes)));
            }
        }
        built.add(new Step(at, 0, false));
        built.sort(Comparator.comparing(Step::at));
        steps = List.copyOf(built);
    }

    public Instant opensAt() {
        return opensAt;
    }

    public boolean closed(Instant now) {
        return opensAt != null && now.isBefore(opensAt);
    }

    /** The newest step whose time has come (within {@link #GRACE}) and hasn't fired yet; each comes
     * back once, and older ones caught up at the same moment are dropped, never sent in a burst. */
    public List<Step> due(Instant now) {
        Step newest = null;
        for (Step step : steps) {
            if (!fired.contains(step) && !now.isBefore(step.at()) && now.isBefore(step.at().plus(GRACE))) {
                fired.add(step);
                newest = step;
            }
        }
        return newest == null ? List.of() : List.of(newest);
    }

    /** True once the earliest warp-hint countdown step has passed: from then on, joiners hear about
     * {@code /warp end} too. */
    public boolean warpHintStarted(Instant now) {
        for (Step step : steps) {
            if (step.warpHint() && !now.isBefore(step.at())) {
                return true;
            }
        }
        return false;
    }

    /** {@code opens-at} on the server's wall clock, e.g. "20:30 CEST". */
    public String clockLabel() {
        return opensAt == null ? "" : CLOCK.format(opensAt.atZone(zone));
    }

    /** Time until opening, rounded up to the minute: "1h 30m", "5 minutes", "1 minute". */
    public String leftLabel(Instant now) {
        if (opensAt == null) {
            return "";
        }
        long seconds = Math.max(0, Duration.between(now, opensAt).getSeconds());
        return minutesLabel((seconds + 59) / 60);
    }

    static String minutesLabel(long minutes) {
        if (minutes >= 60) {
            long hours = minutes / 60;
            long rest = minutes % 60;
            return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
        }
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    /** Parses {@code /endfight opening set}'s argument: a full ISO date-time with offset, or just
     * "HH:mm" meaning that time today on the server's zone. */
    public Instant parse(String raw, Instant now) {
        String text = raw.trim();
        if (text.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = text.split(":");
            return now.atZone(zone).toLocalDate()
                    .atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]))
                    .atZone(zone).toInstant();
        }
        return OffsetDateTime.parse(text).toInstant();
    }
}

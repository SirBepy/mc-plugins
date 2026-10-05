package dev.sirbepy.mc.tag.logic;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure target selection for IT's tracker compass. Bukkit-free like {@link AfkEligibility}: the
 * caller resolves each online player into a {@link Candidate} and this decides who is trackable,
 * in what cycle order, and who the compass starts on.
 */
public final class TrackerTargets {

    private TrackerTargets() {
    }

    /**
     * One online player other than IT. {@code distance} is only meaningful when {@code sameWorld};
     * a compass can't point across dimensions, so other-realm players carry no distance.
     */
    public record Candidate(UUID uuid, String name, boolean sameWorld, double distance, boolean afk,
                            boolean optedOut, boolean survivalOrAdventure) {
    }

    /** Opted-out and creative/spectator players never show up, AFK ones do (they're labelled instead). */
    public static List<Candidate> trackable(List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> !c.optedOut() && c.survivalOrAdventure())
                .sorted(CYCLE_ORDER)
                .toList();
    }

    // Same-world players nearest first, then everyone in another realm by name, so a right-click
    // cycle walks outward from IT before jumping to the players it can't point at.
    private static final Comparator<Candidate> CYCLE_ORDER = Comparator
            .comparing((Candidate c) -> !c.sameWorld())
            .thenComparingDouble(c -> c.sameWorld() ? c.distance() : 0.0)
            .thenComparing(c -> c.name().toLowerCase(Locale.ROOT));

    /** Nearest same-world player who can actually be tagged, else the first trackable one, else null. */
    public static UUID defaultTarget(List<Candidate> trackable) {
        return trackable.stream()
                .filter(c -> c.sameWorld() && !c.afk())
                .findFirst()
                .or(() -> trackable.stream().findFirst())
                .map(Candidate::uuid)
                .orElse(null);
    }

    /** Keeps {@code current} while it's still trackable; falls back to {@link #defaultTarget} once it isn't. */
    public static UUID resolve(List<Candidate> trackable, UUID current) {
        if (current != null && trackable.stream().anyMatch(c -> c.uuid().equals(current))) {
            return current;
        }
        return defaultTarget(trackable);
    }

    /** The player after {@code current} in cycle order, wrapping around; null when nobody is trackable. */
    public static UUID next(List<Candidate> trackable, UUID current) {
        if (trackable.isEmpty()) {
            return null;
        }
        for (int i = 0; i < trackable.size(); i++) {
            if (trackable.get(i).uuid().equals(current)) {
                return trackable.get((i + 1) % trackable.size()).uuid();
            }
        }
        return defaultTarget(trackable);
    }
}

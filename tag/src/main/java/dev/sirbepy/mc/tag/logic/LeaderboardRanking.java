package dev.sirbepy.mc.tag.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure ranking for the tag leaderboard (lowest points wins). Kept Bukkit-free so the tie-break
 * rule is unit testable without a server, and shared by both {@code /tag top} and the in-world
 * board/sidebar so they can never disagree about who is tied with whom.
 */
public final class LeaderboardRanking {

    private LeaderboardRanking() {
    }

    /** One player's resolved facts going into the ranking: last-known name, current points. */
    public record Entry(UUID uuid, String name, double points) {
    }

    /** A ranked row. Competition ranking (1, 1, 3), not dense ranking, so a tie reads as a tie. */
    public record Row(int rank, UUID uuid, String name, double points) {
    }

    /**
     * Sorted ascending by points; equal points share the same rank. The name compare is only a
     * stable tiebreak for ordering within a tied group (case-insensitive, since player names
     * aren't case-normalized anywhere else either), never used to break the shared rank number.
     * Capped at {@code limit} rows.
     */
    public static List<Row> rank(List<Entry> entries, int limit) {
        List<Entry> sorted = entries.stream()
                .sorted(Comparator.comparingDouble(Entry::points)
                        .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)))
                .toList();

        List<Row> rows = new ArrayList<>();
        int rank = 0;
        int position = 0;
        Double previousPoints = null;
        for (Entry entry : sorted) {
            if (rows.size() >= limit) {
                break;
            }
            position++;
            if (previousPoints == null || entry.points() != previousPoints) {
                rank = position;
            }
            rows.add(new Row(rank, entry.uuid(), entry.name(), entry.points()));
            previousPoints = entry.points();
        }
        return rows;
    }
}

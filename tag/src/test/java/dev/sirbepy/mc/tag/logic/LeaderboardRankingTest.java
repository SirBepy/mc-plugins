package dev.sirbepy.mc.tag.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardRankingTest {

    @Test
    void tiedPointsShareARankAndTheNextDistinctValueJumpsToItsOrdinalPosition() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<LeaderboardRanking.Entry> entries = List.of(
                new LeaderboardRanking.Entry(a, "Alex", 1.0),
                new LeaderboardRanking.Entry(b, "Bo", 1.0),
                new LeaderboardRanking.Entry(c, "Cy", 3.0));

        List<LeaderboardRanking.Row> rows = LeaderboardRanking.rank(entries, 10);

        assertEquals(3, rows.size());
        assertEquals(1, rows.get(0).rank());
        assertEquals(1, rows.get(1).rank());
        assertEquals(3, rows.get(2).rank(), "competition ranking: the tie at rank 1 takes two slots, so the next row is rank 3, not 2");
    }

    @Test
    void equalPointsBreakOrderByCaseInsensitiveName() {
        UUID lower = UUID.randomUUID();
        UUID upper = UUID.randomUUID();
        List<LeaderboardRanking.Entry> entries = List.of(
                new LeaderboardRanking.Entry(upper, "Zed", 5.0),
                new LeaderboardRanking.Entry(lower, "alex", 5.0));

        List<LeaderboardRanking.Row> rows = LeaderboardRanking.rank(entries, 10);

        assertEquals("alex", rows.get(0).name(), "case-insensitive name is the stable tiebreak for ordering a tied group");
        assertEquals("Zed", rows.get(1).name());
        assertEquals(1, rows.get(0).rank());
        assertEquals(1, rows.get(1).rank(), "the name tiebreak orders the tie, it never breaks the shared rank itself");
    }

    @Test
    void capsAtTheGivenLimit() {
        List<LeaderboardRanking.Entry> entries = List.of(
                new LeaderboardRanking.Entry(UUID.randomUUID(), "A", 1.0),
                new LeaderboardRanking.Entry(UUID.randomUUID(), "B", 2.0),
                new LeaderboardRanking.Entry(UUID.randomUUID(), "C", 3.0));

        List<LeaderboardRanking.Row> rows = LeaderboardRanking.rank(entries, 2);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().map(LeaderboardRanking.Row::name).toList().containsAll(List.of("A", "B")));
    }

    @Test
    void emptyInputGivesAnEmptyRanking() {
        assertEquals(List.of(), LeaderboardRanking.rank(List.of(), 10));
    }
}

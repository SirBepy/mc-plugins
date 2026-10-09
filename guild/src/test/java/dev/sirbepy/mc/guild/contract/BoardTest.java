package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest extends GuildTestBase {

    @Test
    void anEAdventurerGetsFiveDistinctEDailiesAndAnEWeekly() {
        Member member = join(alice);
        assertEquals(5, member.dailies().size());
        Set<String> ids = new HashSet<>();
        for (ActiveContract daily : member.dailies()) {
            ids.add(daily.defId());
            assertEquals(Rank.E, daily.def().rank());
            assertEquals(ContractDef.Type.DAILY, daily.def().type());
        }
        assertEquals(5, ids.size());
        assertNotNull(member.weekly());
        assertEquals(ContractDef.Type.WEEKLY, member.weekly().def().type());
        assertEquals(Rank.E, member.weekly().def().rank());
    }

    @Test
    void theFirstSlotDrawsOneRankDown() {
        Member member = join(alice);
        member.setRank(Rank.C);
        Board board = new Board(new Random(3) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        });
        board.ensure(member, "2026-10-09", "2026-10-05", Board.MAX_DAILY_SLOTS);
        assertEquals(Rank.D, member.dailies().get(0).def().rank());
        for (int slot = 1; slot < 5; slot++) {
            assertEquals(Rank.C, member.dailies().get(slot).def().rank(), "slot " + slot);
        }
    }

    @Test
    void everyRankFillsFiveDistinctSlotsTwoDaysRunning() {
        Member member = join(alice);
        Board board = new Board(new Random(11));
        for (Rank rank : Rank.values()) {
            member.setRank(rank);
            for (String day : new String[] {"2026-10-09", "2026-10-10", "2026-10-11"}) {
                board.ensure(member, rank + day, "2026-10-05", Board.MAX_DAILY_SLOTS);
                Set<String> ids = new HashSet<>();
                for (ActiveContract daily : member.dailies()) {
                    assertNotNull(daily, rank + " drew an empty slot");
                    ids.add(daily.defId());
                }
                assertEquals(5, ids.size(), rank + " repeated a contract on one board");
            }
        }
    }

    @Test
    void aThreeSlotBoardGainsTwoSlotsTheSameDayKeepingItsWork() {
        Member member = join(alice);
        while (member.dailies().size() > 3) {
            member.dailies().remove(member.dailies().size() - 1);
        }
        member.dailies().get(0).markClaimed();
        member.setRerollsUsed(1);
        java.util.List<String> before = member.dailies().stream().map(ActiveContract::defId).toList();

        guild.member(alice);

        assertEquals(5, member.dailies().size());
        assertEquals(before, member.dailies().subList(0, 3).stream().map(ActiveContract::defId).toList());
        assertTrue(member.dailies().get(0).claimed(), "today's claim survives the top-up");
        assertEquals(1, member.rerollsUsed(), "the top-up is not a new day");
        assertEquals(5, member.dailies().stream().map(ActiveContract::defId).distinct().count());
        member.dailies().subList(3, 5).forEach(d -> assertEquals(Rank.E, d.def().rank()));
    }

    @Test
    void aNewDayRedrawsAndAvoidsYesterdaysContracts() {
        Member member = join(alice);
        Set<String> yesterday = new HashSet<>();
        member.dailies().forEach(d -> yesterday.add(d.defId()));
        clock.plusDays(1);
        guild.member(alice);
        for (ActiveContract daily : member.dailies()) {
            assertFalse(yesterday.contains(daily.defId()), daily.defId() + " came back the next day");
        }
        assertEquals(clock.dayKey(), member.boardDay());
    }

    @Test
    void theSameDayKeepsTheSameBoard() {
        Member member = join(alice);
        String first = member.dailies().get(1).defId();
        clock.plusHours(3);
        guild.member(alice);
        assertEquals(first, member.dailies().get(1).defId());
    }

    @Test
    void aWeekLaterTheWeeklyChanges() {
        Member member = join(alice);
        String weekly = member.weekly().defId();
        clock.plusDays(7);
        guild.member(alice);
        assertFalse(weekly.equals(member.weekly().defId()));
    }

    @Test
    void rerollsAreLimitedByRank() {
        Member member = join(alice);
        assertEquals(Board.RerollResult.REROLLED, guild.board().reroll(member, 0));
        assertEquals(Board.RerollResult.NO_REROLLS_LEFT, guild.board().reroll(member, 1));
        member.setRank(Rank.A);
        assertEquals(Board.RerollResult.REROLLED, guild.board().reroll(member, 1));
        assertEquals(Board.RerollResult.REROLLED, guild.board().reroll(member, 2));
        assertEquals(Board.RerollResult.NO_REROLLS_LEFT, guild.board().reroll(member, 2));
    }

    @Test
    void aFinishedContractCannotBeRerolled() {
        Member member = join(alice);
        ActiveContract daily = member.dailies().get(0);
        for (int i = 0; i < daily.def().objectives().size(); i++) {
            daily.setProgress(i, daily.def().objectives().get(i).amount());
        }
        if (daily.def().hasGather()) {
            daily.markClaimed();
        }
        assertEquals(Board.RerollResult.NOT_REROLLABLE, guild.board().reroll(member, 0));
    }

    @Test
    void fromDRankADailyCanRollRare() {
        Member member = join(alice);
        member.setRank(Rank.D);
        Board alwaysRare = new Board(new Random(5) {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        alwaysRare.ensure(member, "2026-10-09", "2026-10-05", Board.MAX_DAILY_SLOTS);
        for (ActiveContract daily : member.dailies()) {
            assertEquals(ContractDef.Type.RARE, daily.def().type());
            assertEquals(Rank.D, daily.rewardRank());
            assertTrue(daily.reward().bundle());
        }
    }

    @Test
    void anEAdventurerNeverRollsRare() {
        Member member = join(alice);
        Board alwaysRare = new Board(new Random(5) {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        alwaysRare.ensure(member, "2026-10-09", "2026-10-05", Board.MAX_DAILY_SLOTS);
        member.dailies().forEach(d -> assertEquals(ContractDef.Type.DAILY, d.def().type()));
    }
}

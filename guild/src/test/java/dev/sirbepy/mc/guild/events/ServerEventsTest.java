package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEventsTest extends GuildTestBase {

    private ServerEvents events() {
        return guild.events();
    }

    private void finishBounty(PlayerMock player) {
        EventPools.Bounty bounty = events().bounty();
        Objective o = bounty.objective();
        String fact = o.targets().iterator().next();
        guild.progress(player, o.kind(), Set.of(fact), o.amount());
    }

    @Test
    void aBountyIsPostedAndWonByTheFirstToClaimIt() {
        Member member = join(alice);
        member.setRank(Rank.A);
        events().tick();
        assertNotNull(events().bounty());
        finishBounty(alice);
        double before = bank.balance(alice);
        long treasury = store.treasury().balance();

        Guild.Claim claim = events().claimBounty(alice);

        assertEquals(Guild.ClaimResult.CLAIMED, claim.result());
        assertEquals(before + events().bounty().coins(), bank.balance(alice));
        assertTrue(store.treasury().balance() > treasury, "the commission is minted on bounties too");
        assertEquals(1, member.stat("bounties"));
        assertEquals(Guild.ClaimResult.CLOSED, events().claimBounty(alice).result());
    }

    @Test
    void theGuildmasterCanWinABountyLikeAnyMember() {
        Member member = join(gm);
        member.setRank(Rank.S);
        events().tick();
        finishBounty(gm);
        double before = bank.balance(gm);

        Guild.Claim claim = events().claimBounty(gm);

        assertEquals(Guild.ClaimResult.CLAIMED, claim.result());
        assertEquals(before + events().bounty().coins(), bank.balance(gm));
    }

    @Test
    void lowRanksCannotTakeBounties() {
        join(alice);
        events().tick();
        assertEquals(Guild.ClaimResult.NOT_ELIGIBLE, events().claimBounty(alice).result());
    }

    @Test
    void aClaimedBountyIsReplacedAfterTwelveHours() {
        Member member = join(alice);
        member.setRank(Rank.A);
        events().tick();
        String first = events().bounty().id();
        finishBounty(alice);
        events().claimBounty(alice);
        clock.plusHours(11);
        events().tick();
        assertEquals(first, events().bounty().id());
        clock.plusHours(2);
        events().tick();
        assertNotEquals(first, events().bounty().id());
    }

    @Test
    void anUnclaimedBountyExpiresAfterThreeDays() {
        join(alice);
        events().tick();
        String first = events().bounty().id();
        clock.plusDays(3);
        clock.plusHours(1);
        events().tick();
        assertNotEquals(first, events().bounty().id());
    }

    @Test
    void rotationNeverPicksABountyRankNoMemberHolds() {
        Member member = join(alice);
        member.setRank(Rank.C);
        // Rotate many times (seed 42 is fixed, but exercise enough draws that a B/A pick would show up
        // pre-fix) and confirm every bounty posted stays claimable by the C rank Alice is capped at.
        for (int i = 0; i < 30; i++) {
            events().tick();
            EventPools.Bounty bounty = events().bounty();
            assertNotNull(bounty);
            assertTrue(Rank.C.atLeast(bounty.minRank()),
                    "rotated in " + bounty.id() + " (" + bounty.minRank() + "), above the only rank any member holds");
            clock.plusDays(3);
            clock.plusHours(1);
        }
    }

    @Test
    void aGrandQuestCompletesTogetherAndSharesItsReward() {
        Member a = join(alice);
        Member g = join(gm);
        var bob = server.addPlayer("Bob");
        var carol = server.addPlayer("Carol");
        var dave = server.addPlayer("Dave");
        join(bob);
        join(carol);
        join(dave);
        events().tick();
        EventPools.GrandQuest quest = events().grandQuest();
        Set<String> facts = quest.targets().isEmpty() ? Set.of("ANY") : Set.of(quest.targets().iterator().next());
        int cap = events().shareCap();
        guild.progress(alice, quest.kind(), facts, cap);
        guild.progress(bob, quest.kind(), facts, cap);
        guild.progress(carol, quest.kind(), facts, cap);
        guild.progress(dave, quest.kind(), facts, quest.goal() - 3 * cap - 5);
        assertEquals(Guild.ClaimResult.NOT_DONE, events().claimGrand(alice).result());
        guild.progress(gm, quest.kind(), facts, 5);
        assertTrue(events().grandDone());
        assertTrue(a.titles().contains("champion"), "on a tie at the cap, the earliest contributor is the champion");

        assertEquals(Guild.ClaimResult.CLAIMED, events().claimGrand(alice).result());
        assertEquals(Guild.ClaimResult.ALREADY_CLAIMED, events().claimGrand(alice).result());
        assertEquals(Guild.ClaimResult.NOT_ELIGIBLE, events().claimGrand(gm).result(), "5 is under the share threshold");
        assertEquals(1, a.stat("grand-quests"));
        assertEquals(0, g.stat("grand-quests"));
    }

    @Test
    void oneAdventurerCountsForAtMostAQuarterOfTheGoal() {
        Member a = join(alice);
        store.events().set("grand.id", "gq_hunt");
        store.events().set("grand.week", clock.weekKey());
        assertEquals(250, events().shareCap());
        guild.progress(alice, Objective.Kind.HUNT, Set.of("#HOSTILE"), 240);
        guild.progress(alice, Objective.Kind.HUNT, Set.of("#HOSTILE"), 1000);
        assertEquals(250, events().contribution(a));
        assertEquals(250, events().grandTotal(), "the rest of the deed adds nothing to the server's total");
        assertFalse(events().grandDone());
    }

    @Test
    void aRerollVoidsTheQuestButKeepsWhatWasPaid() {
        Member a = join(alice);
        events().tick();
        EventPools.GrandQuest first = events().grandQuest();
        store.events().set("grand.done", true);
        store.events().set("grand.contrib." + a.id(), first.goal());
        store.events().set("grand.total", first.goal());
        assertEquals(Guild.ClaimResult.CLAIMED, events().claimGrand(alice).result());
        double paid = bank.balance(alice);

        EventPools.GrandQuest next = events().rerollGrandQuest();

        assertNotEquals(first.id(), next.id());
        assertEquals(next.id(), events().grandQuest().id());
        assertEquals(0, events().grandTotal());
        assertEquals(0, events().contribution(a));
        assertFalse(events().grandDone());
        assertEquals(Guild.ClaimResult.NOT_DONE, events().claimGrand(alice).result());
        assertEquals(paid, bank.balance(alice));
        assertEquals(1, a.stat("grand-quests"));
        events().tick();
        assertEquals(next.id(), events().grandQuest().id(), "the weekly rotation keeps the rerolled quest");
    }

    @Test
    void aNewWeekBringsANewGrandQuest() {
        join(alice);
        events().tick();
        String first = events().grandQuest().id();
        clock.plusDays(7);
        events().tick();
        assertNotEquals(first, events().grandQuest().id());
        assertEquals(0, events().grandTotal());
    }

    @Test
    void theGuildmastersActivityPaysItsWinnerFromTheTreasury() {
        join(alice);
        join(gm);
        assertEquals(ServerEvents.StartResult.NOT_GUILDMASTER, events().startActivity(alice, ServerEvents.ActivityKind.FISH, 100, 30));
        assertEquals(ServerEvents.StartResult.BAD_PRIZE, events().startActivity(gm, ServerEvents.ActivityKind.FISH, 5000, 30));
        long treasury = store.treasury().balance();
        assertEquals(ServerEvents.StartResult.STARTED, events().startActivity(gm, ServerEvents.ActivityKind.FISH, 100, 30));
        assertEquals(treasury - 100, store.treasury().balance());

        guild.progress(alice, Objective.Kind.FISH, Set.of("COD"), 3);
        guild.progress(gm, Objective.Kind.FISH, Set.of("COD"), 9);
        double before = bank.balance(alice);
        clock.plusHours(1);
        events().tick();

        assertEquals(null, events().activity());
        assertEquals(before + 100, bank.balance(alice), "the Guildmaster can't win his own contest");
        assertTrue(store.member(alice.getUniqueId()).titles().contains("derby"));
        assertEquals(ServerEvents.StartResult.ALREADY_TODAY, events().startActivity(gm, ServerEvents.ActivityKind.HUNT, 100, 30));
    }

    @Test
    void anActivityNobodyEntersIsRefunded() {
        join(gm);
        long treasury = store.treasury().balance();
        events().startActivity(gm, ServerEvents.ActivityKind.MINE, 200, 10);
        clock.plusHours(1);
        events().tick();
        assertEquals(treasury, store.treasury().balance());
        assertEquals(LedgerEntry.Kind.REFUND, store.treasury().ledger().getFirst().kind());
    }

    @Test
    void aHandEditedActivityScoreKeyIsSkipped() {
        join(alice);
        join(gm);
        events().startActivity(gm, ServerEvents.ActivityKind.FISH, 100, 30);
        guild.progress(alice, Objective.Kind.FISH, Set.of("COD"), 3);
        store.events().set("activity.score.not-a-uuid", 50);
        double before = bank.balance(alice);
        clock.plusHours(1);
        events().tick();

        assertEquals(null, events().activity());
        assertEquals(before + 100, bank.balance(alice));
    }

    @Test
    void eventStateSurvivesASave() {
        Member member = join(alice);
        member.setRank(Rank.A);
        events().tick();
        String bounty = events().bounty().id();
        String quest = events().grandQuest().id();
        store.save();
        var reloaded = newStore();
        assertEquals(bounty, reloaded.events().getString("bounty.id"));
        assertEquals(quest, reloaded.events().getString("grand.id"));
    }
}

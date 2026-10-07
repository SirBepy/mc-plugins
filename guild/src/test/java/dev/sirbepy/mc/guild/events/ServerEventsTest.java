package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEventsTest extends GuildTestBase {

    private ServerEvents events() {
        return guild.events();
    }

    private void finishBounty(Member member) {
        EventPools.Bounty bounty = events().bounty();
        Objective o = bounty.objective();
        String fact = o.targets().iterator().next();
        guild.progress(alice, o.kind(), Set.of(fact), o.amount());
    }

    @Test
    void aBountyIsPostedAndWonByTheFirstToClaimIt() {
        Member member = join(alice);
        member.setRank(Rank.A);
        events().tick();
        assertNotNull(events().bounty());
        finishBounty(member);
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
        finishBounty(member);
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
    void aGrandQuestCompletesTogetherAndSharesItsReward() {
        Member a = join(alice);
        Member g = join(gm);
        events().tick();
        EventPools.GrandQuest quest = events().grandQuest();
        Set<String> facts = quest.targets().isEmpty() ? Set.of("ANY") : Set.of(quest.targets().iterator().next());
        guild.progress(alice, quest.kind(), facts, quest.goal() - 5);
        assertEquals(Guild.ClaimResult.NOT_DONE, events().claimGrand(alice).result());
        guild.progress(gm, quest.kind(), facts, 5);
        assertTrue(events().grandDone());
        assertTrue(a.titles().contains("champion"), "the biggest contributor is the champion");

        assertEquals(Guild.ClaimResult.CLAIMED, events().claimGrand(alice).result());
        assertEquals(Guild.ClaimResult.ALREADY_CLAIMED, events().claimGrand(alice).result());
        assertEquals(Guild.ClaimResult.NOT_ELIGIBLE, events().claimGrand(gm).result(), "5 is under the share threshold");
        assertEquals(1, a.stat("grand-quests"));
        assertEquals(0, g.stat("grand-quests"));
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

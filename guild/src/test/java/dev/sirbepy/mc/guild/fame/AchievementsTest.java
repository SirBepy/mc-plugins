package dev.sirbepy.mc.guild.fame;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementsTest extends GuildTestBase {

    private void finishAndClaim(Member member) {
        guild.member(alice); // draws the day's board first, so the placed contract isn't replaced by it
        ActiveContract daily = placeDaily(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        assertTrue(guild.claim(alice, daily).paid());
    }

    @Test
    void registeringGivesTheERankTitleAndTheFounderHisOwn() {
        Member member = join(alice);
        assertTrue(member.titles().contains("rank-E"));
        Member founder = join(gm);
        assertTrue(founder.titles().contains("founder"));
        assertEquals("founder", founder.activeTitle());
    }

    @Test
    void theFirstContractEverIsRecordedOnce() {
        Member a = join(alice);
        finishAndClaim(a);
        assertTrue(a.titles().contains("first-contract"));
        assertEquals("Alice", store.firsts().get("first-contract").name());
        Member g = join(gm);
        ActiveContract daily = placeDaily(g, "e_zombies");
        guild.progress(gm, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        guild.claim(gm, daily);
        assertFalse(g.titles().contains("first-contract"));
    }

    @Test
    void aClaimBetweenThreeAndFiveIsANightOwl() {
        clock.set(LocalDateTime.of(2026, 10, 9, 3, 30));
        Member member = join(alice);
        finishAndClaim(member);
        assertTrue(member.titles().contains("night-owl"));
    }

    @Test
    void sevenDaysInARowIsSteadfast() {
        Member member = join(alice);
        for (int day = 0; day < 7; day++) {
            finishAndClaim(member);
            clock.plusDays(1);
        }
        assertEquals(7, member.streak());
        assertTrue(member.titles().contains("steadfast"));
    }

    @Test
    void aMissedDayResetsTheStreak() {
        Member member = join(alice);
        finishAndClaim(member);
        clock.plusDays(2);
        finishAndClaim(member);
        assertEquals(1, member.streak());
    }

    @Test
    void exploringTwentyFiveStructureKindsMakesAPathfinder() {
        Member member = join(alice);
        for (int i = 0; i < 25; i++) {
            guild.achievements().explored(alice, member, "minecraft:plains", List.of("test:structure_" + i));
        }
        assertTrue(member.titles().contains("pathfinder"));
        assertEquals(1, member.biomesSeen().size());
    }

    @Test
    void theFirstARankIsRecordedAndTheRankTitleGranted() {
        Member member = join(alice);
        member.setRank(Rank.B);
        member.setReputation(Rank.A.reputation());
        member.setContractsDone(Rank.A.contracts());
        guild.startTrial(alice);
        guild.progress(alice, Objective.Kind.BOSS, Set.of("ELDER_GUARDIAN"), 1);
        guild.progress(alice, Objective.Kind.STRUCTURE, Set.of("minecraft:monument"), 1);
        assertEquals(Guild.ClaimResult.PROMOTED, guild.claim(alice, member.trial()).result());
        assertTrue(member.titles().contains("rank-A"));
        assertEquals("Alice", store.firsts().get("first-rank-A").name());
    }

    @Test
    void threeDeathsInOneTrialEarnTheirOwnTitle() {
        Member member = join(alice);
        member.setReputation(Rank.D.reputation());
        member.setContractsDone(Rank.D.contracts());
        guild.startTrial(alice);
        for (int i = 0; i < 3; i++) {
            guild.onDeath(alice);
        }
        assertTrue(member.titles().contains("deaths-regular"));
    }

    @Test
    void titlesSurviveASave() {
        Member member = join(alice);
        alice.getInventory().addItem(new ItemStack(Material.BREAD));
        guild.achievements().grant(alice, member, "night-owl");
        store.save();
        Member back = newStore().member(alice.getUniqueId());
        assertTrue(back.titles().contains("night-owl"));
        assertEquals("night-owl", back.activeTitle());
    }
}

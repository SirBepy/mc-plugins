package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryTest extends GuildTestBase {

    private void fund(long amount) {
        store.treasury().record(clock.nowMillis(), LedgerEntry.Kind.ADJUST, amount, "test");
    }

    @Test
    void theGuildmasterDrawsAtMostAThousandADay() {
        fund(5000);
        assertEquals(1000, guild.withdraw(gm, 5000));
        assertEquals(1000, bank.balance(gm));
        assertEquals(0, guild.withdraw(gm, 1));
        assertEquals(4000, store.treasury().balance());
        clock.plusDays(1);
        assertEquals(400, guild.withdraw(gm, 400));
        assertEquals(600, guild.withdrawable());
    }

    @Test
    void aWithdrawalNeverExceedsTheTreasury() {
        fund(300);
        assertEquals(300, guild.withdrawable());
        assertEquals(300, guild.withdraw(gm, 1000));
        assertEquals(0, store.treasury().balance());
    }

    @Test
    void nobodyElseCanWithdraw() {
        fund(5000);
        assertEquals(0, guild.withdraw(alice, 100));
        assertEquals(5000, store.treasury().balance());
    }

    @Test
    void aCommissionIsEscrowedFromTheTreasury() {
        fund(1000);
        Objective objective = new Objective(Objective.Kind.GATHER, Set.of("OAK_LOG"), 64, "Deliver 64 Oak Log");
        assertEquals(Guild.PostResult.POSTED, guild.postCommission(gm, objective, 300, Rank.E));
        assertEquals(700, store.treasury().balance());
        assertEquals(Guild.PostResult.BAD_REWARD, guild.postCommission(gm, objective, 5, Rank.E));
        assertEquals(Guild.PostResult.BAD_REWARD, guild.postCommission(gm, objective, 1001, Rank.E));
        assertEquals(Guild.PostResult.TREASURY_SHORT, guild.postCommission(gm, objective, 800, Rank.E));
        assertEquals(Guild.PostResult.NOT_GUILDMASTER, guild.postCommission(alice, objective, 100, Rank.E));
    }

    @Test
    void threeCommissionsAWeek() {
        fund(10_000);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 5, "Hunt 5 Zombie");
        for (int i = 0; i < 3; i++) {
            assertEquals(Guild.PostResult.POSTED, guild.postCommission(gm, objective, 50, Rank.E));
        }
        assertEquals(Guild.PostResult.WEEKLY_LIMIT, guild.postCommission(gm, objective, 50, Rank.E));
        clock.plusDays(7);
        assertEquals(Guild.PostResult.POSTED, guild.postCommission(gm, objective, 50, Rank.E));
    }

    @Test
    void aHandInCommissionPaysTheFirstAdventurerAndStoresTheGoods() {
        fund(1000);
        join(alice);
        long before = store.treasury().balance();
        Objective objective = new Objective(Objective.Kind.GATHER, Set.of("OAK_LOG"), 64, "Deliver 64 Oak Log");
        guild.postCommission(gm, objective, 200, Rank.E);
        Commission commission = store.commissions().getFirst();
        give(alice, Material.OAK_LOG, 64);
        double balance = bank.balance(alice);

        Guild.Claim claim = guild.claimCommission(alice, commission);

        assertEquals(Guild.ClaimResult.CLAIMED, claim.result());
        assertEquals(balance + 200, bank.balance(alice));
        assertEquals(0, count(alice, Material.OAK_LOG));
        assertEquals(Commission.Status.CLAIMED, commission.status());
        assertEquals(before - 200, store.treasury().balance(), "paid from escrow, nothing minted");
        assertEquals(64, store.stores().stream().mapToInt(i -> i.getAmount()).sum());
        assertEquals(Guild.ClaimResult.CLOSED, guild.claimCommission(alice, commission).result());

        assertEquals(64, guild.collectStores(gm));
        assertEquals(64, count(gm, Material.OAK_LOG));
    }

    @Test
    void theGuildmasterCantTakeHisOwnCommission() {
        fund(1000);
        Member own = join(gm);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.postCommission(gm, objective, 100, Rank.E);
        Commission commission = store.commissions().getFirst();
        guild.progress(gm, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        assertEquals(0, commission.progress(own.id()));
        assertEquals(Guild.ClaimResult.NOT_ELIGIBLE, guild.claimCommission(gm, commission).result());
        assertTrue(commission.open());
    }

    @Test
    void aCommissionHonoursItsMinimumRank() {
        fund(1000);
        Member member = join(alice);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.postCommission(gm, objective, 100, Rank.D);
        Commission commission = store.commissions().getFirst();
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        assertEquals(0, commission.progress(member.id()));
        member.setRank(Rank.D);
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        assertEquals(Guild.ClaimResult.CLAIMED, guild.claimCommission(alice, commission).result());
        assertEquals(0, member.contractsDone(), "commissions never count toward promotion");
    }

    @Test
    void cancellingRefundsTheTreasury() {
        fund(1000);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.postCommission(gm, objective, 400, Rank.E);
        Commission commission = store.commissions().getFirst();
        assertFalse(guild.cancelCommission(alice, commission));
        assertTrue(guild.cancelCommission(gm, commission));
        assertEquals(1000, store.treasury().balance());
        assertEquals(Commission.Status.CANCELLED, commission.status());
    }

    @Test
    void featuringAndAnnouncingAreTheGuildmastersAlone() {
        fund(1000);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.postCommission(gm, objective, 100, Rank.E);
        Commission commission = store.commissions().getFirst();
        assertFalse(guild.feature(alice, commission));
        assertTrue(guild.feature(gm, commission));
        assertNotNull(guild.featured());

        assertFalse(guild.announce(alice, "hi"));
        assertTrue(guild.announce(gm, "The Guild is open!"));
        assertFalse(guild.announce(gm, "again"), "one per cooldown");
        clock.plusHours(1);
        assertTrue(guild.announce(gm, "again"));
    }
}

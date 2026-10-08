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
        assertEquals(1000, guild.office().withdraw(gm, 5000));
        assertEquals(1000, bank.balance(gm));
        assertEquals(0, guild.office().withdraw(gm, 1));
        assertEquals(4000, store.treasury().balance());
        clock.plusDays(1);
        assertEquals(400, guild.office().withdraw(gm, 400));
        assertEquals(600, guild.office().withdrawable());
    }

    @Test
    void aWithdrawalNeverExceedsTheTreasury() {
        fund(300);
        assertEquals(300, guild.office().withdrawable());
        assertEquals(300, guild.office().withdraw(gm, 1000));
        assertEquals(0, store.treasury().balance());
    }

    @Test
    void nobodyElseCanWithdraw() {
        fund(5000);
        assertEquals(0, guild.office().withdraw(alice, 100));
        assertEquals(5000, store.treasury().balance());
    }

    @Test
    void aCommissionIsEscrowedFromTheTreasury() {
        fund(1000);
        Objective objective = new Objective(Objective.Kind.GATHER, Set.of("OAK_LOG"), 64, "Deliver 64 Oak Log");
        assertEquals(GuildmasterOffice.PostResult.POSTED, guild.office().postCommission(gm, objective, 300, Rank.E));
        assertEquals(700, store.treasury().balance());
        assertEquals(GuildmasterOffice.PostResult.BAD_REWARD, guild.office().postCommission(gm, objective, 5, Rank.E));
        assertEquals(GuildmasterOffice.PostResult.BAD_REWARD, guild.office().postCommission(gm, objective, 1001, Rank.E));
        assertEquals(GuildmasterOffice.PostResult.TREASURY_SHORT, guild.office().postCommission(gm, objective, 800, Rank.E));
        assertEquals(GuildmasterOffice.PostResult.NOT_GUILDMASTER, guild.office().postCommission(alice, objective, 100, Rank.E));
    }

    @Test
    void threeCommissionsAWeek() {
        fund(10_000);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 5, "Hunt 5 Zombie");
        for (int i = 0; i < 3; i++) {
            assertEquals(GuildmasterOffice.PostResult.POSTED, guild.office().postCommission(gm, objective, 50, Rank.E));
        }
        assertEquals(GuildmasterOffice.PostResult.WEEKLY_LIMIT, guild.office().postCommission(gm, objective, 50, Rank.E));
        clock.plusDays(7);
        assertEquals(GuildmasterOffice.PostResult.POSTED, guild.office().postCommission(gm, objective, 50, Rank.E));
    }

    @Test
    void aHandInCommissionPaysTheFirstAdventurerAndStoresTheGoods() {
        fund(1000);
        join(alice);
        long before = store.treasury().balance();
        Objective objective = new Objective(Objective.Kind.GATHER, Set.of("OAK_LOG"), 64, "Deliver 64 Oak Log");
        guild.office().postCommission(gm, objective, 200, Rank.E);
        Commission commission = store.commissions().getFirst();
        give(alice, Material.OAK_LOG, 64);
        double balance = bank.balance(alice);

        Guild.Claim claim = guild.office().claimCommission(alice, commission);

        assertEquals(Guild.ClaimResult.CLAIMED, claim.result());
        assertEquals(balance + 200, bank.balance(alice));
        assertEquals(0, count(alice, Material.OAK_LOG));
        assertEquals(Commission.Status.CLAIMED, commission.status());
        assertEquals(before - 200, store.treasury().balance(), "paid from escrow, nothing minted");
        assertEquals(64, store.stores().stream().mapToInt(i -> i.getAmount()).sum());
        assertEquals(Guild.ClaimResult.CLOSED, guild.office().claimCommission(alice, commission).result());

        assertEquals(64, guild.office().collectStores(gm));
        assertEquals(64, count(gm, Material.OAK_LOG));
    }

    @Test
    void theGuildmasterCantTakeHisOwnCommission() {
        fund(1000);
        Member own = join(gm);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.office().postCommission(gm, objective, 100, Rank.E);
        Commission commission = store.commissions().getFirst();
        guild.progress(gm, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        assertEquals(0, commission.progress(own.id()));
        assertEquals(Guild.ClaimResult.NOT_ELIGIBLE, guild.office().claimCommission(gm, commission).result());
        assertTrue(commission.open());
    }

    @Test
    void aCommissionHonoursItsMinimumRank() {
        fund(1000);
        Member member = join(alice);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.office().postCommission(gm, objective, 100, Rank.D);
        Commission commission = store.commissions().getFirst();
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        assertEquals(0, commission.progress(member.id()));
        member.setRank(Rank.D);
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);
        assertEquals(Guild.ClaimResult.CLAIMED, guild.office().claimCommission(alice, commission).result());
        assertEquals(0, member.contractsDone(), "commissions never count toward promotion");
    }

    @Test
    void cancellingRefundsTheTreasury() {
        fund(1000);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.office().postCommission(gm, objective, 400, Rank.E);
        Commission commission = store.commissions().getFirst();
        assertFalse(guild.office().cancelCommission(alice, commission));
        assertTrue(guild.office().cancelCommission(gm, commission));
        assertEquals(1000, store.treasury().balance());
        assertEquals(Commission.Status.CANCELLED, commission.status());
    }

    @Test
    void featuringAndAnnouncingAreTheGuildmastersAlone() {
        fund(1000);
        Objective objective = new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie");
        guild.office().postCommission(gm, objective, 100, Rank.E);
        Commission commission = store.commissions().getFirst();
        assertFalse(guild.office().feature(alice, commission));
        assertTrue(guild.office().feature(gm, commission));
        assertNotNull(guild.office().featured());

        assertFalse(guild.office().announce(alice, "hi"));
        assertTrue(guild.office().announce(gm, "The Guild is open!"));
        assertFalse(guild.office().announce(gm, "again"), "one per cooldown");
        clock.plusHours(1);
        assertTrue(guild.office().announce(gm, "again"));
    }
}

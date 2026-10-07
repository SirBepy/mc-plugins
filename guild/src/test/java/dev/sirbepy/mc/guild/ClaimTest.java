package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.ContractPool;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTest extends GuildTestBase {

    @Test
    void registeringCostsTheFeeAndFillsTheTreasury() {
        bank.set(alice, 750);
        assertEquals(Guild.RegisterResult.REGISTERED, guild.register(alice));
        assertEquals(250, bank.balance(alice));
        assertEquals(500, store.treasury().balance());
        assertEquals(LedgerEntry.Kind.FEE, store.treasury().ledger().getFirst().kind());
        Member member = guild.member(alice);
        assertEquals(Rank.E, member.rank());
        assertEquals(Guild.RegisterResult.ALREADY_MEMBER, guild.register(alice));
        assertEquals(250, bank.balance(alice));
    }

    @Test
    void tooPoorToRegisterChangesNothing() {
        bank.set(alice, 499);
        assertEquals(Guild.RegisterResult.TOO_POOR, guild.register(alice));
        assertEquals(499, bank.balance(alice));
        assertEquals(0, store.treasury().balance());
        assertEquals(null, guild.member(alice));
    }

    @Test
    void theGuildmasterStartsAtERankLikeEveryone() {
        Member member = join(gm);
        assertEquals(Rank.E, member.rank());
        assertEquals(0, member.reputation());
    }

    @Test
    void aFinishedContractPaysInFullAndTheCommissionIsMintedOnTop() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        int levelsBefore = alice.getLevel();

        Guild.Claim claim = guild.claim(alice, daily);

        assertEquals(Guild.ClaimResult.CLAIMED, claim.result());
        assertEquals(25, claim.coins());
        assertEquals(25, bank.balance(alice));
        assertEquals(levelsBefore + 1, alice.getLevel());
        assertEquals(10, member.reputation());
        assertEquals(1, member.contractsDone());
        // 500 fee + 5% of 25 rounded = 1
        assertEquals(501, store.treasury().balance());
        assertEquals(LedgerEntry.Kind.COMMISSION, store.treasury().ledger().getFirst().kind());
        assertEquals(Guild.ClaimResult.ALREADY_CLAIMED, guild.claim(alice, daily).result());
    }

    @Test
    void anUnfinishedContractPaysNothing() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 11);
        assertEquals(Guild.ClaimResult.NOT_DONE, guild.claim(alice, daily).result());
        assertEquals(0, bank.balance(alice));
    }

    @Test
    void theDailyCoinCapClipsAClaim() {
        Member member = join(alice);
        member.setCoinsToday(config.get().dailyCoinCap() - 10);
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        Guild.Claim claim = guild.claim(alice, daily);
        assertEquals(10, claim.coins());
        assertTrue(claim.clipped());
        assertEquals(config.get().dailyCoinCap(), member.coinsToday());
        assertEquals(10, member.reputation(), "reputation is never clipped");
    }

    @Test
    void theDailyLevelCapClipsAClaim() {
        Member member = join(alice);
        member.setLevelsToday(15);
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        int before = alice.getLevel();
        Guild.Claim claim = guild.claim(alice, daily);
        assertEquals(0, claim.levels());
        assertEquals(before, alice.getLevel());
    }

    @Test
    void theCapsResetAtMidnight() {
        Member member = join(alice);
        member.setCoinsToday(2000);
        member.setLevelsToday(15);
        clock.plusDays(1);
        guild.member(alice);
        assertEquals(0, member.coinsToday());
        assertEquals(0, member.levelsToday());
    }

    @Test
    void aHandInTakesTheItemsAtClaim() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_bread");
        give(alice, Material.BREAD, 10);
        assertEquals(Guild.ClaimResult.MISSING_ITEMS, guild.claim(alice, daily).result());
        assertEquals(10, count(alice, Material.BREAD));
        give(alice, Material.BREAD, 10);
        assertEquals(Guild.ClaimResult.CLAIMED, guild.claim(alice, daily).result());
        assertEquals(4, count(alice, Material.BREAD));
    }

    @Test
    void aRenamedItemIsNeverTaken() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_bread");
        ItemStack named = new ItemStack(Material.BREAD, 16);
        ItemMeta meta = named.getItemMeta();
        meta.displayName(Component.text("Grandma's Loaf"));
        named.setItemMeta(meta);
        alice.getInventory().addItem(named);
        assertEquals(Guild.ClaimResult.MISSING_ITEMS, guild.claim(alice, daily).result());
    }

    @Test
    void progressOnlyCountsMatchingDeeds() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("SKELETON", "#HOSTILE"), 5);
        assertEquals(0, daily.progress(0));
        guild.progress(alice, Objective.Kind.HUNT, Set.of("HUSK", "#HOSTILE"), 1);
        assertEquals(1, daily.progress(0));
    }

    @Test
    void aWeeklyPaysFiveTimesADaily() {
        Member member = join(alice);
        member.setWeekly(ActiveContract.fresh(ContractPool.byId("we_patrol"), Rank.E));
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE", "#HOSTILE"), 40);
        Guild.Claim claim = guild.claim(alice, member.weekly());
        assertEquals(125, claim.coins());
        assertEquals(40, claim.reputation());
        assertEquals(5, claim.levels());
    }

    @Test
    void aClosedBankKeepsTheContractReady() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        bank.up = false;
        assertEquals(Guild.ClaimResult.NO_BANK, guild.claim(alice, daily).result());
        assertFalse(daily.claimed());
    }

    @Test
    void reachingTheNextRanksNumbersOffersItsTrial() {
        Member member = join(alice);
        member.setReputation(140);
        member.setContractsDone(5);
        assertFalse(Guild.qualifies(member));
        ActiveContract daily = place(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        guild.claim(alice, daily);
        assertTrue(Guild.qualifies(member));
        assertEquals(Rank.E, member.rank(), "numbers alone never promote");
    }

    /** Puts a specific contract in daily slot 1, so a test doesn't depend on the random draw. */
    static ActiveContract place(Member member, String id) {
        ContractDef def = ContractPool.byId(id);
        ActiveContract active = ActiveContract.fresh(def, def.rank());
        member.dailies().set(1, active);
        return active;
    }
}

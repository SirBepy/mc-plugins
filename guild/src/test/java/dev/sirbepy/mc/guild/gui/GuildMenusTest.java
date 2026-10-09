package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildMenusTest extends GuildTestBase {

    private GuildMenus menus;

    @BeforeEach
    void setUpMenus() {
        menus = new GuildMenus(guild);
    }

    private static String name(ItemStack item) {
        // Frame panes carry no name at all.
        var display = item.getItemMeta().displayName();
        return display == null ? "" : PlainTextComponentSerializer.plainText().serialize(display);
    }

    /** As if {@code player} just talked to a staff member standing right beside them. */
    private void atDesk(org.bukkit.entity.Player player) {
        menus.desk().visit(player, player.getLocation());
    }

    /** A gather contract with nothing carried stays rerollable, whatever the zombie kills finished on the drawn board. */
    private static void unfinishedInSlot0(Member member) {
        var logs = dev.sirbepy.mc.guild.contract.ContractPool.byId("e_logs");
        member.dailies().set(0, ActiveContract.fresh(logs, logs.rank()));
    }

    private String lastLine(org.mockbukkit.mockbukkit.entity.PlayerMock player) {
        String last = null;
        for (var m = player.nextComponentMessage(); m != null; m = player.nextComponentMessage()) {
            last = PlainTextComponentSerializer.plainText().serialize(m);
        }
        return last;
    }

    private static String lore(ItemStack item) {
        StringBuilder out = new StringBuilder();
        item.getItemMeta().lore().forEach(line -> out.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n'));
        return out.toString();
    }

    @Test
    void aStrangerSeesRegistrationAndCanJoinFromTheMenu() {
        bank.set(alice, 600);
        atDesk(alice);
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        ItemStack register = menu.getInventory().getItem(GuildMenus.REGISTER_SLOT);
        assertEquals("Register as an Adventurer", name(register));
        assertTrue(lore(register).contains("500 coins"));

        menu.click(GuildMenus.REGISTER_SLOT, ClickType.LEFT);

        assertNotNull(guild.member(alice));
        assertEquals(100, bank.balance(alice));
        assertTrue(name(menu.getInventory().getItem(GuildMenus.DAILY_SLOTS[0])).length() > 0);
    }

    @Test
    void theBoardShowsEveryRewardBeforeTheWork() {
        Member member = join(alice);
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        assertEquals(5, member.dailies().size());
        for (int i = 0; i < 5; i++) {
            ItemStack icon = menu.getInventory().getItem(GuildMenus.DAILY_SLOTS[i]);
            assertEquals(member.dailies().get(i).def().title(), name(icon));
            assertTrue(lore(icon).contains("Reward: "), lore(icon));
        }
        assertTrue(lore(menu.getInventory().getItem(GuildMenus.WEEKLY_SLOT)).contains("Reward: "));
    }

    @Test
    void aDailyPastWhatTheLevelCapLeavesShowsTheClip() {
        Member member = join(alice);
        member.setLevelsToday(15);
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        String lore = lore(menu.getInventory().getItem(GuildMenus.DAILY_SLOTS[4]));
        assertTrue(lore.contains("Today's limits leave room for"), lore);
        assertTrue(lore.contains("and 0 levels"), lore);
    }

    @Test
    void theTrialIconHidesTheTrialUntilQualified() {
        Member member = join(alice);
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        String locked = lore(menu.getInventory().getItem(GuildMenus.TRIAL_SLOT));
        assertTrue(locked.contains("nobody says"), locked);
        assertTrue(!locked.contains("Iron Sword"));

        member.setReputation(Rank.D.reputation());
        member.setContractsDone(Rank.D.contracts());
        atDesk(alice);
        menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        menu.click(GuildMenus.TRIAL_SLOT, ClickType.LEFT);
        assertEquals("trial_d", member.trial().defId());
    }

    @Test
    void awayFromTheHallAStrangerCanReadTheRegistrationButNotRegister() {
        bank.set(alice, 600);
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        assertTrue(lore(menu.getInventory().getItem(GuildMenus.REGISTER_SLOT)).contains("/warp guild"));

        menu.click(GuildMenus.REGISTER_SLOT, ClickType.LEFT);

        assertNull(store.member(alice.getUniqueId()));
        assertEquals(600, bank.balance(alice));
        assertTrue(lastLine(alice).contains("Guild Hall"));
    }

    @Test
    void awayFromTheHallTheBoardShowsFinishedWorkButTakesNone() {
        Member member = join(alice);
        ActiveContract daily = placeDaily(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        unfinishedInSlot0(member);
        member.setReputation(Rank.D.reputation());
        member.setContractsDone(Rank.D.contracts());
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        assertTrue(lore(menu.getInventory().getItem(GuildMenus.DAILY_SLOTS[1])).contains("turn it in at the Guild Hall"));
        assertTrue(lore(menu.getInventory().getItem(GuildMenus.PROFILE_SLOT)).contains("/warp guild"), "the profile says where");

        menu.click(GuildMenus.DAILY_SLOTS[1], ClickType.LEFT);
        menu.click(GuildMenus.DAILY_SLOTS[0], ClickType.RIGHT);
        menu.click(GuildMenus.TRIAL_SLOT, ClickType.LEFT);

        assertFalse(daily.claimed());
        assertEquals(0, bank.balance(alice));
        assertEquals(0, member.rerollsUsed());
        assertNull(member.trial());
        assertTrue(lastLine(alice).contains("Guild Hall"));
    }

    @Test
    void atTheDeskTheSameClicksTakeAndTurnInWork() {
        Member member = join(alice);
        ActiveContract daily = placeDaily(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        unfinishedInSlot0(member);
        atDesk(alice);
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);

        menu.click(GuildMenus.DAILY_SLOTS[1], ClickType.LEFT);
        menu.click(GuildMenus.DAILY_SLOTS[0], ClickType.RIGHT);

        assertTrue(daily.claimed());
        assertEquals(25, bank.balance(alice));
        assertEquals(1, member.rerollsUsed());
    }

    @Test
    void walkingAwayFromTheStaffLeavesTheDesk() {
        Member member = join(alice);
        ActiveContract daily = placeDaily(member, "e_zombies");
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 12);
        atDesk(alice);
        alice.teleport(alice.getLocation().add(HallDesk.REACH + 1, 0, 0));
        assertFalse(menus.desk().at(alice));
        alice.teleport(alice.getLocation().add(-2, 0, 0));
        assertTrue(menus.desk().at(alice), "back within reach of the staff member");
        alice.teleport(new Location(server.addSimpleWorld("elsewhere"), 0, 64, 0));

        menus.build(alice, GuildMenus.Screen.MAIN, 0).click(GuildMenus.DAILY_SLOTS[1], ClickType.LEFT);

        assertFalse(daily.claimed());
    }

    @Test
    void aCommissionIsOnlyClaimedAtTheDesk() {
        join(alice);
        join(gm);
        store.treasury().record(clock.nowMillis(), LedgerEntry.Kind.ADJUST, 1000, "test");
        guild.office().postCommission(gm, new Objective(Objective.Kind.HUNT, Set.of("ZOMBIE"), 1, "Hunt 1 Zombie"), 50, Rank.E);
        Commission commission = store.commissions().getFirst();
        guild.progress(alice, Objective.Kind.HUNT, Set.of("ZOMBIE"), 1);

        menus.build(alice, GuildMenus.Screen.COMMISSIONS, 0).click(10, ClickType.LEFT);
        assertEquals(Commission.Status.OPEN, commission.status());

        atDesk(alice);
        menus.build(alice, GuildMenus.Screen.COMMISSIONS, 0).click(10, ClickType.LEFT);
        assertEquals(Commission.Status.CLAIMED, commission.status());
    }

    @Test
    void onlyTheGuildmasterSeesTheOffice() {
        join(alice);
        join(gm);
        Menu aliceMenu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        ItemStack slot = aliceMenu.getInventory().getItem(GuildMenus.OFFICE_SLOT);
        assertTrue(slot == null || !name(slot).contains("Office"));
        Menu office = menus.build(alice, GuildMenus.Screen.OFFICE, 0);
        assertTrue(!"Treasury".equals(name(office.getInventory().getItem(19))), "a member asking for the office gets the board");

        Menu gmMenu = menus.build(gm, GuildMenus.Screen.MAIN, 0);
        assertEquals("Guildmaster's Office", name(gmMenu.getInventory().getItem(GuildMenus.OFFICE_SLOT)));
        Menu gmOffice = menus.build(gm, GuildMenus.Screen.OFFICE, 0);
        assertEquals("Treasury", name(gmOffice.getInventory().getItem(19)));
    }

    @Test
    void theBoardShowsTheBountyAndGrandQuestAndLeadsToTheHallOfFameAndTitles() {
        join(alice);
        guild.events().tick();
        Menu menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        assertEquals(guild.events().bounty().title(), name(menu.getInventory().getItem(FameMenus.BOUNTY_SLOT)));
        assertEquals(guild.events().grandQuest().title(), name(menu.getInventory().getItem(FameMenus.GRAND_SLOT)));

        menu.click(GuildMenus.INFO_SLOT, ClickType.LEFT);
        assertEquals("The Hall of Fame", name(menu.getInventory().getItem(4)));
        menu.click(45, ClickType.LEFT);
        menu.click(GuildMenus.PROFILE_SLOT, ClickType.LEFT);
        assertEquals("Titles", name(menu.getInventory().getItem(4)));
        assertTrue(lore(menu.getInventory().getItem(4)).contains("E-Rank Adventurer"), "the rank title is worn");
    }

    @Test
    void theOfficeWithdrawButtonPaysTheGuildmaster() {
        join(alice);
        join(gm);
        Menu office = menus.build(gm, GuildMenus.Screen.OFFICE, 0);
        office.click(20, ClickType.LEFT);
        assertEquals(100, bank.balance(gm));
        assertEquals(900, store.treasury().balance());
    }
}

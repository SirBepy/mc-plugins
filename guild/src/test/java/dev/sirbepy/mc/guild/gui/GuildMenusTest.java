package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static String lore(ItemStack item) {
        StringBuilder out = new StringBuilder();
        item.getItemMeta().lore().forEach(line -> out.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n'));
        return out.toString();
    }

    @Test
    void aStrangerSeesRegistrationAndCanJoinFromTheMenu() {
        bank.set(alice, 600);
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
        for (int i = 0; i < 3; i++) {
            ItemStack icon = menu.getInventory().getItem(GuildMenus.DAILY_SLOTS[i]);
            assertEquals(member.dailies().get(i).def().title(), name(icon));
            assertTrue(lore(icon).contains("Reward: "), lore(icon));
        }
        assertTrue(lore(menu.getInventory().getItem(GuildMenus.WEEKLY_SLOT)).contains("Reward: "));
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
        menu = menus.build(alice, GuildMenus.Screen.MAIN, 0);
        menu.click(GuildMenus.TRIAL_SLOT, ClickType.LEFT);
        assertEquals("trial_d", member.trial().defId());
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
    void theOfficeWithdrawButtonPaysTheGuildmaster() {
        join(alice);
        join(gm);
        Menu office = menus.build(gm, GuildMenus.Screen.OFFICE, 0);
        office.click(20, ClickType.LEFT);
        assertEquals(100, bank.balance(gm));
        assertEquals(900, store.treasury().balance());
    }
}

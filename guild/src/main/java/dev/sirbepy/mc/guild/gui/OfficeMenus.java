package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/** The Guildmaster's Office: Treasury withdrawals, statistics, and the Members and Ledger pages it opens into. */
final class OfficeMenus {

    private final Guild guild;

    OfficeMenus(Guild guild) {
        this.guild = guild;
    }

    Menu office(Player player, BiConsumer<Menu, GuildMenus.Screen> go) {
        Menu menu = new Menu(6, Component.text("Guildmaster's Office", NamedTextColor.GOLD));
        var treasury = guild.store().treasury();
        long withdrawable = guild.office().withdrawable();
        menu.set(GuildMenus.PROFILE_SLOT, Icons.icon(Icons.head(player), "Guildmaster " + player.getName(), NamedTextColor.GOLD,
                List.of("Founder of the Adventurer's Guild.",
                        "Your adventurer rank is earned like anyone's.")));
        menu.set(19, Icons.icon(Material.GOLD_BLOCK, "Treasury", NamedTextColor.GOLD,
                "Balance: " + Text.coins(treasury.balance()),
                "Taken in, all time: " + Text.coins(treasury.totalIn()),
                "Paid out, all time: " + Text.coins(treasury.totalOut()),
                "",
                "You may draw " + Text.coins(withdrawable) + " more today",
                "(" + Text.coins(guild.config().guildmasterDailyWithdrawal()) + " a day)."));
        int[] amounts = {100, 500};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            menu.set(20 + i, Icons.icon(Material.GOLD_NUGGET, "Withdraw " + Text.coins(amount), NamedTextColor.YELLOW,
                    withdrawable > 0 ? "!Click to take it" : "Nothing left to draw today"),
                    (shown, click) -> {
                        withdraw(player, guild, amount);
                        go.accept(shown, GuildMenus.Screen.OFFICE);
                    });
        }
        menu.set(22, Icons.icon(Material.GOLD_INGOT, "Withdraw all allowed", NamedTextColor.YELLOW,
                "That is " + Text.coins(withdrawable) + " now.",
                withdrawable > 0 ? "!Click to take it" : "Nothing left to draw today"),
                (shown, click) -> {
                    withdraw(player, guild, withdrawable);
                    go.accept(shown, GuildMenus.Screen.OFFICE);
                });
        menu.set(24, Icons.icon(Material.WRITTEN_BOOK, "Ledger", Chat.BRONZE,
                "Every coin in and out of the Treasury.", "", "!Click to read"),
                (shown, click) -> go.accept(shown, GuildMenus.Screen.LEDGER));
        menu.set(25, Icons.icon(Material.PLAYER_HEAD, "Members", Chat.BRONZE,
                guild.store().members().size() + " adventurers", "", "!Click to view"),
                (shown, click) -> go.accept(shown, GuildMenus.Screen.MEMBERS));
        menu.set(29, statistics());
        menu.set(31, Icons.icon(Material.BELL, "Commissions", Chat.BRONZE,
                "Posted this week: " + guild.office().commissionsThisWeek() + " / " + guild.config().commissionsPerWeek(),
                "Reward " + guild.config().commissionMinReward() + "-" + guild.config().commissionMaxReward()
                        + " coins, taken from the Treasury.",
                "",
                "/guild commission <hunt|gather|mine|fish>",
                "  <target> <amount> <reward> [min rank]",
                "",
                "!Click to manage"), (shown, click) -> go.accept(shown, GuildMenus.Screen.COMMISSIONS));
        int stored = guild.store().stores().stream().mapToInt(ItemStack::getAmount).sum();
        menu.set(33, Icons.icon(Material.CHEST, "Your stores", Chat.BRONZE,
                stored + " items handed in for your commissions",
                stored > 0 ? "!Click to collect" : ""), (shown, click) -> {
            int taken = guild.office().collectStores(player);
            if (taken > 0) {
                Chat.send(player, "You collect " + taken + " items from the Guild stores.");
            }
            go.accept(shown, GuildMenus.Screen.OFFICE);
        });
        long wait = guild.office().announceWait();
        menu.set(40, Icons.icon(Material.GOAT_HORN, "Announcements", Chat.BRONZE,
                "/guild announce <message>",
                wait > 0 ? "Next one in " + wait + " min" : "!Ready",
                "One every " + guild.config().announcementCooldownMinutes() + " minutes."));
        GuildMenus.back(menu, go);
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE);
        return menu;
    }

    /** Shared by the Office screen's withdraw buttons and {@link GuildChannel}'s mod-screen click. */
    static void withdraw(Player player, Guild guild, long amount) {
        long paid = guild.office().withdraw(player, amount);
        if (paid > 0) {
            Chat.send(player, "You draw " + Text.coins(paid) + " from the Treasury.", NamedTextColor.GOLD);
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.4f);
        } else {
            Chat.send(player, "Nothing can be drawn right now.", NamedTextColor.YELLOW);
        }
    }

    private ItemStack statistics() {
        int[] byRank = new int[Rank.values().length];
        int contracts = 0;
        int today = 0;
        long coins = 0;
        String day = guild.clock().dayKey();
        for (Member m : guild.store().members()) {
            byRank[m.rank().ordinal()]++;
            contracts += m.contractsDone();
            coins += m.stat("coins-earned");
            if (day.equals(m.boardDay())) {
                today += (int) m.dailies().stream().filter(ActiveContract::claimed).count();
            }
        }
        List<String> lore = new ArrayList<>();
        StringBuilder ranks = new StringBuilder();
        for (Rank rank : Rank.values()) {
            ranks.append(rank.name()).append(": ").append(byRank[rank.ordinal()]).append("  ");
        }
        lore.add(ranks.toString().trim());
        lore.add("Contracts completed: " + contracts + " (" + today + " dailies today)");
        lore.add("Paid to adventurers: " + Text.coins(coins));
        long commissionsDone = guild.store().commissions().stream().filter(c -> c.status() == Commission.Status.CLAIMED).count();
        lore.add("Commissions completed: " + commissionsDone);
        return Icons.icon(Material.MAP, "Statistics", Chat.BRONZE, lore);
    }

    Menu members(int page, GuildMenus.PageNav nav) {
        Menu menu = new Menu(6, Component.text("Guild Members", NamedTextColor.GOLD));
        List<Member> sorted = new ArrayList<>(guild.store().members());
        sorted.sort(Comparator.comparing((Member m) -> m.rank().ordinal()).reversed()
                .thenComparing(Comparator.comparingInt(Member::reputation).reversed()));
        int from = page * 45;
        for (int i = from; i < Math.min(sorted.size(), from + 45); i++) {
            Member m = sorted.get(i);
            menu.set(i - from, Icons.icon(Icons.head(Bukkit.getOfflinePlayer(m.id())), m.name(), m.rank().color(),
                    List.of(m.rank().title(),
                            "Reputation: " + m.reputation(),
                            "Contracts: " + m.contractsDone() + ", commissions: " + m.stat("commissions-done"),
                            "Trials passed: " + m.stat("trials-passed"),
                            "Joined: " + Text.dateTime(m.joinedAt(), guild.config().zone()),
                            "Last active: " + Text.dateTime(m.lastActive(), guild.config().zone()))));
        }
        pager(menu, GuildMenus.Screen.MEMBERS, page, sorted.size(), nav);
        return menu;
    }

    Menu ledger(int page, GuildMenus.PageNav nav) {
        Menu menu = new Menu(6, Component.text("Treasury Ledger", NamedTextColor.GOLD));
        List<LedgerEntry> entries = guild.store().treasury().ledger();
        int from = page * 45;
        for (int i = from; i < Math.min(entries.size(), from + 45); i++) {
            LedgerEntry e = entries.get(i);
            menu.set(i - from, Icons.icon(e.amount() >= 0 ? Material.LIME_DYE : Material.RED_DYE,
                    (e.amount() >= 0 ? "+" : "") + Text.coins(e.amount()), e.amount() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED,
                    List.of(e.kind().label(), "By: " + e.who(),
                            "When: " + Text.dateTime(e.time(), guild.config().zone()),
                            "Balance after: " + Text.coins(e.balanceAfter()))));
        }
        pager(menu, GuildMenus.Screen.LEDGER, page, entries.size(), nav);
        return menu;
    }

    private void pager(Menu menu, GuildMenus.Screen screen, int page, int total, GuildMenus.PageNav nav) {
        if (page > 0) {
            menu.set(45, Icons.icon(Material.ARROW, "Previous page", Chat.PARCHMENT),
                    (shown, click) -> nav.go(shown, screen, page - 1));
        }
        if ((page + 1) * 45 < total) {
            menu.set(53, Icons.icon(Material.ARROW, "Next page", Chat.PARCHMENT),
                    (shown, click) -> nav.go(shown, screen, page + 1));
        }
        menu.set(GuildMenus.BACK_SLOT, Icons.icon(Material.OAK_DOOR, "Back to the office", Chat.PARCHMENT),
                (shown, click) -> nav.go(shown, GuildMenus.Screen.OFFICE, 0));
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE);
    }
}

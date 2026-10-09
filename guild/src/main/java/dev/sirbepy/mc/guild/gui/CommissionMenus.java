package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** The Guildmaster's Commissions screen: posted contracts paid from the Treasury, open to every eligible member. */
final class CommissionMenus {

    private final Guild guild;
    private final HallDesk desk;

    CommissionMenus(Guild guild, HallDesk desk) {
        this.guild = guild;
        this.desk = desk;
    }

    Menu commissions(Player player, Member member, BiConsumer<Menu, GuildMenus.Screen> go) {
        Menu menu = new Menu(6, Component.text("Guildmaster's Commissions", Chat.BRONZE));
        boolean gm = guild.isGuildmaster(player);
        menu.set(GuildMenus.PROFILE_SLOT, Icons.icon(Material.BELL, "Guildmaster's Commissions", Chat.BRONZE,
                "Posted by the Guildmaster, paid from the",
                "Treasury. Everyone eligible works on them",
                "at once; the first to claim one wins it.",
                "",
                gm ? "Posted this week: " + guild.office().commissionsThisWeek() + " / " + guild.config().commissionsPerWeek()
                        : "Gathered goods go to the Guildmaster.",
                gm ? "Left-click: feature. Right-click: cancel." : ""));
        List<Commission> open = guild.store().commissions().stream().filter(Commission::open).toList();
        int slot = 10;
        for (Commission commission : open) {
            if (slot > 43) {
                break;
            }
            menu.set(slot, commissionIcon(player, member, commission, false), (shown, click) -> {
                if (gm) {
                    if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                        if (guild.office().cancelCommission(player, commission)) {
                            Chat.send(player, "Commission cancelled; " + Text.coins(commission.reward())
                                    + " went back to the Treasury.");
                        }
                    } else if (guild.office().feature(player, commission)) {
                        Chat.send(player, "Commission featured.");
                    }
                } else {
                    desk.require(player, () -> claimCommission(player, commission));
                }
                go.accept(shown, GuildMenus.Screen.COMMISSIONS);
            });
            slot += slot % 9 == 7 ? 3 : 1;
        }
        if (open.isEmpty()) {
            menu.set(22, Icons.icon(Material.PAPER, "No open commissions", NamedTextColor.GRAY,
                    gm ? "Post one: /guild commission <hunt|gather|mine|fish> <target> <amount> <reward> [min rank]"
                            : "The Guildmaster hasn't posted any right now."));
        }
        GuildMenus.back(menu, go);
        menu.frame(Material.BROWN_STAINED_GLASS_PANE);
        return menu;
    }

    ItemStack commissionIcon(Player player, Member member, Commission commission, boolean featured) {
        Objective o = commission.objective();
        boolean gm = guild.isGuildmaster(player);
        boolean eligible = member != null && guild.office().eligibleFor(member, commission);
        List<String> lore = new ArrayList<>();
        lore.add((featured ? "Featured commission, " : "Commission " + commission.id() + ", ")
                + commission.minRank().name() + "-Rank and up");
        lore.add("");
        if (eligible) {
            int value = o.kind() == Objective.Kind.GATHER ? Guild.carried(player, o) : commission.progress(member.id());
            lore.add((value >= o.amount() ? "!" : "") + (o.kind() == Objective.Kind.GATHER ? "Carried: " : "Progress: ")
                    + value + " / " + o.amount());
        }
        lore.add("Reward: " + Text.coins(commission.reward()) + ", +" + Guild.COMMISSION_REPUTATION + " reputation");
        lore.add("Paid from the Treasury, not counted toward daily limits.");
        lore.add("");
        if (gm) {
            lore.add("Your commission. You can't take it yourself.");
        } else if (!eligible) {
            lore.add("Open to " + commission.minRank().name() + "-Rank adventurers and up.");
        } else {
            lore.add(o.kind() == Objective.Kind.GATHER ? "!Click to hand them in and claim" : "!Click to claim when done");
        }
        return Icons.icon(featured ? Material.BELL : Material.FILLED_MAP, o.text(), NamedTextColor.GOLD, lore);
    }

    void claimCommission(Player player, Commission commission) {
        Guild.Claim claim = guild.office().claimCommission(player, commission);
        switch (claim.result()) {
            case CLAIMED -> {
                Chat.send(player, "Commission complete: " + commission.objective().text() + ". Paid "
                        + Text.coins(claim.coins()) + " from the Treasury.", NamedTextColor.GREEN);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            }
            case NOT_DONE -> Chat.send(player, "Not finished yet.", NamedTextColor.YELLOW);
            case MISSING_ITEMS -> Chat.send(player, "You aren't carrying all of it.", NamedTextColor.YELLOW);
            case CLOSED -> Chat.send(player, "Someone got there first.", NamedTextColor.GRAY);
            case NOT_ELIGIBLE -> Chat.send(player, "This commission is open to "
                    + commission.minRank().name() + "-Rank and up, and never to the Guildmaster.", NamedTextColor.GRAY);
            case NO_BANK -> Chat.send(player, "The Guild's bank is closed right now.", NamedTextColor.RED);
            case ALREADY_CLAIMED, PROMOTED -> { }
        }
    }
}

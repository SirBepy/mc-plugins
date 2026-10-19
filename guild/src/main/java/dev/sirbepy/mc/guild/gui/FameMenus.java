package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.EventPools;
import dev.sirbepy.mc.guild.events.FirstDragon;
import dev.sirbepy.mc.guild.events.ServerEvents;
import dev.sirbepy.mc.guild.fame.Titles;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * The server-wide side of the Guild: the bounty, Grand Quest and activity icons on the board, the Hall
 * of Fame, and the title picker. Secret titles appear only once earned.
 */
final class FameMenus {

    static final int BOUNTY_SLOT = 11;
    static final int GRAND_SLOT = 13;
    static final int ACTIVITY_SLOT = 15;
    static final int SHOW_SLOT = 49;

    private final Guild guild;

    FameMenus(Guild guild) {
        this.guild = guild;
    }

    // ---- board icons ----

    ItemStack bountyIcon(Member member) {
        ServerEvents events = guild.events();
        EventPools.Bounty bounty = events.bounty();
        if (bounty == null) {
            return Icons.icon(Material.PAPER, "Bounty Board", NamedTextColor.GRAY, "No bounty posted right now.");
        }
        List<String> lore = new ArrayList<>();
        lore.add("Server-wide bounty, " + bounty.minRank().name() + "-Rank and up");
        lore.add("First to finish and claim it wins.");
        lore.add("");
        if (events.bountyClaimed()) {
            lore.add("Claimed by " + events.bountyClaimedBy() + ". A new one comes soon.");
            return Icons.icon(Material.PAPER, bounty.title(), NamedTextColor.DARK_GRAY, lore);
        }
        boolean eligible = events.bountyEligible(member);
        int progress = events.bountyProgress(member);
        if (eligible) {
            lore.add((progress >= bounty.objective().amount() ? "!" : "") + "Progress: " + progress + " / " + bounty.objective().amount());
        } else {
            lore.add("Open to " + bounty.minRank().name() + "-Rank adventurers and up.");
        }
        lore.add("Reward: " + Text.coins(bounty.coins()) + ", +" + bounty.reputation() + " reputation, +2 levels");
        lore.add("Gone in " + Text.duration(events.bountyEnds() - guild.clock().nowMillis()) + " if nobody claims it.");
        if (eligible && progress >= bounty.objective().amount()) {
            lore.add("");
            lore.add("!Click to claim it");
            return Icons.glint(Icons.icon(bounty.icon(), bounty.title(), NamedTextColor.GOLD, lore));
        }
        return Icons.icon(bounty.icon(), bounty.title(), NamedTextColor.GOLD, lore);
    }

    /** Shared by the board's bounty icon and {@link GuildChannel}'s mod-screen click. */
    static void claimBounty(Player player, Guild guild) {
        Guild.Claim claim = guild.events().claimBounty(player);
        switch (claim.result()) {
            case CLAIMED -> {
                Chat.send(player, "Bounty claimed! Paid " + Text.coins(claim.coins()) + ", " + claim.reputation()
                        + " reputation." + (claim.clipped() ? " (Today's limits held back the rest.)" : ""), NamedTextColor.GREEN);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
            case NOT_DONE -> Chat.send(player, "Not finished yet.", NamedTextColor.YELLOW);
            case CLOSED -> Chat.send(player, "Someone got there first.", NamedTextColor.GRAY);
            case NO_BANK -> Chat.send(player, "The Guild's bank is closed right now.", NamedTextColor.RED);
            default -> { }
        }
    }

    ItemStack grandIcon(Member member) {
        ServerEvents events = guild.events();
        EventPools.GrandQuest quest = events.grandQuest();
        if (quest == null) {
            return Icons.icon(Material.PAPER, "Grand Quest", NamedTextColor.GRAY, "The next one starts soon.");
        }
        List<String> lore = new ArrayList<>();
        lore.add("This week's Grand Quest, for the whole server");
        lore.add("");
        lore.add((events.grandDone() ? "!" : "") + "Together: " + Math.min(events.grandTotal(), quest.goal()) + " / " + quest.goal());
        int mine = events.contribution(member);
        lore.add((mine >= events.threshold() ? "!" : "") + "Yours: " + mine + " (" + events.threshold() + " earns a share)");
        lore.add("Share: " + Text.coins(ServerEvents.GRAND_COINS) + ", +" + ServerEvents.GRAND_REPUTATION + " reputation, +"
                + ServerEvents.GRAND_LEVELS + " levels");
        lore.add("The one who gives the most earns a title.");
        boolean claimable = events.grandDone() && mine >= events.threshold() && !events.grandClaimed(member);
        if (events.grandClaimed(member)) {
            lore.add("");
            lore.add("Your share is claimed.");
        } else if (claimable) {
            lore.add("");
            lore.add("!Click to claim your share");
        }
        ItemStack icon = Icons.icon(quest.icon(), quest.title(), NamedTextColor.AQUA, lore);
        return claimable ? Icons.glint(icon) : icon;
    }

    /** Shared by the board's Grand Quest icon and {@link GuildChannel}'s mod-screen click. */
    static void claimGrand(Player player, Guild guild) {
        Guild.Claim claim = guild.events().claimGrand(player);
        switch (claim.result()) {
            case CLAIMED -> {
                Chat.send(player, "Your Grand Quest share: " + Text.coins(claim.coins()) + ", " + claim.reputation()
                        + " reputation, " + claim.levels() + " levels.", NamedTextColor.GREEN);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
            case NOT_DONE -> Chat.send(player, "The Grand Quest isn't complete yet. Keep at it, and bring friends.", NamedTextColor.YELLOW);
            case NOT_ELIGIBLE -> Chat.send(player, "Give at least " + guild.events().threshold() + " to earn a share.", NamedTextColor.YELLOW);
            case ALREADY_CLAIMED -> Chat.send(player, "You already took your share.", NamedTextColor.GRAY);
            case NO_BANK -> Chat.send(player, "The Guild's bank is closed right now.", NamedTextColor.RED);
            default -> { }
        }
    }

    ItemStack activityIcon(Member member) {
        ServerEvents events = guild.events();
        ServerEvents.ActivityKind kind = events.activity();
        if (kind == null) {
            return null;
        }
        return Icons.glint(Icons.icon(Material.GOAT_HORN, kind.title() + " (running)", NamedTextColor.GOLD,
                "Opened by the Guildmaster: " + kind.goal() + ".",
                "Prize: " + Text.coins(events.activityPrize()) + " from the Treasury",
                "Your score: " + events.score(member),
                "Ends in " + Text.duration(events.activityEnds() - guild.clock().nowMillis())));
    }

    // ---- Hall of Fame ----

    Menu hallOfFame(Player player, BiConsumer<Menu, GuildMenus.Screen> go) {
        Menu menu = new Menu(6, Component.text("Hall of Fame", NamedTextColor.GOLD));
        GuildStore store = guild.store();
        menu.set(4, Icons.icon(Material.GOLDEN_HELMET, "The Hall of Fame", NamedTextColor.GOLD,
                "Kept by Pip Quillfeather, archivist.",
                "Firsts never change hands; records do."));
        int slot = 10;
        for (Rank rank : Rank.values()) {
            if (rank == Rank.E) {
                continue;
            }
            GuildStore.First first = store.firsts().get("first-rank-" + rank.name());
            menu.set(slot++, first == null
                    ? Icons.icon(Material.GRAY_DYE, "First " + rank.title(), NamedTextColor.GRAY, "Unclaimed. Yet.")
                    : Icons.icon(Material.NAME_TAG, "First " + rank.title(), rank.color(),
                    first.name(), "on " + Text.dateTime(first.time(), guild.config().zone())));
        }
        GuildStore.First firstContract = store.firsts().get("first-contract");
        menu.set(16, Icons.icon(Material.PAPER, "The First Contract", Chat.BRONZE,
                firstContract == null ? "Unclaimed." : firstContract.name(),
                firstContract == null ? "" : "on " + Text.dateTime(firstContract.time(), guild.config().zone())));
        menu.set(22, firstDragon(store));
        menu.set(28, leaderboard(Material.WRITABLE_BOOK, "Most contracts", Member::contractsDone));
        menu.set(30, leaderboard(Material.EXPERIENCE_BOTTLE, "Most reputation", Member::reputation));
        menu.set(32, leaderboard(Material.CLOCK, "Longest daily streak", m -> m.stat("best-streak")));
        menu.set(34, leaderboard(Material.CROSSBOW, "Most bounties", m -> m.stat("bounties")));
        menu.set(38, leaderboard(Material.BELL, "Most commissions", m -> m.stat("commissions-done")));
        menu.set(40, leaderboard(Material.COMPASS, "Most places explored", m -> m.structuresSeen().size()));
        menu.set(42, leaderboard(Material.NAME_TAG, "Most titles", m -> m.titles().size()));
        menu.set(45, Icons.icon(Material.OAK_DOOR, "Back to the board", Chat.PARCHMENT),
                (shown, click) -> go.accept(shown, GuildMenus.Screen.MAIN));
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE);
        return menu;
    }

    /** The party that brought down the server's first Ender Dragon, set once by {@link FirstDragon}. */
    ItemStack firstDragon(GuildStore store) {
        if (!FirstDragon.done(store)) {
            return Icons.icon(Material.GRAY_DYE, "The First Dragon", NamedTextColor.GRAY, "Unclaimed. Yet.");
        }
        if (FirstDragon.earlier(store)) {
            return Icons.icon(Material.DRAGON_HEAD, "The First Dragon", NamedTextColor.GRAY, "It fell before the Guild kept records.");
        }
        Map<UUID, String> party = FirstDragon.party(store);
        List<String> lore = new ArrayList<>();
        lore.add("The party that brought it down:");
        lore.addAll(party.isEmpty() ? List.of("no Guild adventurer fought it") : party.values());
        lore.add("on " + Text.dateTime(FirstDragon.time(store), guild.config().zone()));
        return Icons.icon(Material.DRAGON_HEAD, "The First Dragon", NamedTextColor.LIGHT_PURPLE, lore);
    }

    private ItemStack leaderboard(Material icon, String name, ToIntFunction<Member> score) {
        List<Member> members = new ArrayList<>(guild.store().members());
        members.sort(Comparator.comparingInt(score).reversed());
        List<String> lore = new ArrayList<>();
        int place = 1;
        for (Member m : members) {
            int value = score.applyAsInt(m);
            if (value <= 0 || place > 5) {
                break;
            }
            lore.add(place++ + ". " + m.name() + ": " + value);
        }
        if (lore.isEmpty()) {
            lore.add("Nobody yet.");
        }
        return Icons.icon(icon, name, Chat.BRONZE, lore);
    }

    // ---- titles ----

    private static ItemStack showToggle(Member member) {
        if (member.showTitle()) {
            return Icons.glint(Icons.icon(Material.NAME_TAG, "Title beside your name: on", NamedTextColor.GREEN,
                    "Your worn title shows beside your name", "in chat and the tab list.", "", "Click to hide it"));
        }
        return Icons.icon(Material.GRAY_DYE, "Title beside your name: off", NamedTextColor.GRAY,
                "Show your worn title beside your name", "in chat and the tab list.", "", "Click to show it");
    }

    Menu titles(Player player, Member member, BiConsumer<Menu, GuildMenus.Screen> go) {
        Menu menu = new Menu(6, Component.text("Your Titles", Chat.BRONZE));
        menu.set(4, Icons.icon(Material.NAME_TAG, "Titles", Chat.BRONZE,
                "Wearing: " + (member.activeTitle() == null ? "none" : Titles.name(member.activeTitle())),
                "Earned " + member.titles().size() + ". Others wait to be discovered.",
                "Click one to wear it."));
        int slot = 9;
        for (String id : member.titles()) {
            Titles.Title title = Titles.get(id);
            if (title == null || slot > 44) {
                continue;
            }
            boolean wearing = id.equals(member.activeTitle());
            ItemStack icon = Icons.icon(wearing ? Material.GOLDEN_HELMET : Material.PAPER, title.name(),
                    wearing ? NamedTextColor.GOLD : Chat.PARCHMENT, "Earned for " + title.earnedFor() + ".",
                    wearing ? "!Wearing it" : "Click to wear it");
            menu.set(slot++, wearing ? Icons.glint(icon) : icon, (shown, click) -> {
                guild.wearTitle(member, id);
                Chat.send(player, "You now go by \"" + title.name() + "\".");
                go.accept(shown, GuildMenus.Screen.TITLES);
            });
        }
        menu.set(SHOW_SLOT, showToggle(member), (shown, click) -> {
            guild.showTitle(member, !member.showTitle());
            Chat.send(player, member.showTitle()
                    ? "Your title now shows beside your name in chat and the tab list."
                    : "Your title no longer shows beside your name.");
            go.accept(shown, GuildMenus.Screen.TITLES);
        });
        menu.set(45, Icons.icon(Material.OAK_DOOR, "Back to the board", Chat.PARCHMENT),
                (shown, click) -> go.accept(shown, GuildMenus.Screen.MAIN));
        menu.frame(Material.BROWN_STAINED_GLASS_PANE);
        return menu;
    }
}

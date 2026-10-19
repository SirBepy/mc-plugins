package dev.sirbepy.mc.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.ServerEvents;
import dev.sirbepy.mc.guild.fame.Titles;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every JSON shape {@link GuildChannel} sends to the tree mod: one object per screen, every row's
 * label/tooltip precomputed here. Split out of {@code GuildChannel} (which keeps the
 * {@code PluginMessageListener} dispatch and the click handling) so one class builds snapshots and
 * the other decides when to send them, the same split {@link dev.sirbepy.mc.classes.talent.TreeSnapshots}
 * already made for {@code classes:tree}. Kept separate from {@link GuildSnapshotText} (done/189): that
 * class builds the display-text lines, this class assembles the JSON shape around them.
 */
final class GuildSnapshots {

    /** The snapshot's shape; the mod ignores versions it doesn't know, so a new shape needs a new number. */
    static final int VERSION = 1;
    /** A page of Members/Ledger rows on the mod screen; smaller than the chest's 45 since a vertical
     * list of text rows, not a 9-wide grid, can't fit nearly as many before the panel over-scales. */
    static final int LIST_PAGE_SIZE = 10;

    private final Guild guild;
    private final GuildMenus menus;
    private final GuildSnapshotText text;

    GuildSnapshots(Guild guild, GuildMenus menus, GuildSnapshotText text) {
        this.guild = guild;
        this.menus = menus;
        this.text = text;
    }

    // ---- snapshot building: one JSON object per screen, every row's text precomputed here ----

    JsonObject snapshot(Player player, GuildMenus.Screen screen, int page) {
        Member member = guild.member(player);
        if (member == null && screen != GuildMenus.Screen.OFFICE) {
            return registrationSnapshot(player);
        }
        return switch (screen) {
            case MAIN -> mainSnapshot(player, member);
            case COMMISSIONS -> commissionsSnapshot(player, member);
            case OFFICE -> guild.isGuildmaster(player) ? officeSnapshot() : mainSnapshot(player, member);
            case MEMBERS -> membersSnapshot(page);
            case LEDGER -> ledgerSnapshot(page);
            case TITLES -> titlesSnapshot(member);
            case HALL_OF_FAME -> hallOfFameSnapshot();
        };
    }

    private JsonObject base(String screenName, String title, List<String> subtitle) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "guild");
        o.addProperty("v", VERSION);
        o.addProperty("screen", screenName);
        o.addProperty("title", title);
        JsonArray sub = new JsonArray();
        subtitle.forEach(sub::add);
        o.add("subtitle", sub);
        o.add("rows", new JsonArray());
        return o;
    }

    private void row(JsonObject snapshot, String id, String label, boolean clickable, List<String> lines) {
        JsonObject r = new JsonObject();
        r.addProperty("id", id);
        r.addProperty("label", label);
        r.addProperty("clickable", clickable);
        JsonArray arr = new JsonArray();
        lines.forEach(arr::add);
        r.add("lines", arr);
        snapshot.getAsJsonArray("rows").add(r);
    }

    private void backRow(JsonObject snapshot, GuildMenus.Screen current) {
        if (backTarget(current) == null) return;
        boolean toOffice = current == GuildMenus.Screen.MEMBERS || current == GuildMenus.Screen.LEDGER;
        row(snapshot, "back", toOffice ? "Back to the office" : "Back to the board", true, List.of());
    }

    private void pagerRows(JsonObject snapshot, int page, int total) {
        if (page > 0) row(snapshot, "page:" + (page - 1), "Previous page", true, List.of());
        if ((page + 1) * LIST_PAGE_SIZE < total) row(snapshot, "page:" + (page + 1), "Next page", true, List.of());
    }

    static GuildMenus.Screen backTarget(GuildMenus.Screen screen) {
        return switch (screen) {
            case MAIN -> null;
            case COMMISSIONS, OFFICE, TITLES, HALL_OF_FAME -> GuildMenus.Screen.MAIN;
            case MEMBERS, LEDGER -> GuildMenus.Screen.OFFICE;
        };
    }

    static GuildMenus.Screen parseScreen(String name) {
        return switch (name) {
            case "main" -> GuildMenus.Screen.MAIN;
            case "commissions" -> GuildMenus.Screen.COMMISSIONS;
            case "office" -> GuildMenus.Screen.OFFICE;
            case "members" -> GuildMenus.Screen.MEMBERS;
            case "ledger" -> GuildMenus.Screen.LEDGER;
            case "titles" -> GuildMenus.Screen.TITLES;
            case "hallOfFame" -> GuildMenus.Screen.HALL_OF_FAME;
            default -> null;
        };
    }

    private JsonObject registrationSnapshot(Player player) {
        JsonObject snap = base("registration", "The Adventurer's Guild", List.of(
                "The Adventurer's Guild takes contracts from every corner of this world:",
                "hunts, deliveries, expeditions, bosses, and stranger things.",
                "Members start at E-Rank and climb to S through reputation, contracts and trials."));
        row(snap, "register", "Register as an Adventurer", true, text.registerLines(player));
        return snap;
    }

    private JsonObject mainSnapshot(Player player, Member member) {
        JsonObject snap = base("main", "Adventurer's Guild: " + member.rank().title(), List.of());
        boolean atDesk = menus.desk().at(player);
        row(snap, "profile", player.getName(), true, text.profileLines(player, member));
        row(snap, "bounty", text.bountyLabel(), true, text.bountyLines(member));
        row(snap, "grand", text.grandLabel(), true, text.grandLines(member));
        ServerEvents.ActivityKind activity = guild.events().activity();
        if (activity != null) {
            row(snap, "activity", activity.title() + " (running)", false, text.activityLines(member));
        }
        for (int i = 0; i < member.dailies().size(); i++) {
            ActiveContract daily = member.dailies().get(i);
            ContractDef def = daily.def();
            row(snap, "daily" + i, def == null ? "Unavailable" : def.title(), true,
                    text.contractLines(player, member, daily, true, atDesk));
        }
        ActiveContract weekly = member.weekly();
        if (weekly != null) {
            ContractDef def = weekly.def();
            row(snap, "weekly", def == null ? "Unavailable" : def.title(), true,
                    text.contractLines(player, member, weekly, false, atDesk));
        }
        Commission featured = guild.office().featured();
        if (featured != null) {
            row(snap, "featured", featured.objective().text(), true, text.commissionLines(player, member, featured, true));
        }
        row(snap, "trial", text.trialLabel(member), true, text.trialLines(player, member));
        long open = guild.store().commissions().stream().filter(Commission::open).count();
        row(snap, "commissions", "Guildmaster's Commissions", true, List.of(open + " open",
                "Contracts the Guildmaster pays for from the Treasury. First to finish and claim one wins it.",
                "", "!Click to view"));
        row(snap, "info", "The Guild", true, text.guildInfoLines());
        if (guild.isGuildmaster(player)) {
            row(snap, "office", "Guildmaster's Office", true, List.of("Treasury, members, ledger, statistics,",
                    "commissions and your stores.", "", "!Click to enter"));
        }
        return snap;
    }

    private JsonObject commissionsSnapshot(Player player, Member member) {
        boolean gm = guild.isGuildmaster(player);
        JsonObject snap = base("commissions", "Guildmaster's Commissions", List.of(
                "Posted by the Guildmaster, paid from the Treasury. Everyone eligible works on them",
                "at once; the first to claim one wins it.",
                gm ? "Posted this week: " + guild.office().commissionsThisWeek() + " / " + guild.config().commissionsPerWeek()
                        : "Gathered goods go to the Guildmaster.",
                gm ? "Left-click: feature. Right-click: cancel." : ""));
        List<Commission> open = guild.store().commissions().stream().filter(Commission::open).toList();
        for (Commission commission : open) {
            row(snap, "commission:" + commission.id(), commission.objective().text(), true,
                    text.commissionLines(player, member, commission, false));
        }
        if (open.isEmpty()) {
            row(snap, "none", "No open commissions", false, List.of(gm
                    ? "Post one: /guild commission <hunt|gather|mine|fish> <target> <amount> <reward> [min rank]"
                    : "The Guildmaster hasn't posted any right now."));
        }
        backRow(snap, GuildMenus.Screen.COMMISSIONS);
        return snap;
    }

    private JsonObject officeSnapshot() {
        JsonObject snap = base("office", "Guildmaster's Office", List.of());
        row(snap, "treasury", "Treasury", false, text.treasuryLines());
        long withdrawable = guild.office().withdrawable();
        row(snap, "withdraw100", "Withdraw " + Text.coins(100), true, text.withdrawLines(withdrawable));
        row(snap, "withdraw500", "Withdraw " + Text.coins(500), true, text.withdrawLines(withdrawable));
        row(snap, "withdrawAll", "Withdraw all allowed", true, text.withdrawLines(withdrawable));
        row(snap, "ledger", "Ledger", true, List.of("Every coin in and out of the Treasury.", "", "!Click to read"));
        row(snap, "members", "Members", true, List.of(guild.store().members().size() + " adventurers", "", "!Click to view"));
        row(snap, "statistics", "Statistics", false, text.statisticsLines());
        row(snap, "commissions", "Commissions", true, List.of(
                "Posted this week: " + guild.office().commissionsThisWeek() + " / " + guild.config().commissionsPerWeek(),
                "Reward " + guild.config().commissionMinReward() + "-" + guild.config().commissionMaxReward()
                        + " coins, taken from the Treasury.",
                "", "/guild commission <hunt|gather|mine|fish>", "  <target> <amount> <reward> [min rank]", "", "!Click to manage"));
        row(snap, "stores", "Your stores", true, text.storesLines());
        row(snap, "announcements", "Announcements", false, text.announcementLines());
        backRow(snap, GuildMenus.Screen.OFFICE);
        return snap;
    }

    private JsonObject membersSnapshot(int page) {
        JsonObject snap = base("members", "Guild Members", List.of());
        List<Member> sorted = new ArrayList<>(guild.store().members());
        sorted.sort(Comparator.comparing((Member m) -> m.rank().ordinal()).reversed()
                .thenComparing(Comparator.comparingInt(Member::reputation).reversed()));
        int from = page * LIST_PAGE_SIZE;
        for (int i = from; i < Math.min(sorted.size(), from + LIST_PAGE_SIZE); i++) {
            Member m = sorted.get(i);
            row(snap, "member:" + m.id(), m.name(), false, text.memberLines(m));
        }
        pagerRows(snap, page, sorted.size());
        backRow(snap, GuildMenus.Screen.MEMBERS);
        return snap;
    }

    private JsonObject ledgerSnapshot(int page) {
        JsonObject snap = base("ledger", "Treasury Ledger", List.of());
        List<LedgerEntry> entries = guild.store().treasury().ledger();
        int from = page * LIST_PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + LIST_PAGE_SIZE); i++) {
            LedgerEntry e = entries.get(i);
            row(snap, "ledger:" + i, (e.amount() >= 0 ? "+" : "") + Text.coins(e.amount()), false, text.ledgerLines(e));
        }
        pagerRows(snap, page, entries.size());
        backRow(snap, GuildMenus.Screen.LEDGER);
        return snap;
    }

    private JsonObject titlesSnapshot(Member member) {
        JsonObject snap = base("titles", "Your Titles", List.of(
                "Wearing: " + (member.activeTitle() == null ? "none" : Titles.name(member.activeTitle())),
                "Earned " + member.titles().size() + ". Others wait to be discovered.",
                "Click one to wear it."));
        for (String id : member.titles()) {
            Titles.Title title = Titles.get(id);
            if (title == null) continue;
            boolean wearing = id.equals(member.activeTitle());
            row(snap, "title:" + id, title.name(), !wearing, List.of("Earned for " + title.earnedFor() + ".",
                    wearing ? "!Wearing it" : "!Click to wear it"));
        }
        row(snap, "showToggle", member.showTitle() ? "Title beside your name: on" : "Title beside your name: off",
                true, text.showToggleLines(member));
        backRow(snap, GuildMenus.Screen.TITLES);
        return snap;
    }

    private JsonObject hallOfFameSnapshot() {
        JsonObject snap = base("hallOfFame", "The Hall of Fame", List.of(
                "Kept by Pip Quillfeather, archivist.", "Firsts never change hands; records do."));
        GuildStore store = guild.store();
        for (Rank rank : Rank.values()) {
            if (rank == Rank.E) continue;
            GuildStore.First first = store.firsts().get("first-rank-" + rank.name());
            row(snap, "first:" + rank.name(), "First " + rank.title(), false,
                    first == null ? List.of("Unclaimed. Yet.")
                            : List.of(first.name(), "on " + Text.dateTime(first.time(), guild.config().zone())));
        }
        GuildStore.First firstContract = store.firsts().get("first-contract");
        row(snap, "firstContract", "The First Contract", false,
                firstContract == null ? List.of("Unclaimed.")
                        : List.of(firstContract.name(), "on " + Text.dateTime(firstContract.time(), guild.config().zone())));
        row(snap, "firstDragon", "The First Dragon", false, text.firstDragonLines());
        row(snap, "mostContracts", "Most contracts", false, text.leaderboardLines(Member::contractsDone));
        row(snap, "mostReputation", "Most reputation", false, text.leaderboardLines(Member::reputation));
        row(snap, "longestStreak", "Longest daily streak", false, text.leaderboardLines(m -> m.stat("best-streak")));
        row(snap, "mostBounties", "Most bounties", false, text.leaderboardLines(m -> m.stat("bounties")));
        row(snap, "mostCommissions", "Most commissions", false, text.leaderboardLines(m -> m.stat("commissions-done")));
        row(snap, "mostExplored", "Most places explored", false, text.leaderboardLines(m -> m.structuresSeen().size()));
        row(snap, "mostTitles", "Most titles", false, text.leaderboardLines(m -> m.titles().size()));
        backRow(snap, GuildMenus.Screen.HALL_OF_FAME);
        return snap;
    }
}

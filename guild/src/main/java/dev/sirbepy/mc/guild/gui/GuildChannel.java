package dev.sirbepy.mc.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.core.gui.JsonSnapshot;
import dev.sirbepy.mc.guild.Chat;
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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * The {@code guild:ui} plugin channel to the Claude SMP tree mod (treemod/). A player whose client
 * registers the channel gets every Guild chest menu as the mod's walnut-and-gold screen instead
 * (Joe, 2026-10-16: "lets try to use the new uis for everything... for now lets just do it for
 * everything related to this mod"). A player without the mod keeps the chest menus unchanged,
 * since {@link #supports} gates on channel registration alone, same base gate as {@code classes:tree}'s
 * {@link dev.sirbepy.mc.classes.talent.TreeChannel#supports}; there is no older guild-aware mod version
 * to fall back from, so an unmodded or too-old client simply never registers this channel at all.
 *
 * <p>Both directions carry one UTF-8 JSON object, nothing else (no length prefix), the same shape as
 * {@code classes:tree}. The server sends {@code {"type":"guild",...}}: a screen name, a title, header
 * lines and a flat list of rows (id, label, clickable, tooltip lines) - everything the screen draws,
 * precomputed server-side, so the mod holds no Guild rules. Unlike the talent tree this channel always
 * sends a fresh full screen rather than an in-place update, since no Guild screen animates. The mod
 * sends {@code {"op":"click","screen":...,"row":...,"button":...}}; every row's click runs through the
 * exact same {@link Guild}/{@link dev.sirbepy.mc.guild.GuildmasterOffice}/{@link ServerEvents} methods
 * a chest click already calls (see {@link GuildMenus}, {@link FameMenus}, {@link CommissionMenus},
 * {@link TrialMenus}), never a parallel rule path; only the chat feedback text is re-sent here rather
 * than shared, the same tolerance {@code TreeChannel} takes for its own "respec" feedback.
 */
public final class GuildChannel implements PluginMessageListener {

    public static final String CHANNEL = "guild:ui";
    /** The snapshot's shape; the mod ignores versions it doesn't know, so a new shape needs a new number. */
    static final int VERSION = 1;
    /** A page of Members/Ledger rows on the mod screen; smaller than the chest's 45 since a vertical
     * list of text rows, not a 9-wide grid, can't fit nearly as many before the panel over-scales. */
    static final int LIST_PAGE_SIZE = 10;

    private final Guild guild;
    private final GuildMenus menus;
    private final GuildSnapshotText text;
    private final Predicate<Player> supports;
    private final BiConsumer<Player, byte[]> sender;
    /** The "hello" version each connected client advertised, for a future shape bump; unused today
     * since every client that ever registers {@link #CHANNEL} already understands version 1. */
    private final Map<UUID, Integer> clientVersions = new ConcurrentHashMap<>();

    /** {@code supports} and {@code sender} are the player's channel list and sendPluginMessage; tests pass fakes. */
    GuildChannel(Guild guild, GuildMenus menus, Predicate<Player> supports, BiConsumer<Player, byte[]> sender) {
        this.guild = guild;
        this.menus = menus;
        this.text = new GuildSnapshotText(guild, menus);
        this.supports = supports;
        this.sender = sender;
    }

    /** The real channel: registers both directions with the server's messenger. */
    public static GuildChannel register(Plugin plugin, Guild guild, GuildMenus menus) {
        GuildChannel channel = new GuildChannel(guild, menus,
                p -> p.getListeningPluginChannels().contains(CHANNEL),
                (p, bytes) -> p.sendPluginMessage(plugin, CHANNEL, bytes));
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, channel);
        return channel;
    }

    public boolean supports(Player player) {
        return supports.test(player);
    }

    /** Sends {@code screen} fresh, for {@link GuildMenus#open} and {@link GuildMenus#openHallOfFame}. */
    public void send(Player player, GuildMenus.Screen screen) {
        send(player, screen, 0);
    }

    private void send(Player player, GuildMenus.Screen screen, int page) {
        sender.accept(player, snapshot(player, screen, page).toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(new String(message, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            request = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        handle(player, request);
    }

    /** One request from the mod. Anything malformed or out of place is ignored, never trusted. */
    void handle(Player player, JsonObject request) {
        String op = JsonSnapshot.text(request, "op");
        if (op == null) return;
        if (op.equals("hello")) {
            clientVersions.put(player.getUniqueId(), JsonSnapshot.intOr(request, "v", 1));
            return;
        }
        if (!op.equals("click")) return;
        String screenName = JsonSnapshot.text(request, "screen");
        String row = JsonSnapshot.text(request, "row");
        String button = JsonSnapshot.text(request, "button");
        if (screenName == null || row == null) return;
        if ("registration".equals(screenName)) {
            handleRegistration(player, row);
            return;
        }
        GuildMenus.Screen screen = parseScreen(screenName);
        if (screen == null) return;
        if (row.equals("back")) {
            GuildMenus.Screen target = backTarget(screen);
            if (target != null) send(player, target, 0);
            return;
        }
        if (row.startsWith("page:")) {
            send(player, screen, parseIntSafe(row.substring("page:".length())));
            return;
        }
        switch (screen) {
            case MAIN -> handleMain(player, row, button);
            case COMMISSIONS -> handleCommissions(player, row, button);
            case OFFICE -> handleOffice(player, row);
            case TITLES -> handleTitles(player, row);
            default -> { }
        }
    }

    // ---- click handling: every op below calls the exact method a chest click already calls ----

    private void handleRegistration(Player player, String row) {
        if (!row.equals("register")) return;
        if (!menus.desk().at(player)) {
            Chat.send(player, "Registration is at the Guild Hall's front desk: talk to Lyra there. "
                    + HallDesk.WARP, NamedTextColor.YELLOW);
            return;
        }
        switch (guild.register(player)) {
            case REGISTERED -> {
                Chat.send(player, "Welcome to the Adventurer's Guild, " + player.getName()
                        + ". You are an E-Rank Adventurer. Your first contracts are on the board.", NamedTextColor.GOLD);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case TOO_POOR -> Chat.send(player, "Registration costs " + Text.coins(guild.config().registrationFee())
                    + ". You have " + Text.coins((long) guild.bank().balance(player)) + ".", NamedTextColor.RED);
            case NO_BANK -> Chat.send(player, "The Guild's bank is closed right now. Try again later.", NamedTextColor.RED);
            case ALREADY_MEMBER -> send(player, GuildMenus.Screen.MAIN, 0);
        }
    }

    private void handleMain(Player player, String row, String button) {
        Member member = guild.member(player);
        if (member == null) return;
        HallDesk desk = menus.desk();
        if (row.startsWith("daily")) {
            int idx = parseIntSafe(row.substring("daily".length()));
            if (idx < 0 || idx >= member.dailies().size()) return;
            ActiveContract active = member.dailies().get(idx);
            if ("right".equals(button)) {
                desk.require(player, () -> GuildMenus.reroll(player, guild, member, idx));
            } else {
                desk.require(player, () -> GuildMenus.claim(player, guild, active));
            }
            send(player, GuildMenus.Screen.MAIN, 0);
            return;
        }
        switch (row) {
            case "profile" -> send(player, GuildMenus.Screen.TITLES, 0);
            case "bounty" -> {
                desk.require(player, () -> FameMenus.claimBounty(player, guild));
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case "grand" -> {
                desk.require(player, () -> FameMenus.claimGrand(player, guild));
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case "trial" -> {
                desk.require(player, () -> trialClick(player, member));
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case "weekly" -> {
                ActiveContract weekly = member.weekly();
                if (weekly != null) desk.require(player, () -> GuildMenus.claim(player, guild, weekly));
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case "featured" -> {
                Commission featured = guild.office().featured();
                if (featured != null) desk.require(player, () -> claimCommission(player, featured));
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case "commissions" -> send(player, GuildMenus.Screen.COMMISSIONS, 0);
            case "info" -> send(player, GuildMenus.Screen.HALL_OF_FAME, 0);
            case "office" -> {
                if (guild.isGuildmaster(player)) send(player, GuildMenus.Screen.OFFICE, 0);
            }
            default -> { }
        }
    }

    private void handleCommissions(Player player, String row, String button) {
        if (!row.startsWith("commission:")) return;
        Commission commission = guild.store().commission(row.substring("commission:".length()));
        if (commission == null) return;
        if (guild.isGuildmaster(player)) {
            if ("right".equals(button)) {
                if (guild.office().cancelCommission(player, commission)) {
                    Chat.send(player, "Commission cancelled; " + Text.coins(commission.reward()) + " went back to the Treasury.");
                }
            } else if (guild.office().feature(player, commission)) {
                Chat.send(player, "Commission featured.");
            }
        } else {
            menus.desk().require(player, () -> claimCommission(player, commission));
        }
        send(player, GuildMenus.Screen.COMMISSIONS, 0);
    }

    private void handleOffice(Player player, String row) {
        if (!guild.isGuildmaster(player)) return;
        switch (row) {
            case "withdraw100" -> {
                OfficeMenus.withdraw(player, guild, 100);
                send(player, GuildMenus.Screen.OFFICE, 0);
            }
            case "withdraw500" -> {
                OfficeMenus.withdraw(player, guild, 500);
                send(player, GuildMenus.Screen.OFFICE, 0);
            }
            case "withdrawAll" -> {
                OfficeMenus.withdraw(player, guild, guild.office().withdrawable());
                send(player, GuildMenus.Screen.OFFICE, 0);
            }
            case "ledger" -> send(player, GuildMenus.Screen.LEDGER, 0);
            case "members" -> send(player, GuildMenus.Screen.MEMBERS, 0);
            case "commissions" -> send(player, GuildMenus.Screen.COMMISSIONS, 0);
            case "stores" -> {
                collectStores(player);
                send(player, GuildMenus.Screen.OFFICE, 0);
            }
            default -> { }
        }
    }

    private void handleTitles(Player player, String row) {
        Member member = guild.member(player);
        if (member == null) return;
        if (row.equals("showToggle")) {
            guild.showTitle(member, !member.showTitle());
            Chat.send(player, member.showTitle()
                    ? "Your title now shows beside your name in chat and the tab list."
                    : "Your title no longer shows beside your name.");
            send(player, GuildMenus.Screen.TITLES, 0);
            return;
        }
        if (row.startsWith("title:")) {
            String id = row.substring("title:".length());
            Titles.Title title = Titles.get(id);
            if (title == null || !member.titles().contains(id)) return;
            guild.wearTitle(member, id);
            Chat.send(player, "You now go by \"" + title.name() + "\".");
            send(player, GuildMenus.Screen.TITLES, 0);
        }
    }

    // ---- small action wrappers: the chat feedback a chest click would show, calling the real rule method ----

    private void trialClick(Player player, Member member) {
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            GuildMenus.claim(player, guild, trial);
            return;
        }
        switch (guild.startTrial(player)) {
            case STARTED -> {
                Chat.send(player, "Your Promotion Trial has begun: " + member.trial().def().title()
                        + ". The examiner is watching.", Chat.BRONZE);
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
            }
            case COOLDOWN -> Chat.send(player, "Not yet. Rest, and come back when the examiner calls.", NamedTextColor.YELLOW);
            case NOT_ELIGIBLE, ALREADY_ACTIVE -> { }
        }
    }

    private void claimCommission(Player player, Commission commission) {
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

    private void collectStores(Player player) {
        int taken = guild.office().collectStores(player);
        if (taken > 0) {
            Chat.send(player, "You collect " + taken + " items from the Guild stores.");
        }
    }

    // ---- snapshot building: one JSON object per screen, every row's text precomputed here ----

    private JsonObject snapshot(Player player, GuildMenus.Screen screen, int page) {
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

    private static GuildMenus.Screen backTarget(GuildMenus.Screen screen) {
        return switch (screen) {
            case MAIN -> null;
            case COMMISSIONS, OFFICE, TITLES, HALL_OF_FAME -> GuildMenus.Screen.MAIN;
            case MEMBERS, LEDGER -> GuildMenus.Screen.OFFICE;
        };
    }

    private static GuildMenus.Screen parseScreen(String name) {
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

    // ---- JSON helpers ----

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

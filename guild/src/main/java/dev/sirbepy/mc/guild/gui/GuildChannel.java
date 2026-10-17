package dev.sirbepy.mc.guild.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Reward;
import dev.sirbepy.mc.guild.contract.Trials;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.EventPools;
import dev.sirbepy.mc.guild.events.FirstDragon;
import dev.sirbepy.mc.guild.events.ServerEvents;
import dev.sirbepy.mc.guild.fame.Titles;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import java.util.function.ToIntFunction;

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
    private final Predicate<Player> supports;
    private final BiConsumer<Player, byte[]> sender;
    /** The "hello" version each connected client advertised, for a future shape bump; unused today
     * since every client that ever registers {@link #CHANNEL} already understands version 1. */
    private final Map<UUID, Integer> clientVersions = new ConcurrentHashMap<>();

    /** {@code supports} and {@code sender} are the player's channel list and sendPluginMessage; tests pass fakes. */
    GuildChannel(Guild guild, GuildMenus menus, Predicate<Player> supports, BiConsumer<Player, byte[]> sender) {
        this.guild = guild;
        this.menus = menus;
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
        String op = text(request, "op");
        if (op == null) return;
        if (op.equals("hello")) {
            clientVersions.put(player.getUniqueId(), intOr(request, "v", 1));
            return;
        }
        if (!op.equals("click")) return;
        String screenName = text(request, "screen");
        String row = text(request, "row");
        String button = text(request, "button");
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
                desk.require(player, () -> reroll(player, member, idx));
            } else {
                desk.require(player, () -> GuildMenus.claim(player, guild, active));
            }
            send(player, GuildMenus.Screen.MAIN, 0);
            return;
        }
        switch (row) {
            case "profile" -> send(player, GuildMenus.Screen.TITLES, 0);
            case "bounty" -> {
                desk.require(player, () -> claimBounty(player));
                send(player, GuildMenus.Screen.MAIN, 0);
            }
            case "grand" -> {
                desk.require(player, () -> claimGrand(player));
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
                withdraw(player, 100);
                send(player, GuildMenus.Screen.OFFICE, 0);
            }
            case "withdraw500" -> {
                withdraw(player, 500);
                send(player, GuildMenus.Screen.OFFICE, 0);
            }
            case "withdrawAll" -> {
                withdraw(player, guild.office().withdrawable());
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

    private void claimBounty(Player player) {
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

    private void claimGrand(Player player) {
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

    private void reroll(Player player, Member member, int slot) {
        switch (guild.board().reroll(member, slot)) {
            case REROLLED -> {
                guild.store().save();
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            }
            case NO_REROLLS_LEFT -> Chat.send(player, "No rerolls left today. You get " + member.rank().rerolls()
                    + " a day at " + member.rank().name() + "-Rank.", NamedTextColor.YELLOW);
            case NOT_REROLLABLE -> Chat.send(player, "That one is done or finished; claim it instead.", NamedTextColor.YELLOW);
            case NOTHING_ELSE -> Chat.send(player, "The board has nothing else of that kind today.", NamedTextColor.YELLOW);
        }
    }

    private void withdraw(Player player, long amount) {
        long paid = guild.office().withdraw(player, amount);
        if (paid > 0) {
            Chat.send(player, "You draw " + Text.coins(paid) + " from the Treasury.", NamedTextColor.GOLD);
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.4f);
        } else {
            Chat.send(player, "Nothing can be drawn right now.", NamedTextColor.YELLOW);
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
        row(snap, "register", "Register as an Adventurer", true, registerLines(player));
        return snap;
    }

    private JsonObject mainSnapshot(Player player, Member member) {
        JsonObject snap = base("main", "Adventurer's Guild: " + member.rank().title(), List.of());
        boolean atDesk = menus.desk().at(player);
        row(snap, "profile", player.getName(), true, profileLines(player, member));
        row(snap, "bounty", bountyLabel(), true, bountyLines(member));
        row(snap, "grand", grandLabel(), true, grandLines(member));
        ServerEvents.ActivityKind activity = guild.events().activity();
        if (activity != null) {
            row(snap, "activity", activity.title() + " (running)", false, activityLines(member));
        }
        for (int i = 0; i < member.dailies().size(); i++) {
            ActiveContract daily = member.dailies().get(i);
            ContractDef def = daily.def();
            row(snap, "daily" + i, def == null ? "Unavailable" : def.title(), true,
                    contractLines(player, member, daily, true, atDesk));
        }
        ActiveContract weekly = member.weekly();
        if (weekly != null) {
            ContractDef def = weekly.def();
            row(snap, "weekly", def == null ? "Unavailable" : def.title(), true,
                    contractLines(player, member, weekly, false, atDesk));
        }
        Commission featured = guild.office().featured();
        if (featured != null) {
            row(snap, "featured", featured.objective().text(), true, commissionLines(player, member, featured, true));
        }
        row(snap, "trial", trialLabel(member), true, trialLines(player, member));
        long open = guild.store().commissions().stream().filter(Commission::open).count();
        row(snap, "commissions", "Guildmaster's Commissions", true, List.of(open + " open",
                "Contracts the Guildmaster pays for from the Treasury. First to finish and claim one wins it.",
                "", "!Click to view"));
        row(snap, "info", "The Guild", true, guildInfoLines());
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
                    commissionLines(player, member, commission, false));
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
        row(snap, "treasury", "Treasury", false, treasuryLines());
        long withdrawable = guild.office().withdrawable();
        row(snap, "withdraw100", "Withdraw " + Text.coins(100), true, withdrawLines(withdrawable));
        row(snap, "withdraw500", "Withdraw " + Text.coins(500), true, withdrawLines(withdrawable));
        row(snap, "withdrawAll", "Withdraw all allowed", true, withdrawLines(withdrawable));
        row(snap, "ledger", "Ledger", true, List.of("Every coin in and out of the Treasury.", "", "!Click to read"));
        row(snap, "members", "Members", true, List.of(guild.store().members().size() + " adventurers", "", "!Click to view"));
        row(snap, "statistics", "Statistics", false, statisticsLines());
        row(snap, "commissions", "Commissions", true, List.of(
                "Posted this week: " + guild.office().commissionsThisWeek() + " / " + guild.config().commissionsPerWeek(),
                "Reward " + guild.config().commissionMinReward() + "-" + guild.config().commissionMaxReward()
                        + " coins, taken from the Treasury.",
                "", "/guild commission <hunt|gather|mine|fish>", "  <target> <amount> <reward> [min rank]", "", "!Click to manage"));
        row(snap, "stores", "Your stores", true, storesLines());
        row(snap, "announcements", "Announcements", false, announcementLines());
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
            row(snap, "member:" + m.id(), m.name(), false, memberLines(m));
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
            row(snap, "ledger:" + i, (e.amount() >= 0 ? "+" : "") + Text.coins(e.amount()), false, ledgerLines(e));
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
                true, showToggleLines(member));
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
        row(snap, "firstDragon", "The First Dragon", false, firstDragonLines());
        row(snap, "mostContracts", "Most contracts", false, leaderboardLines(Member::contractsDone));
        row(snap, "mostReputation", "Most reputation", false, leaderboardLines(Member::reputation));
        row(snap, "longestStreak", "Longest daily streak", false, leaderboardLines(m -> m.stat("best-streak")));
        row(snap, "mostBounties", "Most bounties", false, leaderboardLines(m -> m.stat("bounties")));
        row(snap, "mostCommissions", "Most commissions", false, leaderboardLines(m -> m.stat("commissions-done")));
        row(snap, "mostExplored", "Most places explored", false, leaderboardLines(m -> m.structuresSeen().size()));
        row(snap, "mostTitles", "Most titles", false, leaderboardLines(m -> m.titles().size()));
        backRow(snap, GuildMenus.Screen.HALL_OF_FAME);
        return snap;
    }

    // ---- display text: reconstructs the chest's own lore wording from the same public getters ----

    private List<String> registerLines(Player player) {
        GuildConfig cfg = guild.config();
        boolean atDesk = menus.desk().at(player);
        List<String> lore = new ArrayList<>();
        lore.add("Fee: " + Text.coins(cfg.registrationFee()) + ", once, paid into the Guild Treasury.");
        lore.add("You start as an E-Rank Adventurer with " + cfg.dailyContracts() + " daily contracts and 1 weekly contract.");
        lore.add("");
        lore.add(atDesk ? "!Click to register" : "Register with Lyra at the Guild Hall: " + HallDesk.WARP);
        return lore;
    }

    private List<String> profileLines(Player player, Member member) {
        GuildConfig cfg = guild.config();
        List<String> lore = new ArrayList<>();
        lore.add(member.rank().title());
        if (member.activeTitle() != null && !member.activeTitle().startsWith("rank-")) {
            lore.add("\"" + Titles.name(member.activeTitle()) + "\"");
        }
        lore.add("Reputation: " + member.reputation());
        lore.add("Contracts completed: " + member.contractsDone());
        Rank next = member.rank().next();
        if (next != null) {
            lore.add("");
            lore.add("Toward " + next.name() + "-Rank: " + Math.min(member.reputation(), next.reputation()) + " / "
                    + next.reputation() + " reputation,");
            lore.add("  " + Math.min(member.contractsDone(), next.contracts()) + " / " + next.contracts() + " contracts, then a trial");
        }
        lore.add("");
        lore.add("Earned today: " + member.coinsToday() + " / " + cfg.dailyCoinCap() + " coins, "
                + member.levelsToday() + " / " + cfg.dailyLevelCap() + " levels");
        lore.add("Rerolls left today: " + Math.max(0, member.rank().rerolls() - member.rerollsUsed()));
        lore.add("New dailies at midnight, a new weekly on Monday.");
        lore.add("Contracts are taken and turned in at the Guild Hall's desk: " + HallDesk.WARP);
        lore.add("");
        lore.add("!Click for your titles");
        if (guild.isGuildmaster(player)) {
            lore.add("");
            lore.add("!Founder and Guildmaster");
        }
        return lore;
    }

    private String bountyLabel() {
        EventPools.Bounty bounty = guild.events().bounty();
        return bounty == null ? "Bounty Board" : bounty.title();
    }

    private List<String> bountyLines(Member member) {
        ServerEvents events = guild.events();
        EventPools.Bounty bounty = events.bounty();
        List<String> lore = new ArrayList<>();
        if (bounty == null) {
            lore.add("No bounty posted right now.");
            return lore;
        }
        lore.add("Server-wide bounty, " + bounty.minRank().name() + "-Rank and up");
        lore.add("First to finish and claim it wins.");
        lore.add("");
        if (events.bountyClaimed()) {
            lore.add("Claimed by " + events.bountyClaimedBy() + ". A new one comes soon.");
            return lore;
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
        }
        return lore;
    }

    private String grandLabel() {
        EventPools.GrandQuest quest = guild.events().grandQuest();
        return quest == null ? "Grand Quest" : quest.title();
    }

    private List<String> grandLines(Member member) {
        ServerEvents events = guild.events();
        EventPools.GrandQuest quest = events.grandQuest();
        List<String> lore = new ArrayList<>();
        if (quest == null) {
            lore.add("The next one starts soon.");
            return lore;
        }
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
        return lore;
    }

    private List<String> activityLines(Member member) {
        ServerEvents events = guild.events();
        ServerEvents.ActivityKind kind = events.activity();
        List<String> lore = new ArrayList<>();
        lore.add("Opened by the Guildmaster: " + kind.goal() + ".");
        lore.add("Prize: " + Text.coins(events.activityPrize()) + " from the Treasury");
        lore.add("Your score: " + events.score(member));
        lore.add("Ends in " + Text.duration(events.activityEnds() - guild.clock().nowMillis()));
        return lore;
    }

    private String clipLine(Member member, Reward reward) {
        GuildConfig cfg = guild.config();
        int coinsLeft = Math.max(0, cfg.dailyCoinCap() - member.coinsToday());
        int levelsLeft = Math.max(0, cfg.dailyLevelCap() - member.levelsToday());
        if (reward.coins() <= coinsLeft && reward.levels() <= levelsLeft) {
            return null;
        }
        return "Today's limits leave room for " + Math.min(coinsLeft, reward.coins()) + " coins and "
                + Math.min(levelsLeft, reward.levels()) + " levels";
    }

    private List<String> contractLines(Player player, Member member, ActiveContract active, boolean reroll, boolean atDesk) {
        ContractDef def = active.def();
        List<String> lore = new ArrayList<>();
        if (def == null) {
            lore.add("Unavailable.");
            return lore;
        }
        lore.add(switch (def.type()) {
            case DAILY -> "Daily contract, " + def.rank().name() + "-Rank";
            case WEEKLY -> "Weekly contract, " + def.rank().name() + "-Rank";
            case RARE -> "Rare contract";
            case TRIAL -> "Promotion Trial for " + def.rank().name() + "-Rank";
        });
        lore.add("");
        boolean ready = GuildMenus.objectiveLines(player, active, lore);
        lore.add("");
        lore.add(GuildMenus.rewardLine(active.reward()));
        String clip = clipLine(member, active.reward());
        if (clip != null && !active.claimed()) {
            lore.add(clip);
        }
        lore.add("");
        if (active.claimed()) {
            lore.add("Completed. The Guild thanks you.");
        } else if (ready && !atDesk) {
            lore.add("Ready: turn it in at the Guild Hall, " + HallDesk.WARP);
        } else if (ready) {
            lore.add(def.hasGather() ? "!Click to hand them in and claim" : "!Click to claim your reward");
        } else if (reroll && atDesk) {
            int left = member.rank().rerolls() - member.rerollsUsed();
            lore.add(left > 0 ? "Right-click for a different contract (" + left + " left)" : "No rerolls left today");
        }
        return lore;
    }

    private String trialLabel(Member member) {
        Rank next = member.rank().next();
        if (next == null) return "S-Rank";
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) return trial.def().title();
        if (!Guild.qualifies(member)) return "Next: " + next.title();
        long wait = member.trialRetryAfter() - guild.clock().nowMillis();
        if (wait > 0) return "Promotion Trial: " + next.name() + "-Rank";
        ContractDef def = Trials.forRank(next);
        return "Promotion Trial: " + def.title();
    }

    private List<String> trialLines(Player player, Member member) {
        List<String> lore = new ArrayList<>();
        Rank next = member.rank().next();
        if (next == null) {
            lore.add("You stand at the summit of the Guild.");
            lore.add("Few ever will.");
            return lore;
        }
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            ContractDef def = trial.def();
            lore.add("Promotion Trial for " + next.name() + "-Rank");
            lore.add("");
            boolean ready = GuildMenus.objectiveLines(player, trial, lore);
            lore.add("");
            if (def.noDeath()) {
                lore.add("Dying wipes this trial's progress.");
            }
            long left = member.trialStarted() + Trials.days(next) * Guild.DAY - guild.clock().nowMillis();
            lore.add("Time left: " + Text.duration(left));
            lore.add(GuildMenus.rewardLine(trial.reward()) + ", and the rank");
            lore.add("");
            lore.add(ready ? "!Click to complete the trial" : "Finish every part, then click here");
            return lore;
        }
        if (!Guild.qualifies(member)) {
            lore.add("Reputation: " + member.reputation() + " / " + next.reputation());
            lore.add("Contracts: " + member.contractsDone() + " / " + next.contracts());
            lore.add("");
            lore.add("Then the examiner will set you a");
            lore.add("Promotion Trial. What it asks, nobody says.");
            return lore;
        }
        long wait = member.trialRetryAfter() - guild.clock().nowMillis();
        if (wait > 0) {
            lore.add("The examiner will see you again in " + Text.duration(wait) + ".");
            return lore;
        }
        ContractDef def = Trials.forRank(next);
        lore.add("You have earned the right to attempt it.");
        lore.add("");
        for (Objective o : def.objectives()) {
            lore.add("- " + o.text());
        }
        if (def.noDeath()) {
            lore.add("- Without dying: a death wipes its progress");
        }
        lore.add("");
        lore.add("You get " + Trials.days(next) + " days once you begin.");
        lore.add("");
        lore.add("!Click to begin");
        return lore;
    }

    private List<String> commissionLines(Player player, Member member, Commission commission, boolean featured) {
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
        return lore;
    }

    private List<String> guildInfoLines() {
        long members = guild.store().members().size();
        List<String> lore = new ArrayList<>();
        lore.add("Members: " + members);
        lore.add("Treasury: " + Text.coins(guild.store().treasury().balance()));
        lore.add("");
        lore.add("Ranks: E, D, C, B, A, S.");
        lore.add("Reputation and contracts open a rank's");
        lore.add("Promotion Trial; only passing it promotes.");
        lore.add("Higher ranks draw harder contracts.");
        lore.add("");
        lore.add("Daily limits from Guild contracts:");
        lore.add(Text.coins(guild.config().dailyCoinCap()) + ", " + guild.config().dailyLevelCap() + " levels.");
        lore.add("");
        lore.add("!Click for the Hall of Fame");
        return lore;
    }

    private List<String> treasuryLines() {
        var treasury = guild.store().treasury();
        long withdrawable = guild.office().withdrawable();
        List<String> lore = new ArrayList<>();
        lore.add("Balance: " + Text.coins(treasury.balance()));
        lore.add("Taken in, all time: " + Text.coins(treasury.totalIn()));
        lore.add("Paid out, all time: " + Text.coins(treasury.totalOut()));
        lore.add("");
        lore.add("You may draw " + Text.coins(withdrawable) + " more today");
        lore.add("(" + Text.coins(guild.config().guildmasterDailyWithdrawal()) + " a day).");
        return lore;
    }

    private List<String> withdrawLines(long withdrawable) {
        return withdrawable > 0 ? List.of("!Click to take it") : List.of("Nothing left to draw today");
    }

    private List<String> statisticsLines() {
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
        return lore;
    }

    private List<String> storesLines() {
        int stored = guild.store().stores().stream().mapToInt(ItemStack::getAmount).sum();
        List<String> lore = new ArrayList<>();
        lore.add(stored + " items handed in for your commissions");
        if (stored > 0) {
            lore.add("!Click to collect");
        }
        return lore;
    }

    private List<String> announcementLines() {
        long wait = guild.office().announceWait();
        List<String> lore = new ArrayList<>();
        lore.add("/guild announce <message>");
        lore.add(wait > 0 ? "Next one in " + wait + " min" : "!Ready");
        lore.add("One every " + guild.config().announcementCooldownMinutes() + " minutes.");
        return lore;
    }

    private List<String> memberLines(Member m) {
        List<String> lore = new ArrayList<>();
        lore.add(m.rank().title());
        lore.add("Reputation: " + m.reputation());
        lore.add("Contracts: " + m.contractsDone() + ", commissions: " + m.stat("commissions-done"));
        lore.add("Trials passed: " + m.stat("trials-passed"));
        lore.add("Joined: " + Text.dateTime(m.joinedAt(), guild.config().zone()));
        lore.add("Last active: " + Text.dateTime(m.lastActive(), guild.config().zone()));
        return lore;
    }

    private List<String> ledgerLines(LedgerEntry e) {
        List<String> lore = new ArrayList<>();
        lore.add(e.kind().label());
        lore.add("By: " + e.who());
        lore.add("When: " + Text.dateTime(e.time(), guild.config().zone()));
        lore.add("Balance after: " + Text.coins(e.balanceAfter()));
        return lore;
    }

    private List<String> showToggleLines(Member member) {
        List<String> lore = new ArrayList<>();
        if (member.showTitle()) {
            lore.add("Your worn title shows beside your name in chat and the tab list.");
            lore.add("!Click to hide it");
        } else {
            lore.add("Show your worn title beside your name in chat and the tab list.");
            lore.add("!Click to show it");
        }
        return lore;
    }

    private List<String> firstDragonLines() {
        GuildStore store = guild.store();
        List<String> lore = new ArrayList<>();
        if (!FirstDragon.done(store)) {
            lore.add("Unclaimed. Yet.");
            return lore;
        }
        if (FirstDragon.earlier(store)) {
            lore.add("It fell before the Guild kept records.");
            return lore;
        }
        Map<UUID, String> party = FirstDragon.party(store);
        lore.add("The party that brought it down:");
        lore.addAll(party.isEmpty() ? List.of("no Guild adventurer fought it") : party.values());
        lore.add("on " + Text.dateTime(FirstDragon.time(store), guild.config().zone()));
        return lore;
    }

    private List<String> leaderboardLines(ToIntFunction<Member> score) {
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
        return lore;
    }

    // ---- JSON helpers ----

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

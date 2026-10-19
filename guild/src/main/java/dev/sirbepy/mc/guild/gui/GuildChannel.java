package dev.sirbepy.mc.guild.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.core.gui.JsonSnapshot;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.ServerEvents;
import dev.sirbepy.mc.guild.fame.Titles;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
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

    private final Guild guild;
    private final GuildMenus menus;
    private final GuildSnapshots snapshots;
    private final Predicate<Player> supports;
    private final BiConsumer<Player, byte[]> sender;
    /** The "hello" version each connected client advertised, for a future shape bump; unused today
     * since every client that ever registers {@link #CHANNEL} already understands version 1. */
    private final Map<UUID, Integer> clientVersions = new ConcurrentHashMap<>();

    /** {@code supports} and {@code sender} are the player's channel list and sendPluginMessage; tests pass fakes. */
    GuildChannel(Guild guild, GuildMenus menus, Predicate<Player> supports, BiConsumer<Player, byte[]> sender) {
        this.guild = guild;
        this.menus = menus;
        this.snapshots = new GuildSnapshots(guild, menus, new GuildSnapshotText(guild, menus));
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
        sender.accept(player, snapshots.snapshot(player, screen, page).toString().getBytes(StandardCharsets.UTF_8));
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
        GuildMenus.Screen screen = GuildSnapshots.parseScreen(screenName);
        if (screen == null) return;
        if (row.equals("back")) {
            GuildMenus.Screen target = GuildSnapshots.backTarget(screen);
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

    // ---- JSON helpers ----

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

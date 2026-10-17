package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Reward;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Every Guild screen. Rebuilt on each click (via {@link Menu#adopt}) so progress, caps and the
 * Treasury are always current. The Registration and Main board screens, and the pieces shared
 * across screens (contract icons, claiming), live here; the Office, Commissions and Trial
 * screens live in {@link OfficeMenus}, {@link CommissionMenus} and {@link TrialMenus}. Every screen
 * can be looked at anywhere; registering, claiming, rerolling and trials need the {@link HallDesk}.
 */
public final class GuildMenus {

    public enum Screen { MAIN, COMMISSIONS, OFFICE, MEMBERS, LEDGER, TITLES, HALL_OF_FAME }

    /** Navigates to a screen at a given page, for the Members/Ledger pager (plain {@link BiConsumer} always means page 0). */
    @FunctionalInterface
    interface PageNav {
        void go(Menu shown, Screen screen, int page);
    }

    static final int[] DAILY_SLOTS = {19, 20, 21, 22, 23};
    static final int WEEKLY_SLOT = 25;
    static final int PROFILE_SLOT = 4;
    static final int FEATURED_SLOT = 31;
    static final int TRIAL_SLOT = 38;
    static final int COMMISSIONS_SLOT = 40;
    static final int INFO_SLOT = 42;
    static final int OFFICE_SLOT = 49;
    static final int REGISTER_SLOT = 22;
    static final int BACK_SLOT = 49;

    private final Guild guild;
    private final FameMenus fame;
    private final OfficeMenus office;
    private final CommissionMenus commissionMenus;
    private final TrialMenus trialMenus;
    private final HallDesk desk = new HallDesk();
    /** Set once by {@code GuildPlugin} after the channel registers; null means no mod screen exists
     * yet, so every player gets the chest (safe during startup, before the channel is wired). */
    private GuildChannel channel;

    public GuildMenus(Guild guild) {
        this.guild = guild;
        this.fame = new FameMenus(guild);
        this.office = new OfficeMenus(guild);
        this.commissionMenus = new CommissionMenus(guild, desk);
        this.trialMenus = new TrialMenus(guild);
    }

    public HallDesk desk() {
        return desk;
    }

    /** Lets {@link #open} and {@link #openHallOfFame} hand a modded player the {@code guild:ui} screen
     * instead of the chest, same branch {@code classes:tree}'s TreeMenu takes for a tree-mod player. */
    public void setChannel(GuildChannel channel) {
        this.channel = channel;
    }

    public void open(Player player, Screen screen) {
        if (channel != null && channel.supports(player)) {
            channel.send(player, screen);
            return;
        }
        player.openInventory(build(player, screen, 0).getInventory());
    }

    Menu build(Player player, Screen screen, int page) {
        Member member = guild.member(player);
        if (member == null && screen != Screen.OFFICE) {
            return registration(player);
        }
        return switch (screen) {
            case MAIN -> main(player, member);
            case COMMISSIONS -> commissionMenus.commissions(player, member, (shown, s) -> refresh(player, shown, s, 0));
            case OFFICE -> guild.isGuildmaster(player)
                    ? office.office(player, (shown, s) -> refresh(player, shown, s, 0))
                    : main(player, member);
            case MEMBERS -> office.members(page, (shown, s, p) -> refresh(player, shown, s, p));
            case LEDGER -> office.ledger(page, (shown, s, p) -> refresh(player, shown, s, p));
            case TITLES -> titlesMenu(player, member);
            case HALL_OF_FAME -> hallOfFame(player);
        };
    }

    private void refresh(Player player, Menu shown, Screen screen, int page) {
        shown.adopt(build(player, screen, page));
    }

    private Menu titlesMenu(Player player, Member member) {
        return fame.titles(player, member, (shown, screen) -> refresh(player, shown, screen, 0));
    }

    /** Opens the Hall of Fame directly, for the archivist. */
    public void openHallOfFame(Player player) {
        if (channel != null && channel.supports(player)) {
            channel.send(player, Screen.HALL_OF_FAME);
            return;
        }
        player.openInventory(hallOfFame(player).getInventory());
    }

    private Menu hallOfFame(Player player) {
        return fame.hallOfFame(player, (shown, screen) -> refresh(player, shown, screen, 0));
    }

    // ---- Registration ----

    private Menu registration(Player player) {
        GuildConfig cfg = guild.config();
        boolean atDesk = desk.at(player);
        Menu menu = new Menu(6, Component.text("The Adventurer's Guild", Chat.BRONZE));
        menu.set(PROFILE_SLOT, Icons.icon(Material.WRITABLE_BOOK, "Welcome, traveller", Chat.BRONZE,
                "The Adventurer's Guild takes contracts from",
                "every corner of this world: hunts, deliveries,",
                "expeditions, bosses, and stranger things.",
                "",
                "Members start at E-Rank and climb to S",
                "through reputation, contracts and trials."));
        menu.set(REGISTER_SLOT, Icons.glint(Icons.icon(Material.NAME_TAG, "Register as an Adventurer", NamedTextColor.GOLD,
                "Fee: " + Text.coins(cfg.registrationFee()) + ", once, paid into the",
                "Guild Treasury.",
                "",
                "You start as an E-Rank Adventurer with",
                cfg.dailyContracts() + " daily contracts and 1 weekly contract.",
                "",
                atDesk ? "!Click to register" : "Register with Lyra at the Guild Hall:",
                atDesk ? "" : HallDesk.WARP)), (shown, click) -> {
            if (!desk.at(player)) {
                Chat.send(player, "Registration is at the Guild Hall's front desk: talk to Lyra there. "
                        + HallDesk.WARP, NamedTextColor.YELLOW);
                return;
            }
            switch (guild.register(player)) {
                case REGISTERED -> {
                    Chat.send(player, "Welcome to the Adventurer's Guild, " + player.getName()
                            + ". You are an E-Rank Adventurer. Your first contracts are on the board.", NamedTextColor.GOLD);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                    refresh(player, shown, Screen.MAIN, 0);
                }
                case TOO_POOR -> Chat.send(player, "Registration costs " + Text.coins(cfg.registrationFee())
                        + ". You have " + Text.coins((long) guild.bank().balance(player)) + ".", NamedTextColor.RED);
                case NO_BANK -> Chat.send(player, "The Guild's bank is closed right now. Try again later.", NamedTextColor.RED);
                case ALREADY_MEMBER -> refresh(player, shown, Screen.MAIN, 0);
            }
        });
        menu.frame(Material.BROWN_STAINED_GLASS_PANE);
        return menu;
    }

    // ---- Main board ----

    private Menu main(Player player, Member member) {
        boolean atDesk = desk.at(player);
        Menu menu = new Menu(6, Component.text("Adventurer's Guild: " + member.rank().title(), Chat.BRONZE));
        menu.set(PROFILE_SLOT, profile(player, member), (shown, click) -> refresh(player, shown, Screen.TITLES, 0));
        menu.set(FameMenus.BOUNTY_SLOT, fame.bountyIcon(member), (shown, click) -> {
            desk.require(player, () -> fame.claimBounty(player));
            refresh(player, shown, Screen.MAIN, 0);
        });
        menu.set(FameMenus.GRAND_SLOT, fame.grandIcon(member), (shown, click) -> {
            desk.require(player, () -> fame.claimGrand(player));
            refresh(player, shown, Screen.MAIN, 0);
        });
        ItemStack activity = fame.activityIcon(member);
        if (activity != null) {
            menu.set(FameMenus.ACTIVITY_SLOT, activity);
        }
        for (int i = 0; i < member.dailies().size() && i < DAILY_SLOTS.length; i++) {
            ActiveContract daily = member.dailies().get(i);
            int slot = i;
            menu.set(DAILY_SLOTS[i], contractIcon(player, member, daily, slot, atDesk), (shown, click) -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    desk.require(player, () -> reroll(player, member, slot));
                } else {
                    desk.require(player, () -> claim(player, guild, daily));
                }
                refresh(player, shown, Screen.MAIN, 0);
            });
        }
        if (member.weekly() != null) {
            ActiveContract weekly = member.weekly();
            menu.set(WEEKLY_SLOT, contractIcon(player, member, weekly, -1, atDesk), (shown, click) -> {
                desk.require(player, () -> claim(player, guild, weekly));
                refresh(player, shown, Screen.MAIN, 0);
            });
        }
        Commission featured = guild.office().featured();
        if (featured != null) {
            menu.set(FEATURED_SLOT, Icons.glint(commissionMenus.commissionIcon(player, member, featured, true)), (shown, click) -> {
                desk.require(player, () -> commissionMenus.claimCommission(player, featured));
                refresh(player, shown, Screen.MAIN, 0);
            });
        }
        menu.set(TRIAL_SLOT, trialMenus.trialIcon(player, member), (shown, click) -> {
            desk.require(player, () -> trialMenus.trialClick(player, member));
            refresh(player, shown, Screen.MAIN, 0);
        });
        long open = guild.store().commissions().stream().filter(Commission::open).count();
        menu.set(COMMISSIONS_SLOT, Icons.icon(Material.BELL, "Guildmaster's Commissions", Chat.BRONZE,
                open + " open",
                "Contracts the Guildmaster pays for from the",
                "Treasury. First to finish and claim one wins it.",
                "",
                "!Click to view"), (shown, click) -> refresh(player, shown, Screen.COMMISSIONS, 0));
        menu.set(INFO_SLOT, info(), (shown, click) -> refresh(player, shown, Screen.HALL_OF_FAME, 0));
        if (guild.isGuildmaster(player)) {
            menu.set(OFFICE_SLOT, Icons.glint(Icons.icon(Material.LECTERN, "Guildmaster's Office", NamedTextColor.GOLD,
                    "Treasury, members, ledger, statistics,",
                    "commissions and your stores.",
                    "",
                    "!Click to enter")), (shown, click) -> refresh(player, shown, Screen.OFFICE, 0));
        }
        menu.frame(Material.BROWN_STAINED_GLASS_PANE);
        return menu;
    }

    private ItemStack profile(Player player, Member member) {
        GuildConfig cfg = guild.config();
        List<String> lore = new ArrayList<>();
        lore.add(member.rank().title());
        if (member.activeTitle() != null && !member.activeTitle().startsWith("rank-")) {
            lore.add("\"" + dev.sirbepy.mc.guild.fame.Titles.name(member.activeTitle()) + "\"");
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
        lore.add("Contracts are taken and turned in at the");
        lore.add("Guild Hall's desk: " + HallDesk.WARP);
        lore.add("");
        lore.add("!Click for your titles");
        if (guild.isGuildmaster(player)) {
            lore.add("");
            lore.add("!Founder and Guildmaster");
        }
        return Icons.icon(Icons.head(player), player.getName(), member.rank().color(), lore);
    }

    private ItemStack info() {
        long members = guild.store().members().size();
        return Icons.icon(Material.BOOK, "The Guild", Chat.BRONZE,
                "Members: " + members,
                "Treasury: " + Text.coins(guild.store().treasury().balance()),
                "",
                "Ranks: E, D, C, B, A, S.",
                "Reputation and contracts open a rank's",
                "Promotion Trial; only passing it promotes.",
                "Higher ranks draw harder contracts.",
                "",
                "Daily limits from Guild contracts:",
                Text.coins(guild.config().dailyCoinCap()) + ", " + guild.config().dailyLevelCap() + " levels.",
                "",
                "!Click for the Hall of Fame");
    }

    private ItemStack contractIcon(Player player, Member member, ActiveContract active, int dailySlot, boolean atDesk) {
        ContractDef def = active.def();
        if (def == null) {
            return new ItemStack(Material.BARRIER);
        }
        List<String> lore = new ArrayList<>();
        lore.add(switch (def.type()) {
            case DAILY -> "Daily contract, " + def.rank().name() + "-Rank";
            case WEEKLY -> "Weekly contract, " + def.rank().name() + "-Rank";
            case RARE -> "Rare contract";
            case TRIAL -> "Promotion Trial for " + def.rank().name() + "-Rank";
        });
        lore.add("");
        boolean ready = objectiveLines(player, active, lore);
        lore.add("");
        lore.add(rewardLine(active.reward()));
        String clip = clipLine(member, active.reward());
        if (clip != null && !active.claimed()) {
            lore.add(clip);
        }
        lore.add("");
        TextColor color = def.type() == ContractDef.Type.RARE ? NamedTextColor.GOLD : Chat.PARCHMENT;
        if (active.claimed()) {
            lore.add("Completed. The Guild thanks you.");
            color = NamedTextColor.DARK_GRAY;
        } else if (ready && !atDesk) {
            lore.add("!Ready: turn it in at the Guild Hall, " + HallDesk.WARP);
        } else if (ready) {
            lore.add(def.hasGather() ? "!Click to hand them in and claim" : "!Click to claim your reward");
        } else if (dailySlot >= 0 && atDesk) {
            int left = member.rank().rerolls() - member.rerollsUsed();
            lore.add(left > 0 ? "Right-click for a different contract (" + left + " left)" : "No rerolls left today");
        }
        Material iconMaterial = active.claimed() ? Material.PAPER : def.icon();
        ItemStack icon = Icons.icon(iconMaterial, def.title(), color, lore);
        return ready && !active.claimed() ? Icons.glint(icon) : icon;
    }

    /** Adds one line per objective; true when every objective is met (gather ones from the inventory). */
    static boolean objectiveLines(Player player, ActiveContract active, List<String> lore) {
        ContractDef def = active.def();
        boolean ready = true;
        boolean multi = def.objectives().size() > 1 || def.type() == ContractDef.Type.TRIAL;
        for (int i = 0; i < def.objectives().size(); i++) {
            Objective o = def.objectives().get(i);
            int value = o.kind() == Objective.Kind.GATHER ? Guild.carried(player, o) : active.progress(i);
            boolean done = value >= o.amount();
            ready &= done;
            String progress = o.isVisit() ? (done ? "found" : "not yet") : value + " / " + o.amount();
            String label = multi ? o.text() + ": " : (o.kind() == Objective.Kind.GATHER ? "Carried: " : "Progress: ");
            lore.add((done ? "!" : "") + (multi ? "- " : "") + label + progress);
        }
        return ready;
    }

    static String rewardLine(Reward reward) {
        List<String> parts = new ArrayList<>();
        if (reward.coins() > 0) {
            parts.add(Text.coins(reward.coins()));
        }
        if (reward.reputation() > 0) {
            parts.add("+" + reward.reputation() + " reputation");
        }
        if (reward.levels() > 0) {
            parts.add("+" + reward.levels() + (reward.levels() == 1 ? " level" : " levels"));
        }
        if (reward.bundle()) {
            parts.add("a supply bundle");
        }
        return "Reward: " + String.join(", ", parts);
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

    /** Shared by the main board's contract claims and the Trial screen's claim. */
    static void claim(Player player, Guild guild, ActiveContract active) {
        Guild.Claim claim = guild.claim(player, active);
        switch (claim.result()) {
            case CLAIMED, PROMOTED -> {
                List<String> parts = new ArrayList<>();
                if (claim.coins() > 0) {
                    parts.add(Text.coins(claim.coins()));
                }
                if (claim.reputation() > 0) {
                    parts.add(claim.reputation() + " reputation");
                }
                if (claim.levels() > 0) {
                    parts.add(claim.levels() + " levels");
                }
                if (!claim.items().isEmpty()) {
                    parts.add("a supply bundle");
                }
                Chat.send(player, "Contract complete: " + active.def().title()
                        + (parts.isEmpty() ? "." : ". Paid: " + String.join(", ", parts) + ".")
                        + (claim.clipped() ? " (Today's limits held back the rest.)" : ""), NamedTextColor.GREEN);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            }
            case NOT_DONE -> Chat.send(player, "That contract isn't finished yet.", NamedTextColor.YELLOW);
            case MISSING_ITEMS -> Chat.send(player, "You aren't carrying everything this contract asks for.", NamedTextColor.YELLOW);
            case ALREADY_CLAIMED -> Chat.send(player, "Already done. Fresh contracts come at midnight.", NamedTextColor.GRAY);
            case NO_BANK -> Chat.send(player, "The Guild's bank is closed right now. Your contract stays ready.", NamedTextColor.RED);
            case NOT_ELIGIBLE, CLOSED -> { }
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

    /** Shared "back to the board" button (slot 45) used by the Commissions and Office screens. */
    static void back(Menu menu, BiConsumer<Menu, Screen> go) {
        menu.set(45, Icons.icon(Material.OAK_DOOR, "Back to the board", Chat.PARCHMENT),
                (shown, click) -> go.accept(shown, Screen.MAIN));
    }
}

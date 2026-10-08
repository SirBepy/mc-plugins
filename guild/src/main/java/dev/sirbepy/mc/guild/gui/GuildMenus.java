package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Reward;
import dev.sirbepy.mc.guild.contract.Trials;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every Guild screen. Rebuilt on each click (via {@link Menu#adopt}) so progress, caps and the
 * Treasury are always current.
 */
public final class GuildMenus {

    public enum Screen { MAIN, COMMISSIONS, OFFICE, MEMBERS, LEDGER, TITLES, HALL_OF_FAME }

    static final int[] DAILY_SLOTS = {19, 21, 23};
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

    public GuildMenus(Guild guild) {
        this.guild = guild;
        this.fame = new FameMenus(guild);
    }

    public void open(Player player, Screen screen) {
        player.openInventory(build(player, screen, 0).getInventory());
    }

    Menu build(Player player, Screen screen, int page) {
        Member member = guild.member(player);
        if (member == null && screen != Screen.OFFICE) {
            return registration(player);
        }
        return switch (screen) {
            case MAIN -> main(player, member);
            case COMMISSIONS -> commissions(player, member);
            case OFFICE -> guild.isGuildmaster(player) ? office(player) : main(player, member);
            case MEMBERS -> members(player, page);
            case LEDGER -> ledger(player, page);
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
        player.openInventory(hallOfFame(player).getInventory());
    }

    private Menu hallOfFame(Player player) {
        return fame.hallOfFame(player, (shown, screen) -> refresh(player, shown, screen, 0));
    }

    // ---- Registration ----

    private Menu registration(Player player) {
        GuildConfig cfg = guild.config();
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
                "3 daily contracts and 1 weekly contract.",
                "",
                "!Click to register")), (shown, click) -> {
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
        Menu menu = new Menu(6, Component.text("Adventurer's Guild: " + member.rank().title(), Chat.BRONZE));
        menu.set(PROFILE_SLOT, profile(player, member), (shown, click) -> refresh(player, shown, Screen.TITLES, 0));
        menu.set(FameMenus.BOUNTY_SLOT, fame.bountyIcon(member), (shown, click) -> {
            fame.claimBounty(player);
            refresh(player, shown, Screen.MAIN, 0);
        });
        menu.set(FameMenus.GRAND_SLOT, fame.grandIcon(member), (shown, click) -> {
            fame.claimGrand(player);
            refresh(player, shown, Screen.MAIN, 0);
        });
        ItemStack activity = fame.activityIcon(member);
        if (activity != null) {
            menu.set(FameMenus.ACTIVITY_SLOT, activity);
        }
        for (int i = 0; i < member.dailies().size() && i < DAILY_SLOTS.length; i++) {
            ActiveContract daily = member.dailies().get(i);
            int slot = i;
            menu.set(DAILY_SLOTS[i], contractIcon(player, member, daily, slot), (shown, click) -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    reroll(player, member, slot);
                } else {
                    claim(player, daily);
                }
                refresh(player, shown, Screen.MAIN, 0);
            });
        }
        if (member.weekly() != null) {
            ActiveContract weekly = member.weekly();
            menu.set(WEEKLY_SLOT, contractIcon(player, member, weekly, -1), (shown, click) -> {
                claim(player, weekly);
                refresh(player, shown, Screen.MAIN, 0);
            });
        }
        Commission featured = guild.office().featured();
        if (featured != null) {
            menu.set(FEATURED_SLOT, Icons.glint(commissionIcon(player, member, featured, true)), (shown, click) -> {
                claimCommission(player, featured);
                refresh(player, shown, Screen.MAIN, 0);
            });
        }
        menu.set(TRIAL_SLOT, trialIcon(player, member), (shown, click) -> {
            trialClick(player, member);
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

    private ItemStack contractIcon(Player player, Member member, ActiveContract active, int dailySlot) {
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
        } else if (ready) {
            lore.add(def.hasGather() ? "!Click to hand them in and claim" : "!Click to claim your reward");
        } else if (dailySlot >= 0) {
            int left = member.rank().rerolls() - member.rerollsUsed();
            lore.add(left > 0 ? "Right-click for a different contract (" + left + " left)" : "No rerolls left today");
        }
        Material iconMaterial = active.claimed() ? Material.PAPER : def.icon();
        ItemStack icon = Icons.icon(iconMaterial, def.title(), color, lore);
        return ready && !active.claimed() ? Icons.glint(icon) : icon;
    }

    /** Adds one line per objective; true when every objective is met (gather ones from the inventory). */
    private boolean objectiveLines(Player player, ActiveContract active, List<String> lore) {
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

    private void claim(Player player, ActiveContract active) {
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

    // ---- Trials ----

    private ItemStack trialIcon(Player player, Member member) {
        Rank next = member.rank().next();
        if (next == null) {
            return Icons.glint(Icons.icon(Material.NETHER_STAR, "S-Rank", NamedTextColor.GOLD,
                    "You stand at the summit of the Guild.", "Few ever will."));
        }
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            ContractDef def = trial.def();
            List<String> lore = new ArrayList<>();
            lore.add("Promotion Trial for " + next.name() + "-Rank");
            lore.add("");
            boolean ready = objectiveLines(player, trial, lore);
            lore.add("");
            if (def.noDeath()) {
                lore.add("Dying wipes this trial's progress.");
            }
            long left = member.trialStarted() + Trials.days(next) * Guild.DAY - guild.clock().nowMillis();
            lore.add("Time left: " + Text.duration(left));
            lore.add(rewardLine(trial.reward()) + ", and the rank");
            lore.add("");
            lore.add(ready ? "!Click to complete the trial" : "Finish every part, then click here");
            ItemStack icon = Icons.icon(def.icon(), def.title(), next.color(), lore);
            return ready ? Icons.glint(icon) : icon;
        }
        if (!Guild.qualifies(member)) {
            return Icons.icon(Material.IRON_BARS, "Next: " + next.title(), NamedTextColor.GRAY,
                    "Reputation: " + member.reputation() + " / " + next.reputation(),
                    "Contracts: " + member.contractsDone() + " / " + next.contracts(),
                    "",
                    "Then the examiner will set you a",
                    "Promotion Trial. What it asks, nobody says.");
        }
        long wait = member.trialRetryAfter() - guild.clock().nowMillis();
        if (wait > 0) {
            return Icons.icon(Material.CLOCK, "Promotion Trial: " + next.name() + "-Rank", NamedTextColor.GRAY,
                    "The examiner will see you again in " + Text.duration(wait) + ".");
        }
        ContractDef def = Trials.forRank(next);
        List<String> lore = new ArrayList<>();
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
        return Icons.glint(Icons.icon(def.icon(), "Promotion Trial: " + def.title(), next.color(), lore));
    }

    private void trialClick(Player player, Member member) {
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            claim(player, trial);
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

    // ---- Commissions ----

    private Menu commissions(Player player, Member member) {
        Menu menu = new Menu(6, Component.text("Guildmaster's Commissions", Chat.BRONZE));
        boolean gm = guild.isGuildmaster(player);
        menu.set(PROFILE_SLOT, Icons.icon(Material.BELL, "Guildmaster's Commissions", Chat.BRONZE,
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
                    claimCommission(player, commission);
                }
                refresh(player, shown, Screen.COMMISSIONS, 0);
            });
            slot += slot % 9 == 7 ? 3 : 1;
        }
        if (open.isEmpty()) {
            menu.set(22, Icons.icon(Material.PAPER, "No open commissions", NamedTextColor.GRAY,
                    gm ? "Post one: /guild commission <hunt|gather|mine|fish> <target> <amount> <reward> [min rank]"
                            : "The Guildmaster hasn't posted any right now."));
        }
        back(menu, player);
        menu.frame(Material.BROWN_STAINED_GLASS_PANE);
        return menu;
    }

    private ItemStack commissionIcon(Player player, Member member, Commission commission, boolean featured) {
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

    // ---- The Guildmaster's office ----

    private Menu office(Player player) {
        Menu menu = new Menu(6, Component.text("Guildmaster's Office", NamedTextColor.GOLD));
        var treasury = guild.store().treasury();
        long withdrawable = guild.office().withdrawable();
        menu.set(PROFILE_SLOT, Icons.icon(Icons.head(player), "Guildmaster " + player.getName(), NamedTextColor.GOLD,
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
                        withdraw(player, amount);
                        refresh(player, shown, Screen.OFFICE, 0);
                    });
        }
        menu.set(22, Icons.icon(Material.GOLD_INGOT, "Withdraw all allowed", NamedTextColor.YELLOW,
                "That is " + Text.coins(withdrawable) + " now.",
                withdrawable > 0 ? "!Click to take it" : "Nothing left to draw today"),
                (shown, click) -> {
                    withdraw(player, withdrawable);
                    refresh(player, shown, Screen.OFFICE, 0);
                });
        menu.set(24, Icons.icon(Material.WRITTEN_BOOK, "Ledger", Chat.BRONZE,
                "Every coin in and out of the Treasury.", "", "!Click to read"),
                (shown, click) -> refresh(player, shown, Screen.LEDGER, 0));
        menu.set(25, Icons.icon(Material.PLAYER_HEAD, "Members", Chat.BRONZE,
                guild.store().members().size() + " adventurers", "", "!Click to view"),
                (shown, click) -> refresh(player, shown, Screen.MEMBERS, 0));
        menu.set(29, statistics());
        menu.set(31, Icons.icon(Material.BELL, "Commissions", Chat.BRONZE,
                "Posted this week: " + guild.office().commissionsThisWeek() + " / " + guild.config().commissionsPerWeek(),
                "Reward " + guild.config().commissionMinReward() + "-" + guild.config().commissionMaxReward()
                        + " coins, taken from the Treasury.",
                "",
                "/guild commission <hunt|gather|mine|fish>",
                "  <target> <amount> <reward> [min rank]",
                "",
                "!Click to manage"), (shown, click) -> refresh(player, shown, Screen.COMMISSIONS, 0));
        int stored = guild.store().stores().stream().mapToInt(ItemStack::getAmount).sum();
        menu.set(33, Icons.icon(Material.CHEST, "Your stores", Chat.BRONZE,
                stored + " items handed in for your commissions",
                stored > 0 ? "!Click to collect" : ""), (shown, click) -> {
            int taken = guild.office().collectStores(player);
            if (taken > 0) {
                Chat.send(player, "You collect " + taken + " items from the Guild stores.");
            }
            refresh(player, shown, Screen.OFFICE, 0);
        });
        long wait = guild.office().announceWait();
        menu.set(40, Icons.icon(Material.GOAT_HORN, "Announcements", Chat.BRONZE,
                "/guild announce <message>",
                wait > 0 ? "Next one in " + wait + " min" : "!Ready",
                "One every " + guild.config().announcementCooldownMinutes() + " minutes."));
        back(menu, player);
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE);
        return menu;
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

    private Menu members(Player player, int page) {
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
        pager(menu, player, Screen.MEMBERS, page, sorted.size());
        return menu;
    }

    private Menu ledger(Player player, int page) {
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
        pager(menu, player, Screen.LEDGER, page, entries.size());
        return menu;
    }

    private void pager(Menu menu, Player player, Screen screen, int page, int total) {
        if (page > 0) {
            menu.set(45, Icons.icon(Material.ARROW, "Previous page", Chat.PARCHMENT),
                    (shown, click) -> refresh(player, shown, screen, page - 1));
        }
        if ((page + 1) * 45 < total) {
            menu.set(53, Icons.icon(Material.ARROW, "Next page", Chat.PARCHMENT),
                    (shown, click) -> refresh(player, shown, screen, page + 1));
        }
        menu.set(BACK_SLOT, Icons.icon(Material.OAK_DOOR, "Back to the office", Chat.PARCHMENT),
                (shown, click) -> refresh(player, shown, Screen.OFFICE, 0));
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE);
    }

    private void back(Menu menu, Player player) {
        menu.set(45, Icons.icon(Material.OAK_DOOR, "Back to the board", Chat.PARCHMENT),
                (shown, click) -> refresh(player, shown, Screen.MAIN, 0));
    }
}

package dev.sirbepy.mc.guild.gui;

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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * {@link GuildChannel}'s display text: one method per row, reconstructing the same wording the
 * chest menus show from the same public getters, so a {@code guild:ui} row and a chest item's lore
 * never drift from each other (they just aren't shared verbatim; see {@link GuildChannel}'s own
 * javadoc for why). Pulled out of {@code GuildChannel} itself in {@code 189-split-guild-channel.md}
 * once that file grew past 1000 lines; this split is purely mechanical, no behavior change.
 */
final class GuildSnapshotText {

    private final Guild guild;
    private final GuildMenus menus;

    GuildSnapshotText(Guild guild, GuildMenus menus) {
        this.guild = guild;
        this.menus = menus;
    }

    List<String> registerLines(Player player) {
        GuildConfig cfg = guild.config();
        boolean atDesk = menus.desk().at(player);
        List<String> lore = new ArrayList<>();
        lore.add("Fee: " + Text.coins(cfg.registrationFee()) + ", once, paid into the Guild Treasury.");
        lore.add("You start as an E-Rank Adventurer with " + cfg.dailyContracts() + " daily contracts and 1 weekly contract.");
        lore.add("");
        lore.add(atDesk ? "!Click to register" : "Register with Lyra at the Guild Hall: " + HallDesk.WARP);
        return lore;
    }

    List<String> profileLines(Player player, Member member) {
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

    String bountyLabel() {
        EventPools.Bounty bounty = guild.events().bounty();
        return bounty == null ? "Bounty Board" : bounty.title();
    }

    List<String> bountyLines(Member member) {
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

    String grandLabel() {
        EventPools.GrandQuest quest = guild.events().grandQuest();
        return quest == null ? "Grand Quest" : quest.title();
    }

    List<String> grandLines(Member member) {
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

    List<String> activityLines(Member member) {
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

    List<String> contractLines(Player player, Member member, ActiveContract active, boolean reroll, boolean atDesk) {
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

    String trialLabel(Member member) {
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

    List<String> trialLines(Player player, Member member) {
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

    List<String> commissionLines(Player player, Member member, Commission commission, boolean featured) {
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

    List<String> guildInfoLines() {
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

    List<String> treasuryLines() {
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

    List<String> withdrawLines(long withdrawable) {
        return withdrawable > 0 ? List.of("!Click to take it") : List.of("Nothing left to draw today");
    }

    List<String> statisticsLines() {
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

    List<String> storesLines() {
        int stored = guild.store().stores().stream().mapToInt(ItemStack::getAmount).sum();
        List<String> lore = new ArrayList<>();
        lore.add(stored + " items handed in for your commissions");
        if (stored > 0) {
            lore.add("!Click to collect");
        }
        return lore;
    }

    List<String> announcementLines() {
        long wait = guild.office().announceWait();
        List<String> lore = new ArrayList<>();
        lore.add("/guild announce <message>");
        lore.add(wait > 0 ? "Next one in " + wait + " min" : "!Ready");
        lore.add("One every " + guild.config().announcementCooldownMinutes() + " minutes.");
        return lore;
    }

    List<String> memberLines(Member m) {
        List<String> lore = new ArrayList<>();
        lore.add(m.rank().title());
        lore.add("Reputation: " + m.reputation());
        lore.add("Contracts: " + m.contractsDone() + ", commissions: " + m.stat("commissions-done"));
        lore.add("Trials passed: " + m.stat("trials-passed"));
        lore.add("Joined: " + Text.dateTime(m.joinedAt(), guild.config().zone()));
        lore.add("Last active: " + Text.dateTime(m.lastActive(), guild.config().zone()));
        return lore;
    }

    List<String> ledgerLines(LedgerEntry e) {
        List<String> lore = new ArrayList<>();
        lore.add(e.kind().label());
        lore.add("By: " + e.who());
        lore.add("When: " + Text.dateTime(e.time(), guild.config().zone()));
        lore.add("Balance after: " + Text.coins(e.balanceAfter()));
        return lore;
    }

    List<String> showToggleLines(Member member) {
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

    List<String> firstDragonLines() {
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

    List<String> leaderboardLines(ToIntFunction<Member> score) {
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
}

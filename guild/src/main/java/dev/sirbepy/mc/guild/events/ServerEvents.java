package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.EventPools.Bounty;
import dev.sirbepy.mc.guild.events.EventPools.GrandQuest;
import dev.sirbepy.mc.guild.gui.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Guild's server-wide events: one bounty at a time (C-Rank and up, first to claim wins, a new one
 * every 3 days or 12 h after it's claimed), a weekly Grand Quest everyone pushes together (contributors
 * over a threshold share the reward, the top one earns a title), and the Guildmaster's short contests
 * (one a day, prize from the Treasury). State lives in guild.yml's "events" section.
 */
public final class ServerEvents {

    public static final long BOUNTY_DAYS = 3;
    public static final long BOUNTY_REST_HOURS = 12;
    public static final int GRAND_COINS = 150;
    public static final int GRAND_REPUTATION = 30;
    public static final int GRAND_LEVELS = 3;
    public static final int ACTIVITY_MIN_PRIZE = 50;
    public static final int ACTIVITY_MAX_PRIZE = 500;

    public enum ActivityKind {
        HUNT("Hunting Season", "defeat the most hostile mobs"),
        FISH("Fishing Derby", "catch the most while fishing"),
        MINE("Ore Rush Hour", "mine the most ore");

        private final String title;
        private final String goal;

        ActivityKind(String title, String goal) {
            this.title = title;
            this.goal = goal;
        }

        public String title() {
            return title;
        }

        public String goal() {
            return goal;
        }
    }

    public enum StartResult { STARTED, NOT_GUILDMASTER, ALREADY_TODAY, RUNNING, BAD_PRIZE, TREASURY_SHORT }

    private final Guild guild;
    private final Random random;

    public ServerEvents(Guild guild, Random random) {
        this.guild = guild;
        this.random = random;
    }

    private YamlConfiguration state() {
        return guild.store().events();
    }

    private long now() {
        return guild.clock().nowMillis();
    }

    // ---- the clock ----

    /** Rotates the bounty and Grand Quest and ends a finished activity; run every few seconds. */
    public void tick() {
        rotateBounty();
        rotateGrandQuest();
        tickActivity();
    }

    // ---- bounty ----

    public Bounty bounty() {
        return EventPools.bounty(state().getString("bounty.id"));
    }

    public boolean bountyClaimed() {
        return state().getString("bounty.claimed-name") != null;
    }

    public String bountyClaimedBy() {
        return state().getString("bounty.claimed-name");
    }

    public long bountyEnds() {
        return state().getLong("bounty.posted") + BOUNTY_DAYS * Guild.DAY;
    }

    public int bountyProgress(Member member) {
        return state().getInt("bounty.progress." + member.id());
    }

    void rotateBounty() {
        Bounty current = bounty();
        boolean expired = current != null && !bountyClaimed() && now() > bountyEnds();
        boolean rested = current != null && bountyClaimed()
                && now() > state().getLong("bounty.claimed-at") + BOUNTY_REST_HOURS * Guild.HOUR;
        if (current != null && !expired && !rested) {
            return;
        }
        String last = state().getString("bounty.id");
        List<Bounty> options = EventPools.BOUNTIES.stream().filter(b -> !b.id().equals(last)).toList();
        Bounty next = options.get(random.nextInt(options.size()));
        state().set("bounty", null);
        state().set("bounty.id", next.id());
        state().set("bounty.posted", now());
        guild.store().markDirty();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Member member = guild.store().member(online.getUniqueId());
            if (member != null && member.rank().atLeast(next.minRank())) {
                Chat.send(online, "A new bounty is posted: " + next.title() + ", " + Text.coins(next.coins())
                        + ". First to claim it wins. See /guild.", NamedTextColor.GOLD);
            }
        }
    }

    public boolean bountyEligible(Member member) {
        Bounty bounty = bounty();
        return bounty != null && member.rank().atLeast(bounty.minRank()) && !guild.config().isGuildmaster(member.id());
    }

    public Guild.Claim claimBounty(Player player) {
        Member member = guild.member(player);
        Bounty bounty = bounty();
        if (member == null || bounty == null || !bountyEligible(member)) {
            return claim(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (bountyClaimed()) {
            return claim(Guild.ClaimResult.CLOSED);
        }
        if (bountyProgress(member) < bounty.objective().amount()) {
            return claim(Guild.ClaimResult.NOT_DONE);
        }
        if (!guild.bank().available()) {
            return claim(Guild.ClaimResult.NO_BANK);
        }
        int[] paid = guild.mint(player, member, bounty.coins(), 2);
        state().set("bounty.claimed-by", player.getUniqueId().toString());
        state().set("bounty.claimed-name", player.getName());
        state().set("bounty.claimed-at", now());
        member.addStat("bounties", 1);
        member.addStat("coins-earned", paid[0]);
        guild.addReputation(player, member, bounty.reputation());
        guild.achievements().afterBounty(player, member);
        guild.store().save();
        Bukkit.getServer().broadcast(Chat.line(player.getName() + " has claimed the Guild bounty: " + bounty.title() + "."));
        return new Guild.Claim(Guild.ClaimResult.CLAIMED, paid[0], paid[1], bounty.reputation(), paid[0] < bounty.coins(), List.of());
    }

    // ---- Grand Quest ----

    public GrandQuest grandQuest() {
        return EventPools.grandQuest(state().getString("grand.id"));
    }

    public int grandTotal() {
        return state().getInt("grand.total");
    }

    public boolean grandDone() {
        return state().getBoolean("grand.done");
    }

    public int contribution(Member member) {
        return state().getInt("grand.contrib." + member.id());
    }

    /** What an adventurer must put in to share the reward: 2% of the goal, at least 10. */
    public int threshold() {
        GrandQuest quest = grandQuest();
        return quest == null ? 0 : Math.max(10, quest.goal() / 50);
    }

    public boolean grandClaimed(Member member) {
        return state().getBoolean("grand.claimed." + member.id());
    }

    void rotateGrandQuest() {
        String week = guild.clock().weekKey();
        if (week.equals(state().getString("grand.week")) && grandQuest() != null) {
            return;
        }
        String last = state().getString("grand.id");
        List<GrandQuest> options = EventPools.GRAND_QUESTS.stream().filter(q -> !q.id().equals(last)).toList();
        GrandQuest next = options.get(random.nextInt(options.size()));
        state().set("grand", null);
        state().set("grand.id", next.id());
        state().set("grand.week", week);
        guild.store().markDirty();
        Bukkit.getServer().broadcast(Chat.line("This week's Grand Quest: " + next.title() + ", " + next.goal()
                + " together. Everyone who helps shares the reward. See /guild."));
    }

    public Guild.Claim claimGrand(Player player) {
        Member member = guild.member(player);
        if (member == null || grandQuest() == null) {
            return claim(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (!grandDone()) {
            return claim(Guild.ClaimResult.NOT_DONE);
        }
        if (contribution(member) < threshold()) {
            return claim(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (grandClaimed(member)) {
            return claim(Guild.ClaimResult.ALREADY_CLAIMED);
        }
        if (!guild.bank().available()) {
            return claim(Guild.ClaimResult.NO_BANK);
        }
        int[] paid = guild.mint(player, member, GRAND_COINS, GRAND_LEVELS);
        state().set("grand.claimed." + member.id(), true);
        member.addStat("grand-quests", 1);
        member.addStat("coins-earned", paid[0]);
        guild.addReputation(player, member, GRAND_REPUTATION);
        guild.store().save();
        return new Guild.Claim(Guild.ClaimResult.CLAIMED, paid[0], paid[1], GRAND_REPUTATION, paid[0] < GRAND_COINS, List.of());
    }

    private void completeGrand(GrandQuest quest) {
        state().set("grand.done", true);
        state().set("grand.done-at", now());
        Map<UUID, Integer> contributions = contributions();
        UUID champion = contributions.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        Bukkit.getServer().broadcast(Chat.line("The Grand Quest is complete: " + quest.title() + "! Everyone who gave at least "
                + threshold() + " can claim a share in /guild.", NamedTextColor.GOLD));
        if (champion != null) {
            Member member = guild.store().member(champion);
            state().set("grand.champion", member.name());
            Player online = Bukkit.getPlayer(champion);
            guild.achievements().grant(online, member, "champion");
            member.addStat("grand-champion", 1);
            Bukkit.getServer().broadcast(Chat.line(member.name() + " gave the most, " + contributions.get(champion) + ".", NamedTextColor.GOLD));
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
        }
        guild.store().save();
    }

    public Map<UUID, Integer> contributions() {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        ConfigurationSection section = state().getConfigurationSection("grand.contrib");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    out.put(UUID.fromString(key), section.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited key; skip it
                }
            }
        }
        return out;
    }

    // ---- the Guildmaster's activities ----

    public ActivityKind activity() {
        String kind = state().getString("activity.kind");
        return kind == null ? null : ActivityKind.valueOf(kind);
    }

    public long activityEnds() {
        return state().getLong("activity.ends");
    }

    public int activityPrize() {
        return state().getInt("activity.prize");
    }

    public int score(Member member) {
        return state().getInt("activity.score." + member.id());
    }

    public StartResult startActivity(Player player, ActivityKind kind, int prize, int minutes) {
        if (!guild.isGuildmaster(player)) {
            return StartResult.NOT_GUILDMASTER;
        }
        if (activity() != null) {
            return StartResult.RUNNING;
        }
        if (guild.clock().dayKey().equals(state().getString("activity.day"))) {
            return StartResult.ALREADY_TODAY;
        }
        if (prize < ACTIVITY_MIN_PRIZE || prize > ACTIVITY_MAX_PRIZE) {
            return StartResult.BAD_PRIZE;
        }
        if (guild.store().treasury().balance() < prize) {
            return StartResult.TREASURY_SHORT;
        }
        guild.store().treasury().record(now(), LedgerEntry.Kind.ESCROW, -prize, player.getName());
        state().set("activity", null);
        state().set("activity.kind", kind.name());
        state().set("activity.prize", prize);
        state().set("activity.ends", now() + Math.max(10, Math.min(60, minutes)) * 60_000L);
        state().set("activity.day", guild.clock().dayKey());
        guild.store().save();
        Bukkit.getServer().broadcast(Chat.line("Guildmaster " + player.getName() + " opens a " + kind.title() + "! For the next "
                + Math.max(10, Math.min(60, minutes)) + " minutes, " + kind.goal() + " to win " + Text.coins(prize)
                + " from the Treasury. Guild members only.", NamedTextColor.GOLD));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 1.3f);
        }
        return StartResult.STARTED;
    }

    void tickActivity() {
        ActivityKind kind = activity();
        if (kind == null) {
            return;
        }
        long left = activityEnds() - now();
        if (left <= 5 * 60_000L && left > 0 && !state().getBoolean("activity.warned")) {
            state().set("activity.warned", true);
            Bukkit.getServer().broadcast(Chat.line("Five minutes left in the " + kind.title() + "."));
        }
        if (left > 0) {
            return;
        }
        Map<UUID, Integer> scores = new LinkedHashMap<>();
        ConfigurationSection section = state().getConfigurationSection("activity.score");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                scores.put(UUID.fromString(key), section.getInt(key));
            }
        }
        int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int prize = activityPrize();
        List<UUID> winners = new ArrayList<>();
        scores.forEach((id, score) -> {
            if (score == best && best > 0) {
                winners.add(id);
            }
        });
        String guildmaster = Bukkit.getOfflinePlayer(guild.config().guildmaster()).getName();
        if (winners.isEmpty() || !guild.bank().available()) {
            guild.store().treasury().record(now(), LedgerEntry.Kind.REFUND, prize, guildmaster == null ? "Guildmaster" : guildmaster);
            Bukkit.getServer().broadcast(Chat.line("The " + kind.title() + " is over with no winner. The prize goes back to the Treasury."));
        } else {
            int share = prize / winners.size();
            List<String> names = new ArrayList<>();
            for (UUID id : winners) {
                OfflinePlayer winner = Bukkit.getOfflinePlayer(id);
                guild.bank().deposit(winner, share);
                Member member = guild.store().member(id);
                names.add(member == null ? String.valueOf(winner.getName()) : member.name());
                if (member != null) {
                    member.addStat("activities-won", 1);
                    guild.achievements().grant(winner.getPlayer(), member, "derby");
                }
            }
            int rest = prize - share * winners.size();
            if (rest > 0) {
                guild.store().treasury().record(now(), LedgerEntry.Kind.REFUND, rest, "remainder");
            }
            Bukkit.getServer().broadcast(Chat.line("The " + kind.title() + " is over! " + String.join(" and ", names)
                    + " won with " + best + ", taking " + Text.coins(share) + (winners.size() > 1 ? " each." : "."), NamedTextColor.GOLD));
        }
        state().set("activity", null);
        state().set("activity.day", guild.clock().dayKey());
        guild.store().save();
    }

    // ---- progress ----

    /** Counts a member's deed toward the bounty, the Grand Quest and a running activity. */
    public void progress(Player player, Member member, Kind kind, Collection<String> facts, int amount) {
        Bounty bounty = bounty();
        if (bounty != null && !bountyClaimed() && bountyEligible(member)) {
            Objective o = bounty.objective();
            int before = bountyProgress(member);
            if (o.kind() == kind && before < o.amount() && o.matches(facts)) {
                int after = Math.min(o.amount(), before + amount);
                state().set("bounty.progress." + member.id(), after);
                guild.store().markDirty();
                if (after >= o.amount()) {
                    Chat.send(player, "Bounty done: " + bounty.title() + ". Claim it in /guild before anyone else does!", NamedTextColor.GOLD);
                }
            }
        }
        GrandQuest quest = grandQuest();
        if (quest != null && !grandDone() && quest.kind() == kind && quest.objective().matches(facts)) {
            state().set("grand.contrib." + member.id(), contribution(member) + amount);
            state().set("grand.total", grandTotal() + amount);
            guild.store().markDirty();
            if (grandTotal() >= quest.goal()) {
                completeGrand(quest);
            }
        }
        ActivityKind activity = activity();
        if (activity != null && now() < activityEnds() && !guild.config().isGuildmaster(member.id()) && counts(activity, kind, facts)) {
            state().set("activity.score." + member.id(), score(member) + amount);
            guild.store().markDirty();
        }
    }

    static boolean counts(ActivityKind activity, Kind kind, Collection<String> facts) {
        return switch (activity) {
            case HUNT -> kind == Kind.HUNT && facts.contains("#HOSTILE");
            case FISH -> kind == Kind.FISH;
            case MINE -> kind == Kind.MINE && facts.stream().anyMatch(EventPools.ORES::contains);
        };
    }

    public static ActivityKind parseActivity(String value) {
        try {
            return ActivityKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Guild.Claim claim(Guild.ClaimResult result) {
        return new Guild.Claim(result, 0, 0, 0, false, List.of());
    }
}

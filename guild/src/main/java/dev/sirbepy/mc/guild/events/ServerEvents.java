package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.EventPools.Bounty;
import dev.sirbepy.mc.guild.events.EventPools.GrandQuest;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Guild's server-wide events: one bounty at a time (C-Rank and up, first to claim wins, a new one
 * every 3 days or 12 h after it's claimed), a weekly Grand Quest everyone pushes together (contributors
 * over a threshold share the reward, the top one earns a title), and the Guildmaster's short contests
 * (one a day, prize from the Treasury). State lives in guild.yml's "events" section.
 *
 * <p>The three systems share only this clock/dispatch shell; their own rules live in
 * {@link Bounties}, {@link GrandQuests} and {@link Activities}, which this class delegates to.
 */
public final class ServerEvents {

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

    private final Bounties bounties;
    private final GrandQuests grandQuests;
    private final Activities activities;

    public ServerEvents(Guild guild, Random random) {
        this.bounties = new Bounties(guild, random);
        this.grandQuests = new GrandQuests(guild, random);
        this.activities = new Activities(guild);
    }

    // ---- the clock ----

    /** Rotates the bounty and Grand Quest and ends a finished activity; run every few seconds. */
    public void tick() {
        bounties.rotate();
        grandQuests.rotate();
        activities.tick();
    }

    // ---- bounty ----

    public Bounty bounty() {
        return bounties.bounty();
    }

    public boolean bountyClaimed() {
        return bounties.claimed();
    }

    public String bountyClaimedBy() {
        return bounties.claimedBy();
    }

    public long bountyEnds() {
        return bounties.ends();
    }

    public int bountyProgress(Member member) {
        return bounties.progressOf(member);
    }

    public boolean bountyEligible(Member member) {
        return bounties.eligible(member);
    }

    public Guild.Claim claimBounty(Player player) {
        return bounties.claim(player);
    }

    // ---- Grand Quest ----

    public GrandQuest grandQuest() {
        return grandQuests.current();
    }

    public int grandTotal() {
        return grandQuests.total();
    }

    public boolean grandDone() {
        return grandQuests.done();
    }

    public int contribution(Member member) {
        return grandQuests.contributionOf(member);
    }

    /** What an adventurer must put in to share the reward: 2% of the goal, at least 10. */
    public int threshold() {
        return grandQuests.threshold();
    }

    public boolean grandClaimed(Member member) {
        return grandQuests.claimedBy(member);
    }

    public Guild.Claim claimGrand(Player player) {
        return grandQuests.claim(player);
    }

    public Map<UUID, Integer> contributions() {
        return grandQuests.contributions();
    }

    // ---- the Guildmaster's activities ----

    public ActivityKind activity() {
        return activities.current();
    }

    public long activityEnds() {
        return activities.ends();
    }

    public int activityPrize() {
        return activities.prize();
    }

    public int score(Member member) {
        return activities.score(member);
    }

    public StartResult startActivity(Player player, ActivityKind kind, int prize, int minutes) {
        return activities.start(player, kind, prize, minutes);
    }

    // ---- progress ----

    /** Counts a member's deed toward the bounty, the Grand Quest and a running activity. */
    public void progress(Player player, Member member, Kind kind, Collection<String> facts, int amount) {
        bounties.progress(player, member, kind, facts, amount);
        grandQuests.progress(member, kind, facts, amount);
        activities.progress(member, kind, facts, amount);
    }

    public static ActivityKind parseActivity(String value) {
        try {
            return ActivityKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

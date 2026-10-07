package dev.sirbepy.mc.guild.data;

import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.rank.Rank;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A contract the Guildmaster posted, its reward escrowed from the Treasury. Every eligible member makes
 * progress on it at once; the first to claim it is paid and it closes.
 */
public final class Commission {

    public enum Status { OPEN, CLAIMED, CANCELLED }

    private final String id;
    private final UUID poster;
    private final Objective objective;
    private final int reward;
    private final Rank minRank;
    private final long postedAt;
    private final String week;
    private Status status = Status.OPEN;
    private UUID claimedBy;
    private String claimedName;
    private final Map<UUID, Integer> progress = new HashMap<>();

    public Commission(String id, UUID poster, Objective objective, int reward, Rank minRank, long postedAt, String week) {
        this.id = id;
        this.poster = poster;
        this.objective = objective;
        this.reward = reward;
        this.minRank = minRank;
        this.postedAt = postedAt;
        this.week = week;
    }

    public String id() {
        return id;
    }

    /** The Guildmaster who posted it; the weekly allowance is counted per Guildmaster. */
    public UUID poster() {
        return poster;
    }

    public Objective objective() {
        return objective;
    }

    public int reward() {
        return reward;
    }

    public Rank minRank() {
        return minRank;
    }

    public long postedAt() {
        return postedAt;
    }

    public String week() {
        return week;
    }

    public Status status() {
        return status;
    }

    public boolean open() {
        return status == Status.OPEN;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID claimedBy() {
        return claimedBy;
    }

    public String claimedName() {
        return claimedName;
    }

    public void setClaimed(UUID by, String name) {
        this.claimedBy = by;
        this.claimedName = name;
        this.status = Status.CLAIMED;
    }

    public int progress(UUID member) {
        return progress.getOrDefault(member, 0);
    }

    public void setProgress(UUID member, int value) {
        progress.put(member, value);
    }

    public Map<UUID, Integer> allProgress() {
        return progress;
    }
}

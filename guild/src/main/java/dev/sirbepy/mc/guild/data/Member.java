package dev.sirbepy.mc.guild.data;

import dev.sirbepy.mc.guild.rank.Rank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One registered adventurer: rank, reputation, board, today's earnings and trial. */
public final class Member {

    private final UUID id;
    private String name;
    private Rank rank = Rank.E;
    private int reputation;
    private int contractsDone;
    private long joinedAt;
    private long lastActive;

    private String boardDay;
    private final List<ActiveContract> dailies = new ArrayList<>();
    private final Set<String> previousDailies = new LinkedHashSet<>();
    private int rerollsUsed;
    private String boardWeek;
    private ActiveContract weekly;

    private String earnDay;
    private int coinsToday;
    private int levelsToday;

    private ActiveContract trial;
    private long trialStarted;
    private long trialRetryAfter;

    private final Map<String, Integer> stats = new HashMap<>();
    private final Set<String> titles = new LinkedHashSet<>();
    private String activeTitle;
    private boolean showTitle;
    private String lastClaimDay;
    private int streak;
    private long weeklyDrawnAt;
    private final Set<String> structuresSeen = new LinkedHashSet<>();
    private final Set<String> biomesSeen = new LinkedHashSet<>();

    public Member(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Rank rank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public int reputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        this.reputation = Math.max(0, reputation);
    }

    public int contractsDone() {
        return contractsDone;
    }

    public void setContractsDone(int contractsDone) {
        this.contractsDone = contractsDone;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    public long lastActive() {
        return lastActive;
    }

    public void setLastActive(long lastActive) {
        this.lastActive = lastActive;
    }

    public String boardDay() {
        return boardDay;
    }

    public void setBoardDay(String boardDay) {
        this.boardDay = boardDay;
    }

    public List<ActiveContract> dailies() {
        return dailies;
    }

    public Set<String> previousDailies() {
        return previousDailies;
    }

    public int rerollsUsed() {
        return rerollsUsed;
    }

    public void setRerollsUsed(int rerollsUsed) {
        this.rerollsUsed = rerollsUsed;
    }

    public String boardWeek() {
        return boardWeek;
    }

    public void setBoardWeek(String boardWeek) {
        this.boardWeek = boardWeek;
    }

    public ActiveContract weekly() {
        return weekly;
    }

    public void setWeekly(ActiveContract weekly) {
        this.weekly = weekly;
    }

    public String earnDay() {
        return earnDay;
    }

    public void setEarnDay(String earnDay) {
        this.earnDay = earnDay;
    }

    public int coinsToday() {
        return coinsToday;
    }

    public void setCoinsToday(int coinsToday) {
        this.coinsToday = coinsToday;
    }

    public int levelsToday() {
        return levelsToday;
    }

    public void setLevelsToday(int levelsToday) {
        this.levelsToday = levelsToday;
    }

    public ActiveContract trial() {
        return trial;
    }

    public void setTrial(ActiveContract trial) {
        this.trial = trial;
    }

    public long trialStarted() {
        return trialStarted;
    }

    public void setTrialStarted(long trialStarted) {
        this.trialStarted = trialStarted;
    }

    public long trialRetryAfter() {
        return trialRetryAfter;
    }

    public void setTrialRetryAfter(long trialRetryAfter) {
        this.trialRetryAfter = trialRetryAfter;
    }

    public Map<String, Integer> stats() {
        return stats;
    }

    public int stat(String key) {
        return stats.getOrDefault(key, 0);
    }

    public void addStat(String key, int amount) {
        stats.merge(key, amount, Integer::sum);
    }

    /** Title ids this adventurer has earned, in the order they came. */
    public Set<String> titles() {
        return titles;
    }

    public String activeTitle() {
        return activeTitle;
    }

    public void setActiveTitle(String activeTitle) {
        this.activeTitle = activeTitle;
    }

    /** Whether the worn title shows beside the name in chat and the tab list; opt-in, off by default. */
    public boolean showTitle() {
        return showTitle;
    }

    public void setShowTitle(boolean showTitle) {
        this.showTitle = showTitle;
    }

    public String lastClaimDay() {
        return lastClaimDay;
    }

    public void setLastClaimDay(String lastClaimDay) {
        this.lastClaimDay = lastClaimDay;
    }

    /** Consecutive real days with at least one claimed contract, counting the last claim day. */
    public int streak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public long weeklyDrawnAt() {
        return weeklyDrawnAt;
    }

    public void setWeeklyDrawnAt(long weeklyDrawnAt) {
        this.weeklyDrawnAt = weeklyDrawnAt;
    }

    public Set<String> structuresSeen() {
        return structuresSeen;
    }

    public Set<String> biomesSeen() {
        return biomesSeen;
    }

    /** Every contract on the board that can still make progress: dailies, weekly, trial. */
    public List<ActiveContract> openContracts() {
        List<ActiveContract> open = new ArrayList<>();
        for (ActiveContract daily : dailies) {
            if (!daily.claimed()) {
                open.add(daily);
            }
        }
        if (weekly != null && !weekly.claimed()) {
            open.add(weekly);
        }
        if (trial != null && !trial.claimed()) {
            open.add(trial);
        }
        return open;
    }
}

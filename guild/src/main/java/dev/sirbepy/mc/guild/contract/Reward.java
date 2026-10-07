package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.rank.Rank;

/** What a contract pays, fixed when it is drawn so the board can show it before the work. */
public record Reward(int coins, int reputation, int levels, boolean bundle) {

    public static final int WEEKLY_COINS = 5;
    public static final int WEEKLY_REPUTATION = 4;
    public static final int WEEKLY_LEVELS = 5;
    public static final int TRIAL_LEVELS = 5;

    /** {@code rank} is the rank the reward is measured at: the contract's own for dailies and weeklies,
     * the adventurer's for rares and trials. */
    public static Reward of(ContractDef def, Rank rank) {
        int coins = (int) Math.round(rank.coins() * def.effort());
        int rep = (int) Math.round(rank.rep() * def.effort());
        return switch (def.type()) {
            case DAILY -> new Reward(coins, rep, rank.levels(), false);
            case WEEKLY -> new Reward(coins * WEEKLY_COINS, rep * WEEKLY_REPUTATION, WEEKLY_LEVELS, false);
            case RARE -> new Reward(coins * 2, rep * 2, rank.levels(), true);
            case TRIAL -> new Reward(rank.coins() * 2, 0, TRIAL_LEVELS, true);
        };
    }
}

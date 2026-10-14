package dev.sirbepy.mc.minibosses.registry;

/**
 * The difficulty a Guardian fight is held at. A fixed 3-rung ladder (unlike {@code endfight.Tier}'s
 * 7 rungs): every Guardian defines its own {@link GuardianTierRow} for each of these three, there
 * is no cross-Guardian shared reward table the way {@code endfight.reward.TierRewardRow} is shared
 * across every dragon fight.
 */
public enum Tier {
    NORMAL("Normal"),
    HEROIC("Heroic"),
    MYTHIC("Mythic");

    private final String displayName;

    Tier(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

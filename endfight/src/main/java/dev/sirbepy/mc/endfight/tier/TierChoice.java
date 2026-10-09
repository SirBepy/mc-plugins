package dev.sirbepy.mc.endfight.tier;

/**
 * Which {@link Tier} the current or next dragon fight is held at. The altar menu sets it; rewards
 * read it when a dragon dies.
 */
public interface TierChoice {

    /** The tier of the fight in progress (or of the next one, before its dragon spawns). */
    Tier current();

    /** True when no dragon has ever died in this End, which forces the first fight's tier. */
    boolean firstKillEver();
}

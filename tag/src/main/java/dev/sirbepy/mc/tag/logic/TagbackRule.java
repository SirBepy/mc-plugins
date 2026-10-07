package dev.sirbepy.mc.tag.logic;

import java.util.UUID;

/**
 * Pure tagback check: the new IT cannot tag the player who just tagged them until the cooldown
 * expires. Anyone else is fair game immediately.
 */
public final class TagbackRule {

    private TagbackRule() {
    }

    public static boolean isProtected(UUID candidate, UUID lastTagger, long tagbackExpiryMillis, long nowMillis) {
        return lastTagger != null && lastTagger.equals(candidate) && nowMillis < tagbackExpiryMillis;
    }

    /** Rounded up, so the message never says 0s while the hit is still blocked. */
    public static long secondsLeft(long tagbackExpiryMillis, long nowMillis) {
        return Math.max(1, (tagbackExpiryMillis - nowMillis + 999) / 1000);
    }
}

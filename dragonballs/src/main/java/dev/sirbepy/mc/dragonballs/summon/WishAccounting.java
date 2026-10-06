package dev.sirbepy.mc.dragonballs.summon;

/** Pure wish-count math for {@code /shenron grant}: how many are left, and whether that was the
 * session's last one. Kept free of {@link SummonSession} so the accounting rules are testable
 * without building a whole session/scene. */
public final class WishAccounting {

    private WishAccounting() {
    }

    public record GrantResult(boolean accepted, int remainingAfter, boolean sessionEnds, String error) {
    }

    /** @param wishesLeft the session's current count before this grant
     *  @param count how many of them this grant consumes */
    public static GrantResult grant(int wishesLeft, int count) {
        if (count < 1) {
            return new GrantResult(false, wishesLeft, false, "count must be at least 1");
        }
        if (count > wishesLeft) {
            return new GrantResult(false, wishesLeft, false,
                    "only " + wishesLeft + " wish(es) left, can't grant " + count);
        }
        int remaining = wishesLeft - count;
        return new GrantResult(true, remaining, remaining == 0, null);
    }
}

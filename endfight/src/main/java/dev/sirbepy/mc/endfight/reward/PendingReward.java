package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;

import java.util.List;

/** One fight's rewards still owed to a participant who was offline at the kill (or delivered
 * straight away to one who wasn't - see {@link RewardService}): {@code xp} is always owed (the
 * weekly hoard limit never touches it); {@code hoard} is null when that limit already gave this
 * tier's hoard to this player this week, so only XP is queued. {@code dragonHeadHelm}: they dealt
 * the final blow, so a {@link DragonHeadHelm} goes in the hoard's centre, or alone without one. */
record PendingReward(int xp, HoardSpec hoard, boolean dragonHeadHelm) {

    PendingReward(int xp, HoardSpec hoard) {
        this(xp, hoard, false);
    }

    /**
     * An already-rolled Dragon Hoard, decided at kill time rather than re-rolled on delivery, so
     * a player's owned-armor/aura/head progress stays correct even if they are offline for a
     * while. {@code rolls} holds 0 to {@link RollEngine#MAX_ROLLS} bonus-roll grants.
     */
    record HoardSpec(Tier tier, String dateLabel, int diamonds, int shells, int gapples, int scrap,
                      List<RollGrant> rolls, TierRewardRow.Filler filler) {

        HoardSpec(Tier tier, String dateLabel, int diamonds, int shells, int gapples, int scrap, List<RollGrant> rolls) {
            this(tier, dateLabel, diamonds, shells, gapples, scrap, rolls, TierRewardRow.Filler.NONE);
        }
    }

    /** One roll's outcome, flattened to primitives for YAML: {@code id} is an {@link ArmorPiece}
     * name for {@code ARMOR}/{@code DUP_ARMOR}, a {@link DragonAura} name for {@code AURA}, or a
     * {@code DragonTrophyHeads.Head} id for {@code HEAD}. */
    record RollGrant(RollEngine.Kind kind, String id) {
    }
}

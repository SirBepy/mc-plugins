package dev.sirbepy.mc.endfight.reward;

/** One fight's rewards still owed to a participant who was offline at the kill: {@code bundles}
 * is always 1 today (one bundle per participant per fight), kept as a field rather than assumed so
 * {@code pending-rewards.yml} reads as a self-describing list of {@code {bundles, xp}} entries. */
record PendingReward(int xp, int bundles) {
}

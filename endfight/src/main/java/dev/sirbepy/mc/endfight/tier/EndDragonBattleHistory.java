package dev.sirbepy.mc.endfight.tier;

/**
 * Whether a dragon has ever died in this server's End, independent of {@link TierService}'s own
 * persisted memory of it. A real implementation reads {@code World#getEnderDragonBattle()
 * .hasBeenPreviouslyKilled()}; behind an interface because MockBukkit's {@code WorldMock} throws
 * {@code UnimplementedOperationException} for that call unconditionally (same API, same gap
 * {@code RewardService}'s class docs already note - confirmed there via javap on the pinned
 * {@code mockbukkit-v26.2-4.116.1.jar}).
 */
public interface EndDragonBattleHistory {

    boolean hasBeenPreviouslyKilled();
}

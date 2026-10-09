package dev.sirbepy.mc.endfight.reset;

import org.bukkit.World;

import java.util.Optional;

/** Hides {@code World#getEnderDragonBattle()} behind an interface {@link RespawnWatcher} can run
 * against in a unit test - MockBukkit's {@code WorldMock} throws for the real call. */
public interface DragonBattleView {

    /** Empty if {@code world} has no dragon battle at all (not the End, or no battle started). */
    Optional<DragonRespawnPhase> respawnPhase(World world);

    /** Whether a live (not dead/removed) Ender Dragon currently exists in {@code world}. */
    boolean hasLivingDragon(World world);
}

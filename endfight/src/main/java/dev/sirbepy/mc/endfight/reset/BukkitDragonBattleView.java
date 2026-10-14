package dev.sirbepy.mc.endfight.reset;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;

import java.util.Optional;

/** Real {@link DragonBattleView}: {@code World#getEnderDragonBattle()}, mapped onto our own
 * {@link DragonRespawnPhase} so the rest of the module never imports {@code DragonBattle}
 * directly. */
public final class BukkitDragonBattleView implements DragonBattleView {

    @Override
    public Optional<DragonRespawnPhase> respawnPhase(World world) {
        DragonBattle battle = world.getEnderDragonBattle();
        if (battle == null) {
            return Optional.empty();
        }
        DragonBattle.RespawnPhase phase = battle.getRespawnPhase();
        if (phase == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(DragonRespawnPhase.valueOf(phase.name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean hasLivingDragon(World world) {
        return DragonFighters.liveDragon(world).isPresent();
    }
}

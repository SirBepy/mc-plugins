package dev.sirbepy.mc.endfight.reset;

import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;

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
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            if (dragon.isValid() && !dragon.isDead()) {
                return true;
            }
        }
        return false;
    }
}

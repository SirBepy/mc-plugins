package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * Makes the summon dragon harmless at the event level, on top of (not instead of) setAI(false)
 * and setInvulnerable(true) in {@link DragonActor}. A NoAI EnderDragon already skips its own
 * phase-driven knockBack/hurt/checkWalls calls (confirmed by reading the mojang-mapped
 * EnderDragon.aiStep bytecode: isNoAi() gates an early return before any of those run), but this
 * listener is defense in depth against anything that bypasses that, and it is the only place
 * MockBukkit can exercise "harmless" deterministically in a test.
 */
final class DragonGuardListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isOurDragon(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isOurDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isOurDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (isOurDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isOurDragon(event.getEntity()) || isOurDragon(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** True for the dragon itself or one of its head/neck/body/tail/wing {@link ComplexEntityPart}s. */
    static boolean isOurDragon(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG)) {
            return true;
        }
        if (entity instanceof ComplexEntityPart part) {
            return part.getParent() != null && part.getParent().getScoreboardTags().contains(EffectMarkers.DRAGON_TAG);
        }
        return false;
    }
}

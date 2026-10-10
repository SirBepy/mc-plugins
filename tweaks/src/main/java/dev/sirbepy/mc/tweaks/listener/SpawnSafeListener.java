package dev.sirbepy.mc.tweaks.listener;

import dev.sirbepy.mc.core.SpawnArea;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spawn is a safe space from PvP and mobs (Joe, 2026-10-10): inside the {@link SpawnArea} nothing
 * hurts a player (another player, a mob, or their arrows and blasts), nobody hits a player from
 * inside it, harmful potions thrown by players skip players in it, hostile mobs don't spawn in it,
 * can't target a player in it, and one that walks in is removed. Villagers, merfolk, animals and
 * pets aren't {@link Enemy}, so they're untouched. Tag stays out of spawn on its own side
 * (TagDamageListener), since it tags at LOWEST before this listener cancels the hit.
 */
public final class SpawnSafeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (SpawnArea.contains(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }
        Player attacker = SpawnGuardListener.playerBehind(event.getDamager());
        if (attacker != null && !attacker.equals(victim) && SpawnArea.contains(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower) || !anyHarmful(event.getPotion().getEffects())) {
            return;
        }
        for (LivingEntity hit : new ArrayList<>(event.getAffectedEntities())) {
            if (shieldedFrom(thrower, hit)) {
                event.setIntensity(hit, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLinger(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower) || !anyHarmful(cloudEffects(cloud))) {
            return;
        }
        event.getAffectedEntities().removeIf(hit -> shieldedFrom(thrower, hit));
    }

    private static boolean shieldedFrom(Player thrower, LivingEntity hit) {
        return hit instanceof Player victim && !victim.equals(thrower)
                && (SpawnArea.contains(victim.getLocation()) || SpawnArea.contains(thrower.getLocation()));
    }

    private static List<PotionEffect> cloudEffects(AreaEffectCloud cloud) {
        List<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        PotionType base = cloud.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        return effects;
    }

    private static boolean anyHarmful(Collection<PotionEffect> effects) {
        return effects.stream().anyMatch(e -> e.getType().getCategory() == PotionEffectTypeCategory.HARMFUL);
    }

    // Commands and plugins (CUSTOM: cartel hits, event mobs) still spawn whatever they mean to;
    // a despawnable one is cleared by the sweep anyway.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (event.getEntity() instanceof Enemy && reason != CreatureSpawnEvent.SpawnReason.COMMAND
                && reason != CreatureSpawnEvent.SpawnReason.CUSTOM && SpawnArea.contains(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Enemy && event.getTarget() instanceof Player target
                && SpawnArea.contains(target.getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Once a second: removes hostile mobs that walked (or were pushed, or plugin-spawned) into
     * spawn. Only ones that would despawn on their own anyway; a name-tagged or plugin-kept
     * (persistent) mob is left alone.
     */
    public void sweep(World world) {
        Location spawn = world.getSpawnLocation();
        BoundingBox box = new BoundingBox(
                spawn.getBlockX() - SpawnArea.RADIUS, world.getMinHeight(), spawn.getBlockZ() - SpawnArea.RADIUS,
                spawn.getBlockX() + SpawnArea.RADIUS + 1, world.getMaxHeight(), spawn.getBlockZ() + SpawnArea.RADIUS + 1);
        sweep(world.getNearbyEntities(box));
    }

    static void sweep(Iterable<Entity> nearSpawn) {
        for (Entity entity : nearSpawn) {
            if (entity instanceof Enemy && entity instanceof LivingEntity living && living.getRemoveWhenFarAway()
                    && SpawnArea.contains(entity.getLocation())) {
                entity.remove();
            }
        }
    }
}

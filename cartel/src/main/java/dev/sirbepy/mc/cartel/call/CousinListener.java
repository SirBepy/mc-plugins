package dev.sirbepy.mc.cartel.call;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Keeps the cousins on the job: they only target who {@link CallManager#allowedTarget} allows, never
 * hurt their boss or each other, can't be hurt by their boss, never turn into drowned, and drop
 * nothing when they die (Joe: their gear never drops). Their crossbow bolts and rockets hurt only who
 * the call lets them fight, a fixed 3-4 hearts a hit through any armor, with no knockback or fire. A boss who was offline when a hit landed
 * gets the trophy head on their next join.
 */
public final class CousinListener implements Listener {

    /** Joe, 2026-10-08: 3 to 4 hearts a hit, uniform, for a bolt and a rocket's blast alike. */
    static final double MIN_SHOT_DAMAGE = 6.0;
    static final double MAX_SHOT_DAMAGE = 8.0;
    /**
     * Armor points and toughness, Protection-type enchantments (Blast Protection included), and the
     * last hit's damage taken off a hit inside the victim's half-second of hurt immunity, which would
     * leave nothing of a fixed 6-8 after another 6-8 (a rocket on the heels of a bolt did 0).
     */
    private static final List<DamageModifier> ARMOR_REDUCTIONS = List.of(
            DamageModifier.ARMOR, DamageModifier.MAGIC, DamageModifier.INVULNERABILITY_REDUCTION);

    private final CallManager calls;
    private final Cousins cousins;
    private final Trophies trophies;
    private final DoubleSupplier shotDamage;

    public CousinListener(CallManager calls, Cousins cousins, Trophies trophies) {
        this(calls, cousins, trophies,
                () -> ThreadLocalRandom.current().nextDouble(MIN_SHOT_DAMAGE, MAX_SHOT_DAMAGE));
    }

    CousinListener(CallManager calls, Cousins cousins, Trophies trophies, DoubleSupplier shotDamage) {
        this.calls = calls;
        this.cousins = cousins;
        this.trophies = trophies;
        this.shotDamage = shotDamage;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (cousins.isCousin(event.getEntity()) && !calls.allowedTarget(event.getEntity(), event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        LivingEntity attacker = attacker(event.getDamager());
        boolean byCousin = cousins.isCousin(attacker);
        if ((byCousin && (calls.isOwner(victim) || cousins.isCousin(victim)))
                || (cousins.isCousin(victim) && calls.isOwner(attacker))) {
            event.setCancelled(true);
        }
    }

    /** A bolt (Piercing IV passes on) or a rocket's blast that reaches anyone the call doesn't let the cousins fight hurts nobody. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShot(EntityDamageByEntityEvent event) {
        if (cousinShot(event.getDamager()) && !calls.fairGame(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Anyone a bolt or a rocket's blast may hurt takes {@link #MIN_SHOT_DAMAGE}-{@link #MAX_SHOT_DAMAGE},
     * whatever the bolt's speed, the blast's distance or the difficulty, and armor and Protection take
     * none of it off (Joe). Resistance, a shield and absorption hearts still count. Last in line so no
     * other plugin's damage change sticks, and a rocket's blast goes through Towny, which cancels
     * explosion damage inside any town with explosions off (its default), as a bolt already does.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShotHit(EntityDamageByEntityEvent event) {
        if (!cousinShot(event.getDamager()) || !calls.fairGame(event.getEntity())) {
            return;
        }
        if (event.isCancelled()) {
            if (!(event.getDamager() instanceof Firework)) {
                return;
            }
            event.setCancelled(false);
        }
        // Setting the base re-derives every reduction from it, so these are zeroed after.
        event.setDamage(shotDamage.getAsDouble());
        for (DamageModifier reduction : ARMOR_REDUCTIONS) {
            if (event.isApplicable(reduction)) {
                event.setDamage(reduction, 0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShotKnockback(EntityPushedByEntityAttackEvent event) {
        if (cousinShot(event.getPushedBy())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShotFire(EntityCombustByEntityEvent event) {
        if (cousinShot(event.getCombuster())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        calls.noteAttack(event.getEntity(), attacker(event.getDamager()));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (cousins.isCousin(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        calls.onPlayerDeath(event.getEntity(), event.getDamageSource().getCausingEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        trophies.deliverPending(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (cousins.isCousin(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** A bolt or a firework rocket: a rocket's blast names the rocket as both damager and pusher. */
    private boolean cousinShot(Entity entity) {
        return entity instanceof Projectile shot && shot.getShooter() instanceof Entity shooter
                && cousins.isCousin(shooter);
    }

    static LivingEntity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }
}

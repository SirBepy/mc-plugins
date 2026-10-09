package dev.sirbepy.mc.tweaks.netherwishes;

import dev.sirbepy.mc.tweaks.OwnerArgument;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CoffiebagSupreme's Nether Porunga wish 2 (2026-10-08, session n97wtc): zombies, zombie villagers, husks
 * and drowned never target him, except one he has hurt in the last {@link #GRUDGE}. Reinforcements and
 * the zombies a hurt one calls over hold no grudge, so only the one he hit fights back. Mobs a plugin
 * spawned (the cartel's Salamanca cousins, anything else from {@code World#spawn}) are outside the truce.
 */
public final class ZombieTruce implements Listener {

    /** How long a zombie he hurt may fight him, counted from his latest hit; it ends early if the zombie dies. */
    static final Duration GRUDGE = Duration.ofMinutes(5);

    /** Zombie and husk follow range is 35; this reaches every mob that could be chasing him. */
    static final double SWEEP_RADIUS = 48;

    static final Set<EntityType> TYPES = Set.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED);

    static final NamespacedKey GRUDGE_UNTIL = new NamespacedKey("tweaks", "truce_grudge_until");

    private final Server server;
    private final Supplier<UUID> owner;
    private final LongSupplier clock;

    public ZombieTruce(Server server, Supplier<UUID> owner, LongSupplier clock) {
        this.server = server;
        this.owner = owner;
        this.clock = clock;
    }

    private boolean isOwner(Entity entity) {
        return OwnerArgument.isOwner(entity, owner.get());
    }

    /** Paper keeps the spawn reason in the mob's save data, so a cousin stays outside the truce after a restart. */
    static boolean covers(Entity mob) {
        return TYPES.contains(mob.getType()) && mob.getEntitySpawnReason() != SpawnReason.CUSTOM;
    }

    boolean holdsGrudge(Entity mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        Long until = data.get(GRUDGE_UNTIL, PersistentDataType.LONG);
        if (until == null) return false;
        if (until > clock.getAsLong()) return true;
        data.remove(GRUDGE_UNTIL);
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isOwner(event.getTarget()) && covers(event.getEntity()) && !holdsGrudge(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // MONITOR: the grudge starts only from a hit that landed. The mob's retaliation targets him on its next AI
    // tick, after this.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity mob = event.getEntity();
        if (!covers(mob) || !isOwner(attacker(event))) return;
        mob.getPersistentDataContainer().set(GRUDGE_UNTIL, PersistentDataType.LONG, clock.getAsLong() + GRUDGE.toMillis());
    }

    private static Entity attacker(EntityDamageByEntityEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing != null) return causing;
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Entity entity ? entity : null;
        }
        return event.getDamager();
    }

    /**
     * Once a second: a covered mob still chasing him without a grudge drops him. That ends a grudge that ran
     * out mid-chase, and catches targets set without an event.
     */
    public void tick() {
        UUID id = owner.get();
        Player player = id == null ? null : server.getPlayer(id);
        if (player == null) return;
        for (Entity entity : player.getNearbyEntities(SWEEP_RADIUS, SWEEP_RADIUS, SWEEP_RADIUS)) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget()) && covers(mob) && !holdsGrudge(mob)) {
                mob.setTarget(null);
            }
        }
    }
}

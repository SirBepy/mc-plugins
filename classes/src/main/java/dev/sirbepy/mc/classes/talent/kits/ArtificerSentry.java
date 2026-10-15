package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Artificer's Engineer branch: the Sentry turret (ability 2) and everything that rides its tick
 * (Calibrated, Reinforced, Multishot, Tesla Coil, Range Finder, Frost Rounds, Repair Drone, Mortar),
 * plus Overdrive's "fires twice as fast". Sentry arrows are tagged in the PDC and flagged
 * {@code PickupStatus.DISALLOWED} so a stray arrow never lingers or hurts a player
 * (docs/classes-revamp.md rule 1 and the Sentry's own section).
 */
final class ArtificerSentry implements Listener {

    private static final double BASE_DAMAGE = 3; // 1.5 hearts
    private static final int BASE_RANGE = 10;
    private static final int BASE_DURATION_TICKS = 20 * 20;

    private final KitContext ctx;
    private final NamespacedKey arrowKey;
    private final Consumer<Player> onKill;
    private final Ability ability;
    private final Map<UUID, ArmorStand> active = new HashMap<>();
    private final Map<UUID, BukkitTask> ticks = new HashMap<>();
    private final Map<UUID, Boolean> overdrive = new HashMap<>();

    ArtificerSentry(KitContext ctx, Consumer<Player> onKill) {
        this.ctx = ctx;
        this.arrowKey = new NamespacedKey(ctx.plugin(), "artificer_sentry_arrow");
        this.onKill = onKill;
        this.ability = SimpleAbility.of("Sentry", 60, p -> "place a turret that fires at hostile mobs for 20 s", this::cast);
    }

    void register() {
        ctx.listen(this);
    }

    Ability ability() {
        return ability;
    }

    void startOverdrive(Player player, long ticksLeft) {
        UUID id = player.getUniqueId();
        overdrive.put(id, true);
        ctx.later(() -> overdrive.remove(id), ticksLeft);
    }

    private boolean cast(Player player) {
        Location at = lookedAtSpot(player, 5);
        UUID id = player.getUniqueId();
        dismissExisting(id);
        int reinforced = ctx.rank(player, "reinforced");
        long durationTicks = BASE_DURATION_TICKS + (reinforced == 1 ? 100L : reinforced == 2 ? 200L : 0L);
        ArmorStand sentry = ctx.summons().spawn(player, at, ArmorStand.class, durationTicks, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSmall(true);
        });
        active.put(id, sentry);
        BukkitTask task = tickSentry(player, sentry);
        ticks.put(id, task);
        ctx.later(() -> {
            active.remove(id);
            BukkitTask t = ticks.remove(id);
            if (t != null) t.cancel();
        }, durationTicks);
        ctx.fx().burst(at, Particle.ELECTRIC_SPARK, 15, 0.3);
        ctx.fx().sound(at, Sound.BLOCK_ANVIL_PLACE, 1f, 1.4f);
        return true;
    }

    /**
     * The spot {@code player} is looking at, up to {@code maxDistance} blocks: walked by hand in
     * half-block steps rather than through {@code LivingEntity#rayTraceBlocks}, which MockBukkit
     * doesn't implement (see the report's Deviations); falls back to the player's own feet.
     */
    static Location lookedAtSpot(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 0.5; d <= maxDistance; d += 0.5) {
            var block = eye.clone().add(dir.clone().multiply(d)).getBlock();
            if (!block.getType().isAir()) return block.getLocation().add(0.5, 1, 0.5);
        }
        return player.getLocation();
    }

    private void dismissExisting(UUID id) {
        ArmorStand old = active.remove(id);
        if (old != null && old.isValid()) ctx.summons().dismiss(old);
        BukkitTask oldTask = ticks.remove(id);
        if (oldTask != null) oldTask.cancel();
    }

    private BukkitTask tickSentry(Player owner, ArmorStand sentry) {
        int calibrated = ctx.rank(owner, "calibrated");
        int multishot = ctx.rank(owner, "multishot");
        int rangeFinder = ctx.rank(owner, "range_finder");
        int teslaCoil = ctx.rank(owner, "tesla_coil");
        int frostRounds = ctx.rank(owner, "frost_rounds");
        int repairDrone = ctx.rank(owner, "repair_drone");
        int mortar = ctx.rank(owner, "mortar");
        double range = BASE_RANGE + rangeFinder * 3;
        double damage = BASE_DAMAGE * (1 + calibrated * 0.10);
        boolean doubleSpeed = overdrive.getOrDefault(owner.getUniqueId(), false);
        long period = doubleSpeed ? 10L : 20L;
        return ctx.repeat(new Runnable() {
            int second;

            @Override
            public void run() {
                if (!sentry.isValid() || !owner.isOnline()) return;
                second++;
                LivingEntity target = nearestHostile(sentry.getLocation(), range);
                if (target != null) {
                    int shots = 1 + multishot;
                    for (int i = 0; i < shots; i++) fireArrow(owner, sentry.getLocation(), target, damage, frostRounds);
                }
                if (teslaCoil > 0 && second % 3 == 0 && target != null) {
                    teslaZap(owner, target, teslaCoil);
                }
                if (mortar > 0 && second % 5 == 0 && target != null) {
                    ctx.damage(target, (mortar + 1) * 2, owner);
                    for (LivingEntity near : nearbyHostiles(target.getLocation(), 2)) {
                        if (near != target) ctx.damage(near, (mortar + 1) * 2, owner);
                    }
                    ctx.fx().burst(target.getLocation(), Particle.EXPLOSION, 1, 0.1);
                }
                if (repairDrone > 0 && second % 5 == 0 && owner.getLocation().distance(sentry.getLocation()) <= 6) {
                    double heal = repairDrone == 1 ? 1 : 2;
                    org.bukkit.attribute.AttributeInstance max = owner.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double cap = max == null ? 20 : max.getValue();
                    owner.setHealth(Math.min(cap, owner.getHealth() + heal));
                }
            }
        }, 20L, period);
    }

    private void fireArrow(Player owner, Location from, LivingEntity target, double damage, int frostRounds) {
        Vector toward = target.getLocation().add(0, target.getHeight() / 2, 0).toVector().subtract(from.toVector());
        Arrow arrow = from.getWorld().spawn(from, Arrow.class, a -> {
            a.setShooter(owner);
            a.setVelocity(toward.normalize().multiply(2.2));
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, owner.getUniqueId().toString());
            a.setDamage(damage);
        });
        if (frostRounds > 0) {
            ctx.later(() -> {
                if (target.isValid() && arrow.getLocation().distance(target.getLocation()) < 2) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, frostRounds * 20, 0));
                }
            }, 10L);
        }
    }

    private void teslaZap(Player owner, LivingEntity first, int chain) {
        ctx.damage(first, 2, owner);
        ctx.fx().line(first.getLocation(), first.getLocation().add(0, 2, 0), Particle.ELECTRIC_SPARK);
        // nearbyHostiles' Stream#toList() is immutable, and it refuses List#sort unconditionally
        // (even on an empty list), so this used to crash the sentry's tick every time Tesla Coil
        // found a target: copy into a mutable list first.
        List<LivingEntity> nearby = new java.util.ArrayList<>(nearbyHostiles(first.getLocation(), 6));
        nearby.sort(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(first.getLocation())));
        for (int i = 0; i < Math.min(chain, nearby.size()); i++) {
            ctx.damage(nearby.get(i), 2, owner);
        }
    }

    private LivingEntity nearestHostile(Location at, double range) {
        return at.getNearbyEntitiesByType(LivingEntity.class, range).stream()
                .filter(Mobs::hostile)
                .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(at)))
                .orElse(null);
    }

    private List<LivingEntity> nearbyHostiles(Location at, double range) {
        return at.getNearbyEntitiesByType(LivingEntity.class, range).stream().filter(Mobs::hostile).toList();
    }

    /** True if {@code e} is one of this kit's sentry arrows, fired by {@code owner}. */
    boolean isSentryArrow(Entity e, UUID owner) {
        if (!(e instanceof Arrow)) return false;
        String tagged = e.getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING);
        return tagged != null && (owner == null || tagged.equals(owner.toString()));
    }

    /** A sentry arrow never hurts a player, even if one walks into its path. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onArrowHitsPlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && isSentryArrow(event.getDamager(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSentryArrowKill(EntityDeathEvent event) {
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        if (!(last.getDamager() instanceof Arrow arrow)) return;
        String owner = arrow.getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING);
        if (owner == null) return;
        Player player = ctx.plugin().getServer().getPlayer(UUID.fromString(owner));
        if (player != null && ctx.active(player)) onKill.accept(player);
    }

    void forget(UUID player) {
        BukkitTask t = ticks.remove(player);
        if (t != null) t.cancel();
        active.remove(player);
        overdrive.remove(player);
    }
}

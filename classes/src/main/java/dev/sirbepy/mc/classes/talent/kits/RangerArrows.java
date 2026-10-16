package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Sharpshooter branch, Volley, Arrow Rain and Railshot, plus the arrow state the other two
 * branches read too: which arrows are fully charged, which are temporary extras (Volley's fan,
 * Ricochet) that may never hit a player, and which mobs are currently rooted (for Netting).
 * Arrow Rain lands on the first solid block you look at ({@link #lookedAt}); Railshot's beam passes
 * through blocks and goes through ctx.damage, so armour still applies to it.
 */
final class RangerArrows implements Listener {

    private static final long CAGE_SHOT_WINDOW_TICKS = 100; // 5 s, same window as Volley
    private static final long DEADEYE_WINDOW_MILLIS = 3_000;
    private static final long STILL_MILLIS = 1_000;

    private final KitContext ctx;
    private final NamespacedKey chargedKey;
    private final NamespacedKey mobOnlyKey;
    private final NamespacedKey cageShotKey;
    private final NamespacedKey caltropArrowKey;

    private final Set<UUID> volleyArmed = new HashSet<>();
    private final Set<UUID> cageShotArmed = new HashSet<>();
    private final Map<UUID, Long> rootedUntil = new HashMap<>();
    private final Map<UUID, Long> eagleEyeGlow = new HashMap<>();
    private final Map<String, Integer> deadeyeStacks = new HashMap<>();
    private final Map<String, Long> deadeyeLast = new HashMap<>();
    private final Map<UUID, Long> stillSince = new HashMap<>();

    private final Ability volley;
    private final Ability arrowRain;
    private final Ability railshot;

    RangerArrows(KitContext ctx) {
        this.ctx = ctx;
        this.chargedKey = new NamespacedKey(ctx.plugin(), "ranger_charged");
        this.mobOnlyKey = new NamespacedKey(ctx.plugin(), "ranger_mob_only_arrow");
        this.cageShotKey = new NamespacedKey(ctx.plugin(), "ranger_cage_shot");
        this.caltropArrowKey = new NamespacedKey(ctx.plugin(), "ranger_caltrop_arrow");
        this.volley = SimpleAbility.of("Volley", 45,
                p -> "the next bow shot or trident throw within 5 s fires 5 arrows in a fan",
                p -> {
                    volleyArmed.add(p.getUniqueId());
                    ctx.later(() -> volleyArmed.remove(p.getUniqueId()), CAGE_SHOT_WINDOW_TICKS);
                    return true;
                });
        this.arrowRain = SimpleAbility.ultimate("Arrow Rain",
                p -> "arrows fall on a 5-block circle where you look for 6 s, 1.5 hearts per arrow to mobs",
                this::castArrowRain);
        this.railshot = SimpleAbility.of("Railshot", 45,
                p -> "a beam along a 40-block line, 6 hearts to every mob on it",
                this::castRailshot);
    }

    Ability volley() {
        return volley;
    }

    Ability arrowRain() {
        return arrowRain;
    }

    Ability railshot() {
        return railshot;
    }

    /** Arms the next bow shot or trident throw to carry a Cage Shot root, see {@link #consumeArmedShot}. Called by RangerSnares. */
    void armCageShot(Player player) {
        cageShotArmed.add(player.getUniqueId());
        ctx.later(() -> cageShotArmed.remove(player.getUniqueId()), CAGE_SHOT_WINDOW_TICKS);
    }

    /** Whether {@code mobId} is currently rooted (Snare, Bola, Cage Shot...), for Netting and Survivalist. */
    boolean isRooted(UUID mobId) {
        Long until = rootedUntil.get(mobId);
        return until != null && until > ctx.now();
    }

    /** Roots {@code mob} for {@code ticks} (never a boss), zeroing its velocity like every root. */
    void rootMob(LivingEntity mob, long ticks) {
        if (!mob.isValid() || Mobs.boss(mob)) return;
        mob.setVelocity(new Vector(0, 0, 0));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) ticks, 9));
        rootedUntil.put(mob.getUniqueId(), ctx.now() + ticks * 50);
        ctx.fx().burst(mob, Particle.ITEM_SNOWBALL, 10, 0.3);
    }

    void enable() {
        ctx.listen(this);
    }

    // Shooting: tag charged shots, Volley's fan, Cage Shot's arrow, piercing and wind-reading.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        boolean charged = event.getForce() >= 0.99f;
        if (charged) arrow.getPersistentDataContainer().set(chargedKey, PersistentDataType.BOOLEAN, true);

        applyLaunchTalents(player, arrow, charged);
    }

    /** Piercing Shot (charged only), Wind Reader, then any armed Cage Shot or Volley. */
    private void applyLaunchTalents(Player player, AbstractArrow projectile, boolean charged) {
        int pierce = ctx.rank(player, "piercing_shot");
        if (charged && pierce > 0) projectile.setPierceLevel(Math.max(projectile.getPierceLevel(), pierce));

        int wind = ctx.rank(player, "wind_reader");
        if (wind > 0) projectile.setVelocity(projectile.getVelocity().multiply(1 + 0.1 * wind));

        consumeArmedShot(player, projectile);
    }

    /** Hands an armed Cage Shot or Volley to this bow shot or trident throw. */
    private void consumeArmedShot(Player player, AbstractArrow projectile) {
        if (cageShotArmed.remove(player.getUniqueId())) {
            projectile.getPersistentDataContainer().set(cageShotKey, PersistentDataType.BOOLEAN, true);
            return; // One ability arms a shot at a time; Cage Shot takes priority over a stale Volley arm.
        }
        if (volleyArmed.remove(player.getUniqueId())) {
            fireVolleyFan(player, projectile);
        }
    }

    /**
     * A trident has no draw, so every throw counts as a fully charged shot: Long Shot, Eagle Eye,
     * Marked and Piercing Shot all apply. Tridents never fire EntityShootBowEvent, so the launch-time
     * nodes (pierce, Wind Reader, Volley, Cage Shot) are applied here instead of in {@link #onShoot}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrowTrident(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player) || !ctx.active(player)) return;
        trident.getPersistentDataContainer().set(chargedKey, PersistentDataType.BOOLEAN, true);
        applyLaunchTalents(player, trident, true);
    }

    /** 4 extra arrows in a fan alongside the real one: mob-only, never picked up, tagged for Caltrops. */
    private void fireVolleyFan(Player player, AbstractArrow real) {
        Location at = real.getLocation();
        Vector base = real.getVelocity();
        int caltropsRank = ctx.rank(player, "caltrops");
        for (double degrees : new double[] {-6, -3, 3, 6}) {
            Vector v = base.clone().rotateAroundY(Math.toRadians(degrees));
            at.getWorld().spawn(at, Arrow.class, a -> {
                a.setShooter(player);
                a.setVelocity(v);
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                a.getPersistentDataContainer().set(mobOnlyKey, PersistentDataType.BOOLEAN, true);
                if (caltropsRank > 0) a.getPersistentDataContainer().set(caltropArrowKey, PersistentDataType.BOOLEAN, true);
            });
        }
        ctx.fx().sound(at, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.6f);
    }

    // Landing: Caltrops and Cage Shot both trigger off where an arrow comes down.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        var pdc = arrow.getPersistentDataContainer();
        if (Boolean.TRUE.equals(pdc.get(cageShotKey, PersistentDataType.BOOLEAN))) {
            for (LivingEntity mob : nearbyHostiles(arrow.getLocation(), 4)) rootMob(mob, 80);
            ctx.fx().ring(arrow.getLocation(), Particle.CRIT, 4, 16);
            ctx.fx().sound(arrow.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        } else if (event.getHitEntity() == null && Boolean.TRUE.equals(pdc.get(caltropArrowKey, PersistentDataType.BOOLEAN))) {
            int rank = caltropsRankOf(arrow);
            if (rank > 0) placeCaltrops(arrow.getLocation(), rank);
        }
    }

    /** The arrow's shooter's current Caltrops rank (ranks never leak across classes, see KitContext#rank). */
    private int caltropsRankOf(AbstractArrow arrow) {
        return arrow.getShooter() instanceof Player shooter ? ctx.rank(shooter, "caltrops") : 0;
    }

    /** A patch of caltrops at {@code at}: the first mob to step within a block is slowed, once, within 10 s. */
    private void placeCaltrops(Location at, int rank) {
        int durationTicks = (rank == 1 ? 3 : 5) * 20;
        int[] ticksLeft = {200};
        org.bukkit.scheduler.BukkitTask[] self = new org.bukkit.scheduler.BukkitTask[1];
        self[0] = ctx.repeat(() -> {
            ticksLeft[0] -= 5;
            for (LivingEntity mob : nearbyHostiles(at, 1.0)) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 1));
                ctx.fx().burst(at, Particle.CRIT, 6, 0.2);
                self[0].cancel();
                return;
            }
            if (ticksLeft[0] <= 0) self[0].cancel();
        }, 5, 5);
    }

    private Iterable<LivingEntity> nearbyHostiles(Location at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : at.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity living && Mobs.hostile(living)) out.add(living);
        }
        return out;
    }

    // Damage: every Sharpshooter and Trapper percentage, plus the resource gain.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player) || !ctx.active(player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || !Mobs.hostile(victim)) return;

        var pdc = arrow.getPersistentDataContainer();
        boolean charged = Boolean.TRUE.equals(pdc.get(chargedKey, PersistentDataType.BOOLEAN));
        boolean wasFullHealth = isFullHealth(victim);
        double percent = 0;

        int longShot = ctx.rank(player, "long_shot");
        if (charged && longShot > 0 && player.getLocation().distance(victim.getLocation()) > 15) {
            percent += 4 * longShot;
        }
        int calledShot = ctx.rank(player, "called_shot");
        if (calledShot > 0 && wasFullHealth) percent += 10 * calledShot;

        int deadeye = ctx.rank(player, "deadeye");
        if (deadeye > 0) percent += deadeyeStack(player, victim) * 5;

        if (ctx.rank(player, "marked") > 0 && eagleEyeGlow.getOrDefault(victim.getUniqueId(), 0L) > ctx.now()) {
            percent += 10;
        }
        int netting = ctx.rank(player, "netting");
        if (netting > 0 && isRooted(victim.getUniqueId())) percent += 10 * netting;

        if (ctx.rank(player, "snipers_nest") > 0 && player.isSneaking()
                && ctx.now() - stillSince.computeIfAbsent(player.getUniqueId(), k -> ctx.now()) >= STILL_MILLIS) {
            percent += 15;
        }

        if (percent != 0) event.setDamage(Math.max(0, event.getDamage() * (1 + percent / 100)));

        int eagleEye = ctx.rank(player, "eagle_eye");
        if (charged && eagleEye > 0) markGlowing(victim, 5L * eagleEye);

        int barbed = ctx.rank(player, "barbed_arrows");
        if (barbed > 0) victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 * barbed, 0));

        int bola = ctx.rank(player, "bola");
        if (bola > 0 && ctx.random() < 0.05 * bola) rootMob(victim, 20);

        int kiting = ctx.rank(player, "kiting");
        if (kiting > 0 && player.getLocation().distance(victim.getLocation()) <= 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40 * kiting, 0));
        }

        int recovery = ctx.rank(player, "recovery");
        // A trident is never used up, so there is nothing for Recovery to give back.
        if (recovery > 0 && !(arrow instanceof Trident) && ctx.random() < 0.2 * recovery) {
            player.getInventory().addItem(new ItemStack(Material.ARROW));
        }

        ctx.gain(player, 5);
        if (charged) ctx.gain(player, 10);
    }

    private void markGlowing(LivingEntity victim, long seconds) {
        victim.setGlowing(true);
        long until = ctx.now() + seconds * 1000;
        eagleEyeGlow.put(victim.getUniqueId(), until);
        ctx.later(() -> {
            if (eagleEyeGlow.getOrDefault(victim.getUniqueId(), 0L) <= ctx.now() && victim.isValid()) {
                victim.setGlowing(false);
            }
        }, seconds * 20);
    }

    private boolean isFullHealth(LivingEntity victim) {
        AttributeInstance max = victim.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? victim.getHealth() : max.getValue();
        return victim.getHealth() >= cap;
    }

    private int deadeyeStack(Player player, LivingEntity victim) {
        String key = player.getUniqueId() + "|" + victim.getUniqueId();
        long now = ctx.now();
        Long last = deadeyeLast.get(key);
        int maxStacks = ctx.rank(player, "deadeye") + 2;
        int stack = last != null && now - last <= DEADEYE_WINDOW_MILLIS
                ? Math.min(maxStacks, deadeyeStacks.getOrDefault(key, 1) + 1) : 1;
        deadeyeStacks.put(key, stack);
        deadeyeLast.put(key, now);
        return stack - 1;
    }

    // Ricochet: a kill fires a new arrow at the nearest mob within range.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        if (!(last.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player player)) return;
        if (!ctx.active(player)) return;
        int rank = ctx.rank(player, "ricochet");
        if (rank == 0) return;
        double radius = rank == 1 ? 6 : 10;
        LivingEntity target = nearest(dead.getLocation(), radius);
        if (target == null) return;
        Vector dir = target.getLocation().toVector().subtract(dead.getLocation().toVector()).normalize();
        dead.getWorld().spawn(dead.getLocation(), Arrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(dir.multiply(2));
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.getPersistentDataContainer().set(mobOnlyKey, PersistentDataType.BOOLEAN, true);
        });
        ctx.fx().line(dead.getLocation(), target.getLocation(), Particle.CRIT);
    }

    /**
     * Homecoming: a ranged kill's drops go into the shooter's inventory (overflow at their feet) and
     * its XP straight onto their bar, instead of landing where the mob died. HIGHEST, not MONITOR,
     * because it edits the drop list; other plugins' drop changes at HIGH and below are kept.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHomecoming(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        if (!(last.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player player)) return;
        if (ctx.rank(player, "homecoming") == 0 || !player.isOnline()) return;
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) continue;
            for (ItemStack overflow : player.getInventory().addItem(drop).values()) {
                player.getWorld().dropItem(player.getLocation(), overflow);
            }
        }
        event.getDrops().clear();
        if (event.getDroppedExp() > 0) {
            player.giveExp(event.getDroppedExp());
            event.setDroppedExp(0);
        }
    }

    private LivingEntity nearest(Location at, double radius) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : at.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity living && Mobs.hostile(living)) {
                double d = living.getLocation().distanceSquared(at);
                if (d < bestDist) {
                    bestDist = d;
                    best = living;
                }
            }
        }
        return best;
    }

    // Mob-only tagged arrows (Volley extras, Ricochet) must never hurt a player.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobOnlyArrowHitsPlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof AbstractArrow arrow
                && Boolean.TRUE.equals(arrow.getPersistentDataContainer().get(mobOnlyKey, PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        volleyArmed.remove(id);
        cageShotArmed.remove(id);
        stillSince.remove(id);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().distanceSquared(event.getTo()) > 0.0004) {
            stillSince.put(event.getPlayer().getUniqueId(), ctx.now());
        }
    }

    // Ultimate and keystone.

    private boolean castArrowRain(Player player) {
        Location center = lookedAt(player, 40);
        var task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = ctx.repeat(() -> {
            double angle = ctx.random() * Math.PI * 2;
            double dist = ctx.random() * 5;
            Location drop = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            for (LivingEntity mob : nearbyHostiles(drop, 1.5)) ctx.damage(mob, 3, player);
            ctx.fx().burst(drop.clone().add(0, 3, 0), Particle.ITEM_SNOWBALL, 6, 0.2);
            ctx.fx().sound(drop, Sound.ENTITY_ARROW_HIT, 0.5f, 1.2f);
        }, 10, 10);
        ctx.later(() -> task[0].cancel(), 6L * 20);
        return true;
    }

    /**
     * The first solid block along the look direction within {@code range}, or the point at full
     * range. A manual walk instead of rayTraceBlocks, which MockBukkit doesn't implement.
     */
    static Location lookedAt(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 1; d <= range; d += 0.5) {
            Location at = eye.clone().add(dir.clone().multiply(d));
            if (at.getBlock().getType().isSolid()) return at.add(0, 1, 0);
        }
        return eye.add(dir.multiply(range));
    }

    private boolean castRailshot(Player player) {
        Location from = player.getEyeLocation();
        Vector dir = from.getDirection().normalize();
        boolean hitAny = false;
        for (Entity e : from.getNearbyEntities(40, 40, 40)) {
            if (!(e instanceof LivingEntity mob) || !Mobs.hostile(mob)) continue;
            Vector toMob = mob.getLocation().toVector().subtract(from.toVector());
            double along = toMob.dot(dir);
            if (along < 0 || along > 40) continue;
            double perpendicular = toMob.subtract(dir.clone().multiply(along)).length();
            if (perpendicular > 1.5) continue;
            // Through ctx.damage, not setHealth: the kill is credited and the dragon's death sequence
            // still runs, at the cost of armour applying.
            ctx.damage(mob, 12, player);
            hitAny = true;
        }
        ctx.fx().line(from, from.clone().add(dir.multiply(40)), Particle.END_ROD);
        ctx.fx().sound(from, Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
        return hitAny;
    }
}

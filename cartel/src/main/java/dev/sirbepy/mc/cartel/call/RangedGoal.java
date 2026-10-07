package dev.sirbepy.mc.cartel.call;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Random;
import java.util.function.Predicate;

/**
 * A cousin's crossbow, in place of the zombie's melee goal (removed on attach): while he has a
 * target he keeps his distance and fires bolts on {@link Marksman}'s drill. The plugin fires the
 * bolts itself, a zombie having no ranged attack of its own, so the crossbow's enchantments are
 * applied here by hand (see {@link #load}). Who a bolt may hurt is {@link CousinListener}'s job.
 */
public final class RangedGoal implements Goal<Mob> {

    /** Above every zombie goal that moves it (its melee goal was priority 2). */
    private static final int PRIORITY = 1;
    private static final int REPATH_TICKS = 10;
    /** A pillager's crossbow bolt speed; a player's crossbow fires at 3.15. */
    static final double BOLT_SPEED = 1.6;
    /** An arrow's base damage before Power. */
    static final double ARROW_DAMAGE = 2.0;
    /** Power V, +150%: vanilla's +1 base damage and +0.5 per level above I, on a 2-damage arrow. */
    static final double POWER_V = 2.5;
    static final int PIERCING = 4;
    static final String SHOT_SOUND = "claudesmp:pollos_gun.shot";

    private final GoalKey<Mob> key;
    private final Mob cousin;
    private final Predicate<Entity> friendly;
    private final Marksman marksman;
    private int repath;

    private RangedGoal(Plugin plugin, Mob cousin, Predicate<Entity> friendly, Random random) {
        this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "crossbow"));
        this.cousin = cousin;
        this.friendly = friendly;
        this.marksman = new Marksman(() -> random.nextInt(Marksman.AIM_SPREAD));
    }

    /** {@code friendly}: whoever a bolt must never be fired through (the other cousin, the boss). */
    public static void attach(Plugin plugin, Mob cousin, Predicate<Entity> friendly, Random random) {
        MobGoals goals = plugin.getServer().getMobGoals();
        if (cousin instanceof Zombie zombie) {
            goals.removeGoal(zombie, VanillaGoal.ZOMBIE_ATTACK);
        }
        goals.addGoal(cousin, PRIORITY, new RangedGoal(plugin, cousin, friendly, random));
    }

    @Override
    public boolean shouldActivate() {
        LivingEntity target = cousin.getTarget();
        return target != null && target.isValid() && target.getWorld().equals(cousin.getWorld());
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        cousin.getPathfinder().stopPathfinding();
    }

    @Override
    public void tick() {
        LivingEntity target = cousin.getTarget();
        if (target == null) {
            return;
        }
        cousin.lookAt(target);
        Location at = cousin.getLocation();
        Location there = target.getLocation();
        double horizontal = Math.hypot(at.getX() - there.getX(), at.getZ() - there.getZ());
        boolean clear = clearShot(cousin, target, friendly);
        if (--repath <= 0) {
            repath = REPATH_TICKS;
            switch (marksman.move(horizontal, clear)) {
                case CLOSE_IN -> cousin.getPathfinder().moveTo(target, 1.0);
                case HOLD -> cousin.getPathfinder().stopPathfinding();
                case BACK_OFF -> backOff(target);
            }
        }
        if (marksman.tick(Bukkit.getCurrentTick(), at.distance(there), clear)) {
            fire(cousin, target);
        }
    }

    /** To {@link Marksman#HOLD} blocks from the target, straight away from them (off a pillar's base). */
    private void backOff(LivingEntity target) {
        Location at = cousin.getLocation();
        Vector away = at.toVector().subtract(target.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }
        Location spot = target.getLocation().add(away.normalize().multiply(Marksman.HOLD));
        SpawnSpots.standable(at.getWorld(), spot.getBlockX(), at.getBlockY(), spot.getBlockZ())
                .ifPresent(standable -> cousin.getPathfinder().moveTo(standable, 1.0));
    }

    private static Location muzzle(Mob shooter) {
        return shooter.getEyeLocation().subtract(0, 0.1, 0);
    }

    private static Location aim(LivingEntity target) {
        return target.getLocation().add(0, target.getHeight() / 2, 0);
    }

    /** No block, and no one {@code friendly}, between the muzzle and the target's chest. */
    static boolean clearShot(Mob shooter, LivingEntity target, Predicate<Entity> friendly) {
        Location from = muzzle(shooter);
        Vector to = aim(target).toVector().subtract(from.toVector());
        double length = to.length();
        if (length < 0.01) {
            return true;
        }
        if (shooter.getWorld().rayTraceBlocks(from, to, length, FluidCollisionMode.NEVER, true) != null) {
            return false;
        }
        return shooter.getWorld().rayTraceEntities(from, to, length, 0.3,
                e -> !e.equals(shooter) && friendly.test(e)) == null;
    }

    /** A bolt at the target's chest, lifted for the drop the way a pillager lifts its aim. */
    static void fire(Mob shooter, LivingEntity target) {
        Location from = muzzle(shooter);
        Vector to = aim(target).toVector().subtract(from.toVector());
        double flat = Math.hypot(to.getX(), to.getZ());
        Vector velocity = to.setY(to.getY() + flat * 0.2).normalize().multiply(BOLT_SPEED);
        shooter.launchProjectile(Arrow.class, velocity, RangedGoal::load);
        // Volume 3 carries 48 blocks, like the Los Pollos Hermanos gun this shot comes from.
        shooter.getWorld().playSound(shooter.getLocation(), SHOT_SOUND, SoundCategory.HOSTILE, 3.0f, 1.0f);
    }

    /**
     * The crossbow's enchantments on a plugin-fired bolt. Paper launches it with no weapon, so none
     * apply by themselves: Power V multiplies the base damage (vanilla scales it by speed at the hit),
     * Piercing IV passes through 4 entities, and a crossbow bolt is always critical.
     */
    static void load(AbstractArrow bolt) {
        bolt.setDamage(ARROW_DAMAGE * POWER_V);
        bolt.setPierceLevel(PIERCING);
        bolt.setCritical(true);
        bolt.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        bolt.setFireTicks(0);
    }

    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }
}

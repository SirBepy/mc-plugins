package dev.sirbepy.mc.cartel.call;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Marco Salamanca's pistol in Assassinate Mode, so a target can't wait out a hit on a 3-block
 * pillar (the dragon's council, 2026-10-07). Only once the target has had no melee path for
 * {@link #DRAW_AFTER_MILLIS} does Marco swap his sword for a bow; the first check that finds a path
 * again puts the sword back. With the pistol out he keeps his distance ({@link StandOff}) and only
 * fires when nothing blocks the shot. Shots are capped by {@link CousinListener} at
 * {@link #MAX_DAMAGE}, with no knockback and no fire. One instance per call, steered from
 * {@link CallManager#tick}.
 */
public final class Sidearm {

    @FunctionalInterface
    public interface Trigger {
        void fire(Mob shooter, LivingEntity target);
    }

    static final long DRAW_AFTER_MILLIS = 3_000;
    static final long SHOT_EVERY_MILLIS = 1_500;
    /** A skeleton's own bow range: past it a cornered target is walked to, not shot at. */
    static final double RANGE = 16;
    public static final double MAX_DAMAGE = 3;
    static final String SHOT_SOUND = "claudesmp:pollos_gun.shot";
    /** A skeleton's arrow speed. */
    private static final double ARROW_SPEED = 1.6;

    private final BiPredicate<Mob, LivingEntity> meleePath;
    private final BiPredicate<Mob, LivingEntity> sight;
    private final Trigger trigger;
    private final ItemStack sword;
    private final ItemStack pistol;
    private final BiConsumer<Mob, BooleanSupplier> standOff;
    private boolean standOffAttached;
    private long cornerSince = -1;
    private long lastShot = -1;
    private boolean drawn;

    /**
     * {@code standOff} is handed Marco and this sidearm's {@link #drawn} the first time he draws, to
     * keep him at range while it is true.
     */
    public Sidearm(BiPredicate<Mob, LivingEntity> meleePath, BiPredicate<Mob, LivingEntity> sight, Trigger trigger,
                   ItemStack sword, ItemStack pistol, BiConsumer<Mob, BooleanSupplier> standOff) {
        this.meleePath = meleePath;
        this.sight = sight;
        this.trigger = trigger;
        this.sword = sword;
        this.pistol = pistol;
        this.standOff = standOff;
    }

    /** The real thing: Paper's pathfinder, block ray traces, arrows and {@link StandOff}. */
    public static Sidearm forMarco(Plugin plugin) {
        return new Sidearm(Sidearm::pathfinderReach, Sidearm::clearShot, Sidearm::fire, Cousins.sword(), Cousins.pistol(),
                (marco, drawn) -> StandOff.attach(plugin, marco, drawn));
    }

    public boolean drawn() {
        return drawn;
    }

    public void steer(Mob marco, LivingEntity target, long now) {
        boolean cornered = marco.getWorld().equals(target.getWorld())
                && marco.getLocation().distanceSquared(target.getLocation()) <= RANGE * RANGE
                && !meleePath.test(marco, target);
        if (!cornered) {
            cornerSince = -1;
            if (drawn) {
                drawn = false;
                marco.getEquipment().setItemInMainHand(sword.clone());
            }
            return;
        }
        if (cornerSince < 0) {
            cornerSince = now;
        }
        if (!drawn && now - cornerSince >= DRAW_AFTER_MILLIS) {
            drawn = true;
            marco.getEquipment().setItemInMainHand(pistol.clone());
            if (!standOffAttached) {
                standOffAttached = true;
                standOff.accept(marco, this::drawn);
            }
        }
        if (drawn && (lastShot < 0 || now - lastShot >= SHOT_EVERY_MILLIS) && sight.test(marco, target)) {
            lastShot = now;
            trigger.fire(marco, target);
        }
    }

    /** Whether vanilla pathfinding can bring {@code cousin} all the way to {@code target}. */
    public static boolean pathfinderReach(Mob cousin, LivingEntity target) {
        Pathfinder.PathResult path = cousin.getPathfinder().findPath(target);
        return path != null && path.canReachFinalPoint();
    }

    private static Location muzzle(Mob shooter) {
        return shooter.getEyeLocation().subtract(0, 0.1, 0);
    }

    private static Location aim(LivingEntity target) {
        return target.getLocation().add(0, target.getHeight() / 2, 0);
    }

    /** No block between the muzzle and the target's chest (the pillar's own blocks, from below). */
    public static boolean clearShot(Mob shooter, LivingEntity target) {
        Location from = muzzle(shooter);
        Vector to = aim(target).toVector().subtract(from.toVector());
        return shooter.getWorld().rayTraceBlocks(from, to, to.length(), FluidCollisionMode.NEVER, true) == null;
    }

    /** A plain arrow at the target's chest, lifted for the drop the way a skeleton lifts its aim. */
    public static void fire(Mob shooter, LivingEntity target) {
        Location from = muzzle(shooter);
        Vector to = aim(target).toVector().subtract(from.toVector());
        double flat = Math.hypot(to.getX(), to.getZ());
        Vector velocity = to.setY(to.getY() + flat * 0.2).normalize().multiply(ARROW_SPEED);
        shooter.launchProjectile(Arrow.class, velocity, arrow -> {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setCritical(false);
            arrow.setFireTicks(0);
        });
        // Volume 3 carries 48 blocks, like the Los Pollos Hermanos gun this shot comes from.
        shooter.getWorld().playSound(shooter.getLocation(), SHOT_SOUND, SoundCategory.HOSTILE, 3.0f, 1.0f);
    }
}

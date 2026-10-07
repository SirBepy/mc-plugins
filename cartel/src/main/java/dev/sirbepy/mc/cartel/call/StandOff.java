package dev.sirbepy.mc.cartel.call;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * While Marco's pistol is out, he backs off to {@link #DISTANCE} blocks from his target instead of
 * hugging the pillar, whose own blocks would stop every shot fired from its base. Runs above the
 * zombie's melee goal (priority 2), so it holds the movement and look controls until the pistol is
 * put away; the target itself never changes.
 */
public final class StandOff implements Goal<Mob> {

    static final int DISTANCE = 5;
    private static final int MOVE_PRIORITY = 1;
    private static final int REPATH_TICKS = 10;

    private final GoalKey<Mob> key;
    private final Mob marco;
    private final BooleanSupplier drawn;
    private int cooldown;

    private StandOff(Plugin plugin, Mob marco, BooleanSupplier drawn) {
        this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "pistol_stand_off"));
        this.marco = marco;
        this.drawn = drawn;
    }

    public static void attach(Plugin plugin, Mob marco, BooleanSupplier drawn) {
        plugin.getServer().getMobGoals().addGoal(marco, MOVE_PRIORITY, new StandOff(plugin, marco, drawn));
    }

    @Override
    public boolean shouldActivate() {
        return drawn.getAsBoolean() && marco.getTarget() != null;
    }

    @Override
    public void start() {
        cooldown = 0;
    }

    @Override
    public void stop() {
        marco.getPathfinder().stopPathfinding();
    }

    @Override
    public void tick() {
        LivingEntity target = marco.getTarget();
        if (target == null || !target.getWorld().equals(marco.getWorld())) {
            return;
        }
        marco.lookAt(target);
        if (--cooldown > 0) {
            return;
        }
        cooldown = REPATH_TICKS;
        Location at = marco.getLocation();
        Vector away = at.toVector().subtract(target.getLocation().toVector()).setY(0);
        if (away.lengthSquared() >= (DISTANCE - 1) * (DISTANCE - 1)) {
            marco.getPathfinder().stopPathfinding();
            return;
        }
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }
        Location spot = target.getLocation().add(away.normalize().multiply(DISTANCE));
        SpawnSpots.standable(at.getWorld(), spot.getBlockX(), at.getBlockY(), spot.getBlockZ())
                .ifPresent(standable -> marco.getPathfinder().moveTo(standable, 1.0));
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

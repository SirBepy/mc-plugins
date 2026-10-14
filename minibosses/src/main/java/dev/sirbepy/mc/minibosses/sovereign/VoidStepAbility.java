package dev.sirbepy.mc.minibosses.sovereign;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Void Sovereign's Normal-tier mechanic: once triggered (by {@code SovereignFight}, the first
 * time the boss's projected health crosses half its max, the same threshold convention
 * {@code tyrant.BlazeSummonAbility}/{@code sentinel.RootAbility} established), it blinks itself to
 * a short random offset near a randomly chosen nearby fighter every {@link #STEP_INTERVAL_TICKS},
 * for the rest of the fight - repositioning is this tier's whole mechanic (per the todo: "baseline
 * melee + periodic short-range teleport repositioning"), not a bounded burst like the other two
 * Guardians' Normal abilities, so this keeps running until the boss dies rather than firing a
 * fixed number of pulses. No player displacement here - that is explicitly Heroic/Mythic
 * territory per the todo's own notes.
 *
 * <p>An {@link org.bukkit.entity.Enderman} already teleports on its own in vanilla (fleeing
 * damage, or a random idle blink); this ability does not suppress that, it only adds a second,
 * deliberate "step to a fighter" blink on top - the same "do not fight the vanilla mob's own
 * behavior" judgment {@code tyrant.TyrantFight} applied to a Wither Skeleton's natural Wither
 * effect, both read as "the boss is doing boss things", not a bug.
 */
public final class VoidStepAbility implements GuardianAbility {

    public static final String ID = "void_sovereign_void_step";

    static final long STEP_INTERVAL_TICKS = 100L; // 5 seconds
    static final double LANDING_OFFSET = 3.0;
    // Matches SovereignFight.spawn's own "close enough to see the boss bar" radius.
    static final double FIGHTER_SEARCH_RADIUS = 64.0;

    private final JavaPlugin plugin;
    private final Random random;

    public VoidStepAbility(JavaPlugin plugin, Random random) {
        this.plugin = plugin;
        this.random = random;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (boss.isDead() || !boss.isValid()) {
                    cancel();
                    return;
                }
                stepToRandomFighter(boss);
            }
        }.runTaskTimer(plugin, STEP_INTERVAL_TICKS, STEP_INTERVAL_TICKS);
    }

    private void stepToRandomFighter(LivingEntity boss) {
        List<Player> nearby = new ArrayList<>();
        for (Player player : boss.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(boss.getLocation()) <= FIGHTER_SEARCH_RADIUS * FIGHTER_SEARCH_RADIUS) {
                nearby.add(player);
            }
        }
        if (nearby.isEmpty()) {
            return;
        }
        Player target = nearby.get(random.nextInt(nearby.size()));
        Location targetLocation = target.getLocation();
        double angle = random.nextDouble() * Math.PI * 2;
        double x = targetLocation.getX() + Math.cos(angle) * LANDING_OFFSET;
        double z = targetLocation.getZ() + Math.sin(angle) * LANDING_OFFSET;
        Location bossLocation = boss.getLocation();
        boss.teleport(new Location(targetLocation.getWorld(), x, targetLocation.getY(), z,
                bossLocation.getYaw(), bossLocation.getPitch()));
    }
}

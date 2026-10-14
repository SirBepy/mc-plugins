package dev.sirbepy.mc.minibosses.sovereign;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Void Sovereign's Heroic-tier mechanic: real void-rift zones that damage and displace anyone
 * caught standing in them, distinct from Normal's pure repositioning ({@link VoidStepAbility}) -
 * this is the todo's own first Heroic suggestion verbatim ("void-rift zones that damage/displace
 * players standing in them"), picked over the alternative teleport-swap because a zone hazard
 * gives fighters something to visibly dodge rather than a single instant relocation they cannot
 * react to. {@link #RIFT_COUNT} rift centers are rolled once, scattered around the boss's own
 * position at trigger time, and stay fixed for the ability's whole duration (real zones to play
 * around, not zones that chase the fighter) - fires {@link #CHECKS} times, every
 * {@link #CHECK_INTERVAL_TICKS}; anyone standing within {@link #RIFT_RADIUS} of any rift center at
 * a check takes damage and is displaced (knocked up and outward, away from the rift's own center,
 * simulating being ejected by a void updraft) - the "displace" half of the todo's wording that
 * {@code tyrant.AshenStormAbility}'s pure damage-over-area pulse never attempted. Registered under
 * the id {@code void_sovereign.yml}'s Heroic tier already names ({@code void_sovereign_rift_zones}),
 * resolving what Phase A left as a documented no-op.
 */
public final class RiftZonesAbility implements GuardianAbility {

    public static final String ID = "void_sovereign_rift_zones";

    static final int RIFT_COUNT = 3;
    static final double RIFT_SPREAD = 8.0; // how far from the boss a rift center can roll
    static final double RIFT_RADIUS = 2.5; // a real zone to dodge, not a single point
    static final long CHECK_INTERVAL_TICKS = 40L; // 2 seconds
    static final int CHECKS = 5; // 5 * 2s = 10s of hazard uptime
    static final double DAMAGE = 4.0; // 2 hearts per check caught in a rift, real but not a one-shot
    static final double DISPLACEMENT_UP = 0.6;
    static final double DISPLACEMENT_OUT = 0.8;

    private final JavaPlugin plugin;
    private final Random random;

    public RiftZonesAbility(JavaPlugin plugin, Random random) {
        this.plugin = plugin;
        this.random = random;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        List<Location> rifts = rollRifts(boss.getLocation());
        new BukkitRunnable() {
            int checksLeft = CHECKS;

            @Override
            public void run() {
                if (checksLeft <= 0 || boss.isDead() || !boss.isValid()) {
                    cancel();
                    return;
                }
                checksLeft--;
                for (Player player : boss.getWorld().getPlayers()) {
                    Location rift = nearestRiftWithin(player.getLocation(), rifts);
                    if (rift != null) {
                        damageAndDisplace(player, rift);
                    }
                }
            }
        }.runTaskTimer(plugin, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    private List<Location> rollRifts(Location center) {
        List<Location> rifts = new ArrayList<>();
        for (int i = 0; i < RIFT_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * RIFT_SPREAD;
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            rifts.add(new Location(center.getWorld(), x, center.getY(), z));
        }
        return rifts;
    }

    private Location nearestRiftWithin(Location playerLocation, List<Location> rifts) {
        for (Location rift : rifts) {
            if (rift.getWorld() != null && rift.getWorld().equals(playerLocation.getWorld())
                    && rift.distanceSquared(playerLocation) <= RIFT_RADIUS * RIFT_RADIUS) {
                return rift;
            }
        }
        return null;
    }

    private void damageAndDisplace(Player player, Location rift) {
        player.damage(DAMAGE);
        Vector outward = player.getLocation().toVector().subtract(rift.toVector());
        outward.setY(0);
        if (outward.lengthSquared() < 0.0001) {
            // Standing exactly on the rift center: pick an arbitrary outward direction rather than
            // dividing by zero.
            outward = new Vector(1, 0, 0);
        }
        outward.normalize().multiply(DISPLACEMENT_OUT);
        outward.setY(DISPLACEMENT_UP);
        player.setVelocity(outward);
    }
}

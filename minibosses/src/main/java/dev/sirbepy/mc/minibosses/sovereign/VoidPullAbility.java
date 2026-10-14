package dev.sirbepy.mc.minibosses.sovereign;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The Void Sovereign's Mythic-tier mechanic: an arena-edge void-pull hazard, distinct from
 * Heroic's standing-zone {@link RiftZonesAbility} - rather than reusing either other Guardian's
 * crowd-control idea (the Sentinel's root/vines, the Tyrant's adds/storm), this leans into the
 * Void Sovereign arena's own design brief ("~30x30 flat platform, 15+ block clearance,
 * exposed sky/edges... fall/void-knockback hazards are intentional - lean into that for a
 * signature Mythic mechanic"): every pulse, every fighter within {@link #RADIUS} gets shoved
 * radially outward from the boss's own position at trigger time, with a small downward component -
 * pushing fighters toward the platform's exposed edges, where a missed footing can mean a fall into
 * the void, rather than damaging them directly. This ability deals no damage of its own (no fall
 * damage is applied here either - that is vanilla fall/void damage doing its own job once a
 * fighter actually goes over an edge), so a fighter who holds ground near the center takes a shove
 * and nothing else; the danger is entirely positional, which is the point of an arena built with no
 * walls. Registered under the id {@code void_sovereign.yml}'s Mythic tier already names
 * ({@code void_sovereign_void_pull}), resolving what Phase A left as a documented no-op.
 */
public final class VoidPullAbility implements GuardianAbility {

    public static final String ID = "void_sovereign_void_pull";

    static final double RADIUS = 12.0; // wider than melee range - an arena-wide hazard, not a melee one
    static final long PULSE_INTERVAL_TICKS = 60L; // 3 seconds
    static final int PULSES = 5; // 5 * 3s = 15s of pull uptime
    static final double PULL_STRENGTH = 1.4; // a real shove, enough to cover real ground toward an edge
    static final double DOWNWARD_COMPONENT = -0.15; // a slight downward push, toward a possible fall

    private final JavaPlugin plugin;

    public VoidPullAbility(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        Location center = boss.getLocation().clone();
        new BukkitRunnable() {
            int pulsesLeft = PULSES;

            @Override
            public void run() {
                if (pulsesLeft <= 0 || boss.isDead() || !boss.isValid()) {
                    cancel();
                    return;
                }
                pulsesLeft--;
                for (var entity : boss.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
                    if (entity instanceof Player player) {
                        pull(player, center);
                    }
                }
            }
        }.runTaskTimer(plugin, PULSE_INTERVAL_TICKS, PULSE_INTERVAL_TICKS);
    }

    private void pull(Player player, Location center) {
        Vector outward = player.getLocation().toVector().subtract(center.toVector());
        outward.setY(0);
        if (outward.lengthSquared() < 0.0001) {
            // Standing exactly on the trigger-time center: pick an arbitrary outward direction
            // rather than dividing by zero.
            outward = new Vector(1, 0, 0);
        }
        outward.normalize().multiply(PULL_STRENGTH);
        outward.setY(DOWNWARD_COMPONENT);
        player.setVelocity(outward);
    }
}

package dev.sirbepy.mc.tag.task;

import dev.sirbepy.mc.tag.TagService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Samples each online player's position/facing on a timer instead of hooking PlayerMoveEvent,
 * which fires on every sub-pixel step/camera twitch and is far too noisy to use as an idle
 * signal directly. Also drives the per-tick IT idle check (warning + auto-pass).
 */
public final class AfkSamplingTask implements Runnable {

    // 0.01 block horizontal, ~1 degree: below this is server-side jitter or lag correction, not
    // a deliberate input, per the owner's spec.
    private static final double POSITION_EPSILON = 0.01;
    private static final double ANGLE_EPSILON = 1.0;

    private final TagService service;
    private final Map<UUID, double[]> lastSample = new HashMap<>();

    public AfkSamplingTask(TagService service) {
        this.service = service;
    }

    @Override
    public void run() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            if (moved(player)) {
                service.markActive(player.getUniqueId());
            }
        }
        // Drops players who left, so the sample cache never grows past the online count.
        lastSample.keySet().retainAll(online);
        service.checkItAfk();
    }

    private boolean moved(Player player) {
        Location loc = player.getLocation();
        double[] sample = {loc.getX(), loc.getZ(), loc.getYaw(), loc.getPitch()};
        double[] previous = lastSample.put(player.getUniqueId(), sample);
        if (previous == null) {
            // First sample after (re)start/reload: nothing to compare against yet. Treating it as
            // movement is harmless, join already marks activity separately.
            return true;
        }
        return Math.abs(sample[0] - previous[0]) > POSITION_EPSILON
                || Math.abs(sample[1] - previous[1]) > POSITION_EPSILON
                || angleDelta(sample[2], previous[2]) > ANGLE_EPSILON
                || angleDelta(sample[3], previous[3]) > ANGLE_EPSILON;
    }

    private double angleDelta(double a, double b) {
        double delta = Math.abs(a - b) % 360.0;
        return delta > 180.0 ? 360.0 - delta : delta;
    }
}

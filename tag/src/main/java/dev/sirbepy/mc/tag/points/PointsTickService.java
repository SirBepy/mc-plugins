package dev.sirbepy.mc.tag.points;

import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.logic.PointsMath;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.function.Predicate;

/** Computes one points-tick's gain for the current IT; {@code TagService} applies it to TagState. */
public final class PointsTickService {

    public record Result(double gain, boolean online) {}

    public Result tick(UUID it, TagConfig config, Predicate<UUID> isOptedOut, Predicate<UUID> isAfk) {
        int n = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(it))
                .filter(p -> !isOptedOut.test(p.getUniqueId()))
                .filter(p -> !(config.afk.excludeFromMultiplier && isAfk.test(p.getUniqueId())))
                .count();
        double multiplier = PointsMath.multiplier(n, config.pointsCurve, config.pointsStep);
        boolean online = Bukkit.getPlayer(it) != null;
        double gain = PointsMath.tickGain(online, config.pointsBase, config.pointsOfflineFactor, multiplier);
        return new Result(gain, online);
    }
}

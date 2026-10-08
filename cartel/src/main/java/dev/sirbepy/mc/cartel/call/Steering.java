package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Moves the cousins while a {@link CallManager.Call} is active: who they chase, how far they wander
 * from a guarded owner, and picking a lost hit trail back up. CallManager keeps the call state,
 * placing/ending a call and the who-may-be-fought rules; it only ticks this class.
 */
final class Steering {

    private static final double GUARD_LEASH = 24;

    private final Supplier<CartelConfig> config;
    private final Random random;
    private final Consumer<String> end;
    private final BiPredicate<LivingEntity, Player> guardTarget;
    private final Consumer<Location> smoke;

    Steering(Supplier<CartelConfig> config, Random random, Consumer<String> end,
              BiPredicate<LivingEntity, Player> guardTarget, Consumer<Location> smoke) {
        this.config = config;
        this.random = random;
        this.end = end;
        this.guardTarget = guardTarget;
        this.smoke = smoke;
    }

    void steerGuard(CallManager.Call call) {
        Player owner = Bukkit.getPlayer(call.owner);
        if (owner == null || owner.isDead()) {
            end.accept(null);
            return;
        }
        for (Mob cousin : call.cousins) {
            LivingEntity threat = cousin.getTarget();
            if (threat == null || !threat.isValid() || !guardTarget.test(threat, owner)
                    || threat.getLocation().distanceSquared(owner.getLocation()) > GUARD_LEASH * GUARD_LEASH) {
                threat = nearestThreat(owner);
                cousin.setTarget(threat);
            }
            if (threat != null) {
                continue;
            }
            boolean sameWorld = cousin.getWorld().equals(owner.getWorld());
            double distance = sameWorld ? cousin.getLocation().distance(owner.getLocation()) : Double.MAX_VALUE;
            if (distance > GUARD_LEASH) {
                List<Location> spots = SpawnSpots.around(owner.getLocation(), 2, 1, random);
                cousin.teleport(spots.isEmpty() ? owner.getLocation() : spots.getFirst());
            } else if (distance > 4) {
                cousin.getPathfinder().moveTo(owner, 1.2);
            }
        }
    }

    private LivingEntity nearestThreat(Player owner) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity nearby : owner.getNearbyEntities(CallManager.GUARD_RANGE, 8, CallManager.GUARD_RANGE)) {
            if (nearby instanceof LivingEntity living && guardTarget.test(living, owner)) {
                double d = living.getLocation().distanceSquared(owner.getLocation());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = living;
                }
            }
        }
        return best;
    }

    void steerHit(CallManager.Call call) {
        Player victim = Bukkit.getPlayer(call.target);
        if (victim == null) {
            end.accept(call.targetName + " left the server. The cousins head back to Mexico.");
            return;
        }
        if (!CallManager.targetable(victim)) {
            end.accept(call.targetName + " is out of reach. The cousins head back to Mexico.");
            return;
        }
        int lose = config.get().loseTrailDistance();
        boolean lost = call.cousins.stream().allMatch(cousin -> !cousin.getWorld().equals(victim.getWorld())
                || cousin.getLocation().distanceSquared(victim.getLocation()) > (double) lose * lose);
        if (lost && !retrack(call, victim)) {
            end.accept("The cousins lost " + call.targetName + "'s trail.");
            return;
        }
        for (Mob cousin : call.cousins) {
            if (cousin.getTarget() == null || !cousin.getTarget().getUniqueId().equals(victim.getUniqueId())) {
                cousin.setTarget(victim);
            }
        }
    }

    private boolean retrack(CallManager.Call call, Player victim) {
        if (call.retracks >= config.get().retracks()) {
            return false;
        }
        List<Location> spots = SpawnSpots.around(victim.getLocation(), config.get().hitDistance(), 2, random);
        if (spots.isEmpty()) {
            return false;
        }
        call.retracks++;
        for (int i = 0; i < call.cousins.size(); i++) {
            Location at = spots.get(Math.min(i, spots.size() - 1));
            call.cousins.get(i).teleport(at);
            smoke.accept(at);
        }
        victim.sendMessage(Component.text("The cousins picked up your trail.", NamedTextColor.DARK_RED));
        victim.playSound(victim.getLocation(), Sound.EVENT_MOB_EFFECT_BAD_OMEN, 1.0f, 0.8f);
        return true;
    }
}

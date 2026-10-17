package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sharpshooter's ultimate (Arrow Rain) and keystone (Railshot), split out of {@link RangerArrows}:
 * both only need {@code ctx} and {@link RangerArrows#nearbyHostiles}, not any of that class's
 * arrow-tagging state.
 */
final class RangerShots {

    private final KitContext ctx;
    private final Ability arrowRain;
    private final Ability railshot;

    RangerShots(KitContext ctx) {
        this.ctx = ctx;
        this.arrowRain = SimpleAbility.ultimate("Arrow Rain",
                p -> "arrows fall on a 5-block circle where you look for 6 s, 1.5 hearts per arrow to mobs",
                this::castArrowRain);
        this.railshot = SimpleAbility.of("Railshot", 45,
                p -> "a beam along a 40-block line, 6 hearts to every mob on it",
                this::castRailshot);
    }

    Ability arrowRain() {
        return arrowRain;
    }

    Ability railshot() {
        return railshot;
    }

    private boolean castArrowRain(Player player) {
        Location center = lookedAt(player, 40);
        var task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = ctx.repeat(() -> {
            double angle = ctx.random() * Math.PI * 2;
            double dist = ctx.random() * 5;
            Location drop = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            for (LivingEntity mob : RangerArrows.nearbyHostiles(drop, 1.5)) ctx.damage(mob, 3, player);
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

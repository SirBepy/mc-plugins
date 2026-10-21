package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.FightTicker;
import dev.sirbepy.mc.minibosses.engine.Shape;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Mythic Sentinel's thorn patches: ground left behind by each slam that pricks whoever stands in
 * it. They belong to the fight, not to the slam that made them, so they live in a {@link FightTicker}
 * that keeps running between attacks and is closed when the fight ends. A patch is drawn for its
 * whole life (never invisible damage), expires on its own, and at most {@link SentinelTuning#MAX_PATCHES}
 * exist at once: a newer patch pushes out the oldest.
 */
public final class ThornPatches implements FightTicker {

    private static final Color THORN = Color.fromRGB(40, 110, 35);
    private static final Color THORN_TIP = Color.fromRGB(130, 190, 60);
    private static final int DRAW_EVERY_TICKS = 4;
    private static final double OUTLINE_SPACING = 0.7;
    /** Lifts particles off the floor so they are not buried in the surface block. */
    private static final double LIFT = 0.15;
    private static final int TIPS_PER_DRAW = 3;

    private record Patch(Location at, long expiresAt) {
    }

    private final FightContext ctx;
    private final List<Patch> patches = new ArrayList<>();
    private final Map<UUID, Long> lastPricked = new HashMap<>();
    private long now;

    private ThornPatches(FightContext ctx) {
        this.ctx = ctx;
    }

    /** The patches of this fight, started (and ticking) on first use. */
    public static ThornPatches of(FightContext ctx) {
        return ctx.state(ThornPatches.class, c -> {
            ThornPatches created = new ThornPatches(c);
            c.addTicker(created);
            return created;
        });
    }

    /** How many are alive now. */
    public int count() {
        return patches.size();
    }

    /** Where the live patches are, oldest first. */
    public List<Location> locations() {
        return patches.stream().map(Patch::at).toList();
    }

    /** Lays {@link SentinelTuning#PATCHES_PER_SLAM} patches at random ground points near {@code impact}, inside the area. */
    void leaveAround(Location impact) {
        for (int i = 0; i < SentinelTuning.PATCHES_PER_SLAM; i++) {
            add(ctx.area().randomGroundPoint(ctx.rng(), impact, SentinelTuning.PATCH_SPREAD));
        }
    }

    void add(Location at) {
        patches.add(new Patch(at, now + SentinelTuning.PATCH_LIFE_TICKS));
        while (patches.size() > SentinelTuning.MAX_PATCHES) {
            patches.remove(0);
        }
    }

    @Override
    public boolean tick() {
        now++;
        patches.removeIf(patch -> patch.expiresAt() <= now);
        if (patches.isEmpty()) {
            return false; // kept: the next slam of this fight reuses it
        }
        if (now % DRAW_EVERY_TICKS == 0) {
            patches.forEach(this::draw);
        }
        prick();
        return false;
    }

    @Override
    public void close() {
        patches.clear();
        lastPricked.clear();
    }

    private void prick() {
        for (Player player : ctx.fighters()) {
            Long last = lastPricked.get(player.getUniqueId());
            if (last != null && now - last < SentinelTuning.PATCH_HIT_INTERVAL_TICKS) {
                continue;
            }
            for (Patch patch : patches) {
                if (new Shape.Circle(patch.at(), SentinelTuning.PATCH_RADIUS).contains(player.getLocation())) {
                    ctx.damage(player, SentinelTuning.PATCH_DAMAGE);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SentinelTuning.PATCH_SLOW_TICKS, 0));
                    lastPricked.put(player.getUniqueId(), now);
                    break;
                }
            }
        }
    }

    private void draw(Patch patch) {
        Location centre = patch.at();
        for (Vector point : new Shape.Circle(centre, SentinelTuning.PATCH_RADIUS).outline(OUTLINE_SPACING)) {
            speck(point.getX(), point.getY(), point.getZ(), THORN, 1.0f, false);
        }
        // The glints scatter with their own random source so drawing never shifts the fight's attack picks.
        ThreadLocalRandom scatter = ThreadLocalRandom.current();
        for (int i = 0; i < TIPS_PER_DRAW; i++) {
            double angle = scatter.nextDouble() * Math.PI * 2;
            double reach = Math.sqrt(scatter.nextDouble()) * SentinelTuning.PATCH_RADIUS;
            speck(centre.getX() + Math.cos(angle) * reach, centre.getY(), centre.getZ() + Math.sin(angle) * reach,
                    THORN_TIP, 0.9f, true);
        }
    }

    /** One dust speck (and a thorn glint above it if asked), only where the fight area reaches. */
    private void speck(double x, double y, double z, Color color, float size, boolean glint) {
        Location at = new Location(ctx.area().world(), x, y, z);
        if (!ctx.area().contains(at)) {
            return;
        }
        double lifted = y + LIFT;
        ctx.effects().dust(ctx.area().world(), x, lifted, z, color, size);
        if (glint) {
            ctx.effects().particle(ctx.area().world(), Particle.COMPOSTER, x, lifted + 0.3, z, 1, 0.1);
        }
    }
}

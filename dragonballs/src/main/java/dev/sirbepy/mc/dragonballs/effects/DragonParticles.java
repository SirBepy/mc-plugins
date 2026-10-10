package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Supplier;

/**
 * The summon dragon's per-set particle work, split out of {@link DragonActor} so that class keeps
 * only spawning, movement, visibility and despawn: Nuova's fire trail and the Nether Porunga's
 * purple trail along the body (driven by {@code DragonActor}'s own circle tick), plus the Nether
 * smoke column and End aura (driven by this class's own periodic task). Deliberately only touches
 * {@link World}/{@link Particle}/{@link EffectsConfig}; {@code DragonModel}/{@code EnderDragon}
 * knowledge stays in {@code DragonActor}, which supplies a body spot instead.
 */
final class DragonParticles {

    private static final long AURA_PERIOD_TICKS = 10; // 0.5s; keeps the column/aura at a lag-safe rate
    // Spawned every circle tick on the body, so the particles' own lifetime draws the trail.
    private static final int TRAIL_FLAMES = 4;
    private static final Particle.DustOptions PURPLE_DUST = new Particle.DustOptions(Color.fromRGB(0x9B30FF), 1.5f);
    // On 26.x dragon_breath carries a power float (Particle.DRAGON_BREATH's data type is Float) and
    // Paper throws "missing required data" without it, which would kill the End aura task. 1.0 is
    // vanilla's own power.
    static final float BREATH_POWER = 1.0f;

    private final JavaPlugin plugin;
    private final Location summonCenter;
    private final double circleHeight;
    private final double modelSize;
    private final Supplier<Location> bodySpot;

    private DragonPlacement.Look look;
    private BukkitTask auraTask;

    /** {@code bodySpot} returns null while the dragon isn't up, so the aura skips those ticks. */
    DragonParticles(JavaPlugin plugin, Location summonCenter, double circleHeight, DragonPlacement.Look look,
                     double modelSize, Supplier<Location> bodySpot) {
        this.plugin = plugin;
        this.summonCenter = summonCenter;
        this.circleHeight = circleHeight;
        this.look = look;
        this.modelSize = modelSize;
        this.bodySpot = bodySpot;
    }

    /** Nuova's fire trail: flames and embers along his body, left behind as he moves. Only called
     * while the dragon is alive, so {@code bodySpot} is never null here. */
    void emitFireTrail() {
        double spread = 0.8 * modelSize;
        Location body = bodySpot.get();
        World world = body.getWorld();
        world.spawnParticle(Particle.FLAME, body, TRAIL_FLAMES, spread, spread * 0.5, spread, 0.01);
        world.spawnParticle(Particle.SMALL_FLAME, body, TRAIL_FLAMES / 2, spread, spread * 0.5, spread, 0.01);
        world.spawnParticle(Particle.LAVA, body, 1, spread, spread * 0.5, spread, 0);
    }

    /** A Nether Porunga's purple trail: dust and witch sparkles on his body, like Nuova's flames. */
    void emitPurpleTrail() {
        double spread = 0.8 * modelSize;
        Location body = bodySpot.get();
        World world = body.getWorld();
        world.spawnParticle(Particle.DUST, body, TRAIL_FLAMES, spread, spread * 0.5, spread, 0, PURPLE_DUST);
        world.spawnParticle(Particle.WITCH, body, TRAIL_FLAMES / 2, spread, spread * 0.5, spread, 0.01);
    }

    void startAuraTask(World world, EffectsConfig config) {
        if (look == null || (!look.smokeColumn() && !look.purpleAura())) {
            return;
        }
        auraTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> auraTick(world, config), AURA_PERIOD_TICKS, AURA_PERIOD_TICKS);
    }

    private void auraTick(World world, EffectsConfig config) {
        if (look == null) {
            return;
        }
        if (look.smokeColumn()) {
            emitNetherColumn(world, config);
        } else if (look.purpleAura()) {
            emitEndAura(world, config);
        }
    }

    /** A column of smoke and embers rooted at the summon centre, spanning up to about the
     * dragon's own height - fixed in place (unlike the aura below) since Joe asked for the whole
     * session wrapped in it, not just the circling dragon. */
    private void emitNetherColumn(World world, EffectsConfig config) {
        if (summonCenter == null || look == null) {
            return;
        }
        double bodyTop = DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * look.scale();
        double columnHeight = Math.max(1.0, circleHeight + bodyTop);
        Location mid = summonCenter.clone().add(0, columnHeight / 2.0, 0);
        int smoke = config.netherSmokeParticleCount();
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, mid, smoke, 0.6, columnHeight / 2.0, 0.6, 0.02);
        world.spawnParticle(Particle.LARGE_SMOKE, mid, Math.max(1, smoke / 2), 0.5, columnHeight / 2.0, 0.5, 0.01);
        Location base = summonCenter.clone().add(0, 0.5, 0);
        int embers = config.netherEmberParticleCount();
        world.spawnParticle(Particle.LAVA, base, embers, 0.5, 0.3, 0.5, 0);
        world.spawnParticle(Particle.FLAME, base, embers, 0.5, 0.5, 0.5, 0.02);
    }

    /** A purple swirl on the dragon's body, following it as it moves. */
    private void emitEndAura(World world, EffectsConfig config) {
        Location at = bodySpot.get();
        if (at == null) {
            return;
        }
        int portal = config.endAuraParticleCount();
        world.spawnParticle(Particle.PORTAL, at, portal, 1.2, 1.0, 1.2, 0.3);
        world.spawnParticle(Particle.REVERSE_PORTAL, at, Math.max(1, portal / 3), 1.0, 0.8, 1.0, 0.1);
        world.spawnParticle(Particle.END_ROD, at, 2, 0.8, 0.6, 0.8, 0.02);
        world.spawnParticle(Particle.DRAGON_BREATH, at, 2, 0.8, 0.6, 0.8, 0.01, BREATH_POWER);
    }

    void stopAuraTask() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
        look = null;
    }
}

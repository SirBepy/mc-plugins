package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Tunable knobs for the summon visuals, read straight from the plugin's own
 * {@code config.yml} under the {@code effects} section (not {@link dev.sirbepy.mc.dragonballs.config.DragonBallsConfig},
 * which this package doesn't own). Re-read on demand so {@code /dragonballs reload} picks up
 * edits without this package needing its own cache-invalidation.
 *
 * @param dragonHeight            how far above the summon centre the dragon hovers in the
 *                                 Overworld and the End, in blocks
 * @param dragonScale             {@link org.bukkit.attribute.Attribute#SCALE} base value for the
 *                                 Overworld and End dragon
 * @param netherDragonScale       {@code SCALE} base value for the Nether's half-size dragon
 * @param netherMaxHeight         the Nether ceiling scan's range, and the hover height's cap
 * @param netherCeilingClearance  blocks of headroom the Nether dragon's body tries to keep below
 *                                 the cavern ceiling
 * @param netherMinHeight         the Nether hover height to prefer when the ceiling is close,
 *                                 unless that would still poke the body into it
 * @param netherSmokeParticleCount CAMPFIRE_SIGNAL_SMOKE count per column tick (LARGE_SMOKE gets
 *                                 half as many)
 * @param netherEmberParticleCount LAVA/FLAME count per column tick
 * @param endAuraParticleCount     PORTAL count per aura tick (the rest of the swirl scales off it)
 */
public record EffectsConfig(double dragonHeight, double dragonScale, double netherDragonScale,
                             double netherMaxHeight, double netherCeilingClearance, double netherMinHeight,
                             int netherSmokeParticleCount, int netherEmberParticleCount, int endAuraParticleCount) {

    public static final double DEFAULT_DRAGON_HEIGHT = 18.0;
    public static final double DEFAULT_DRAGON_SCALE = 1.0;
    public static final double DEFAULT_NETHER_DRAGON_SCALE = 0.5;
    public static final double DEFAULT_NETHER_MAX_HEIGHT = 18.0;
    public static final double DEFAULT_NETHER_CEILING_CLEARANCE = 2.0;
    public static final double DEFAULT_NETHER_MIN_HEIGHT = 3.0;
    public static final int DEFAULT_NETHER_SMOKE_PARTICLE_COUNT = 8;
    public static final int DEFAULT_NETHER_EMBER_PARTICLE_COUNT = 4;
    public static final int DEFAULT_END_AURA_PARTICLE_COUNT = 6;

    public static EffectsConfig from(FileConfiguration config) {
        return new EffectsConfig(
                config.getDouble("effects.dragon-height", DEFAULT_DRAGON_HEIGHT),
                config.getDouble("effects.dragon-scale", DEFAULT_DRAGON_SCALE),
                config.getDouble("effects.nether-dragon-scale", DEFAULT_NETHER_DRAGON_SCALE),
                config.getDouble("effects.nether-max-height", DEFAULT_NETHER_MAX_HEIGHT),
                config.getDouble("effects.nether-ceiling-clearance", DEFAULT_NETHER_CEILING_CLEARANCE),
                config.getDouble("effects.nether-min-height", DEFAULT_NETHER_MIN_HEIGHT),
                config.getInt("effects.nether-smoke-particle-count", DEFAULT_NETHER_SMOKE_PARTICLE_COUNT),
                config.getInt("effects.nether-ember-particle-count", DEFAULT_NETHER_EMBER_PARTICLE_COUNT),
                config.getInt("effects.end-aura-particle-count", DEFAULT_END_AURA_PARTICLE_COUNT));
    }
}

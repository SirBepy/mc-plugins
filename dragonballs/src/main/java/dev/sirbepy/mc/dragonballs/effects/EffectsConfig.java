package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Tunable knobs for the summon visuals, read straight from the plugin's own
 * {@code config.yml} under the {@code effects} section (not {@link dev.sirbepy.mc.dragonballs.config.DragonBallsConfig},
 * which this package doesn't own). Re-read on demand so {@code /dragonballs reload} picks up
 * edits without this package needing its own cache-invalidation.
 *
 * @param dragonHeight how far above the summon centre the dragon hovers, in blocks
 * @param dragonScale  {@link org.bukkit.attribute.Attribute#SCALE} base value for the spawned dragon
 */
public record EffectsConfig(double dragonHeight, double dragonScale) {

    public static final double DEFAULT_DRAGON_HEIGHT = 18.0;
    public static final double DEFAULT_DRAGON_SCALE = 1.0;

    public static EffectsConfig from(FileConfiguration config) {
        return new EffectsConfig(
                config.getDouble("effects.dragon-height", DEFAULT_DRAGON_HEIGHT),
                config.getDouble("effects.dragon-scale", DEFAULT_DRAGON_SCALE));
    }
}

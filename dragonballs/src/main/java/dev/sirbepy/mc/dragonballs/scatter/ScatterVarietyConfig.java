package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * The underground/point-of-interest knobs added on top of {@code scatter:} for the "ball placement
 * variety" redesign. Read straight from the plugin's {@link FileConfiguration} rather than through
 * {@code DragonBallsConfig} (owned outside this package), so it still reloads on {@code /dragonballs
 * reload} the same way every other scatter knob does.
 */
public final class ScatterVarietyConfig {

    private final int undergroundCountMin;
    private final int undergroundCountMax;
    private final int undergroundDepthMin;
    private final int undergroundDepthMax;
    private final int poiCountMin;
    private final int poiCountMax;
    private final int poiSearchRadiusChunks;
    private final int poiAttempts;
    private final List<String> overworldPoiStructures;
    private final List<String> netherPoiStructures;

    public ScatterVarietyConfig(FileConfiguration config) {
        this.undergroundCountMin = Math.max(0, config.getInt("scatter.underground-count.min", 1));
        this.undergroundCountMax = Math.max(undergroundCountMin, config.getInt("scatter.underground-count.max", 2));
        this.undergroundDepthMin = Math.max(1, config.getInt("scatter.underground-depth.min", 10));
        this.undergroundDepthMax = Math.max(undergroundDepthMin, config.getInt("scatter.underground-depth.max", 30));
        this.poiCountMin = Math.max(0, config.getInt("scatter.poi-count.min", 1));
        this.poiCountMax = Math.max(poiCountMin, config.getInt("scatter.poi-count.max", 2));
        this.poiSearchRadiusChunks = Math.max(1, config.getInt("scatter.poi-search-radius-chunks", 32));
        this.poiAttempts = Math.max(1, config.getInt("scatter.poi-attempts", 3));
        this.overworldPoiStructures = List.copyOf(config.getStringList("scatter.poi-structures.overworld"));
        this.netherPoiStructures = List.copyOf(config.getStringList("scatter.poi-structures.nether"));
    }

    public int undergroundCountMin() {
        return undergroundCountMin;
    }

    public int undergroundCountMax() {
        return undergroundCountMax;
    }

    public int undergroundDepthMin() {
        return undergroundDepthMin;
    }

    public int undergroundDepthMax() {
        return undergroundDepthMax;
    }

    public int poiCountMin() {
        return poiCountMin;
    }

    public int poiCountMax() {
        return poiCountMax;
    }

    public int poiSearchRadiusChunks() {
        return poiSearchRadiusChunks;
    }

    public int poiAttempts() {
        return poiAttempts;
    }

    public List<String> overworldPoiStructures() {
        return overworldPoiStructures;
    }

    public List<String> netherPoiStructures() {
        return netherPoiStructures;
    }
}

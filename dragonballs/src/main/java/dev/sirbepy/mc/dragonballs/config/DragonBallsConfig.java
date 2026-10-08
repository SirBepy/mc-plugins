package dev.sirbepy.mc.dragonballs.config;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Snapshot of {@code config.yml}, rebuilt by {@code /dragonballs reload}. */
public final class DragonBallsConfig {

    private final Map<BallSet, Map<Integer, String>> starTextures = new EnumMap<>(BallSet.class);
    private final String stoneTexture;
    private final String world;
    private final String netherWorld;
    private final String endWorld;
    private final int pregenLimit;
    private final int netherPregenLimit;
    private final double netherRadiusMultiplier;
    private final int minSpacing;
    private final int maxLiquidResamples;
    private final int endMinDistance;
    private final int endMaxDistance;
    private final List<DifficultyTier> difficulty;
    private final double slowPerBall;
    private final StoneMode stoneMode;
    private final int stoneDays;
    private final int stoneRealHours;
    private final Messages messages;

    public DragonBallsConfig(FileConfiguration config) {
        ConfigurationSection textures = config.getConfigurationSection("textures");
        for (BallSet set : BallSet.values()) {
            starTextures.put(set, parseSetTextures(textures, set));
        }
        this.stoneTexture = textures == null ? null : textures.getString("stone");

        this.world = config.getString("world", "world");
        this.netherWorld = config.getString("nether-world", "world_nether");
        this.endWorld = config.getString("end-world", "world_the_end");
        this.pregenLimit = config.getInt("scatter.pregen-limit", 4900);
        this.netherPregenLimit = config.getInt("scatter.nether-pregen-limit", 1200);
        this.netherRadiusMultiplier = Math.max(0.1, config.getDouble("scatter.nether-radius-multiplier", 2.0));
        this.minSpacing = config.getInt("scatter.min-spacing", 200);
        this.maxLiquidResamples = Math.max(1, config.getInt("scatter.max-liquid-resamples", 20));
        this.endMinDistance = config.getInt("scatter.end-min-distance", 1000);
        this.endMaxDistance = Math.max(endMinDistance, config.getInt("scatter.end-max-distance", 3000));
        this.difficulty = parseDifficulty(config.getMapList("difficulty"));
        this.slowPerBall = config.getDouble("slow-per-ball", 0.05);

        String mode = config.getString("stone.mode", "minecraft-days").toLowerCase(Locale.ROOT);
        this.stoneMode = "real-hours".equals(mode) ? StoneMode.REAL_HOURS : StoneMode.MINECRAFT_DAYS;
        this.stoneDays = Math.max(1, config.getInt("stone.days", 3));
        this.stoneRealHours = Math.max(1, config.getInt("stone.real-hours", 24));
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    // A hardcoded fallback schedule (same as config.yml's default) covers a config.yml saved by an
    // older jar version, before copyDefaults(true) has had a chance to add the new "difficulty" list.
    // Old configs on live still carry "nether"/"end" keys per tier (the old dimension-split scheme);
    // those are read as part of the map but simply never looked at.
    private static List<DifficultyTier> parseDifficulty(List<Map<?, ?>> maps) {
        List<DifficultyTier> tiers = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            int min = asInt(map.get("min-radius"), 1000);
            int max = Math.max(min, asInt(map.get("max-radius"), min));
            tiers.add(new DifficultyTier(min, max));
        }
        return tiers.isEmpty() ? List.of(new DifficultyTier(1000, 2000)) : List.copyOf(tiers);
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /**
     * Overworld falls back to the legacy flat {@code textures.<n>} layout when
     * {@code textures.overworld.<n>} is absent (a {@code config.yml} saved by a jar from before the
     * 3-set split); nether/end have no legacy layout to fall back to, so a missing value there just
     * resolves to the jar's own bundled default via {@code getConfigurationSection}'s defaults chain.
     */
    private static Map<Integer, String> parseSetTextures(ConfigurationSection textures, BallSet set) {
        Map<Integer, String> result = new LinkedHashMap<>();
        ConfigurationSection setSection = textures == null ? null : textures.getConfigurationSection(set.id());
        for (int star = 1; star <= 7; star++) {
            String value = setSection == null ? null : setSection.getString(String.valueOf(star));
            if (value == null && set == BallSet.OVERWORLD) {
                value = textures == null ? null : textures.getString(String.valueOf(star));
            }
            if (value != null) {
                result.put(star, value);
            }
        }
        return result;
    }

    public String texture(BallSet set, int star) {
        return starTextures.get(set).get(star);
    }

    public String stoneTexture() {
        return stoneTexture;
    }

    public String world() {
        return world;
    }

    public String netherWorld() {
        return netherWorld;
    }

    public String endWorld() {
        return endWorld;
    }

    public int pregenLimit() {
        return pregenLimit;
    }

    public int netherPregenLimit() {
        return netherPregenLimit;
    }

    public double netherRadiusMultiplier() {
        return netherRadiusMultiplier;
    }

    public int minSpacing() {
        return minSpacing;
    }

    public int maxLiquidResamples() {
        return maxLiquidResamples;
    }

    public int endMinDistance() {
        return endMinDistance;
    }

    public int endMaxDistance() {
        return endMaxDistance;
    }

    public List<DifficultyTier> difficulty() {
        return difficulty;
    }

    public double slowPerBall() {
        return slowPerBall;
    }

    public StoneMode stoneMode() {
        return stoneMode;
    }

    public int stoneDays() {
        return stoneDays;
    }

    public int stoneRealHours() {
        return stoneRealHours;
    }

    public Messages messages() {
        return messages;
    }
}

package dev.sirbepy.mc.dragonballs.config;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Snapshot of {@code config.yml}, rebuilt by {@code /dragonballs reload}. */
public final class DragonBallsConfig {

    private final Map<Integer, String> starTextures = new LinkedHashMap<>();
    private final String stoneTexture;
    private final String world;
    private final String netherWorld;
    private final String endWorld;
    private final int pregenLimit;
    private final int netherPregenLimit;
    private final int minSpacing;
    private final int maxLiquidResamples;
    private final int endMinDistance;
    private final int endMaxDistance;
    private final List<DifficultyTier> difficulty;
    private final double slowPerBall;
    private final StoneMode stoneMode;
    private final int stoneDays;
    private final int stoneRealHours;
    private final int logoutHours;
    private final Messages messages;

    public DragonBallsConfig(FileConfiguration config) {
        ConfigurationSection textures = config.getConfigurationSection("textures");
        for (int star = 1; star <= 7; star++) {
            String value = textures == null ? null : textures.getString(String.valueOf(star));
            if (value != null) {
                starTextures.put(star, value);
            }
        }
        this.stoneTexture = textures == null ? null : textures.getString("stone");

        this.world = config.getString("world", "world");
        this.netherWorld = config.getString("nether-world", "world_nether");
        this.endWorld = config.getString("end-world", "world_the_end");
        this.pregenLimit = config.getInt("scatter.pregen-limit", 4900);
        this.netherPregenLimit = config.getInt("scatter.nether-pregen-limit", 1200);
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
        this.logoutHours = Math.max(1, config.getInt("logout-hours", 24));
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    // A hardcoded fallback schedule (same as config.yml's default) covers a config.yml saved by an
    // older jar version, before copyDefaults(true) has had a chance to add the new "difficulty" list.
    private static List<DifficultyTier> parseDifficulty(List<Map<?, ?>> maps) {
        List<DifficultyTier> tiers = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            int min = asInt(map.get("min-radius"), 1000);
            int max = Math.max(min, asInt(map.get("max-radius"), min));
            int nether = asInt(map.get("nether"), 0);
            int end = asInt(map.get("end"), 0);
            tiers.add(new DifficultyTier(min, max, nether, end));
        }
        return tiers.isEmpty() ? List.of(new DifficultyTier(1000, 2000, 0, 0)) : List.copyOf(tiers);
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public String texture(int star) {
        return starTextures.get(star);
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

    public int logoutHours() {
        return logoutHours;
    }

    public Messages messages() {
        return messages;
    }
}

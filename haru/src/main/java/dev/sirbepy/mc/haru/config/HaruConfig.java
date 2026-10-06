package dev.sirbepy.mc.haru.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

/** Snapshot of {@code config.yml}, rebuilt by {@code /haru reload}. */
public record HaruConfig(UUID owner, ZoneId zone, String world, int rotationDays, double legendaryChance, Skin skin) {

    /** The Java-side Haru's skin; see config.yml's {@code skin} section. Blank strings mean unset. */
    public record Skin(String texture, String packTexture, String packCape, boolean slim) {
        public static final Skin NONE = new Skin("", "", "", false);
    }

    public static final UUID DEFAULT_OWNER = UUID.fromString("8c0c13b9-057a-4ac8-aacf-7b6c77f8742b");

    public static HaruConfig from(FileConfiguration config) {
        return new HaruConfig(
                parseOwner(config.getString("owner")),
                parseZone(config.getString("timezone")),
                config.getString("world", "world"),
                Math.max(1, config.getInt("rotation-days", 3)),
                Math.clamp(config.getDouble("legendary-chance", 0.2), 0.0, 1.0),
                new Skin(config.getString("skin.texture", "").trim(), config.getString("skin.pack-texture", "").trim(),
                        config.getString("skin.pack-cape", "").trim(),
                        "slim".equalsIgnoreCase(config.getString("skin.model", "wide"))));
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    private static UUID parseOwner(String value) {
        try {
            return value == null ? DEFAULT_OWNER : UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return DEFAULT_OWNER;
        }
    }

    private static ZoneId parseZone(String value) {
        try {
            return value == null ? ZoneId.of("Europe/Zagreb") : ZoneId.of(value.trim());
        } catch (DateTimeException e) {
            return ZoneId.of("Europe/Zagreb");
        }
    }
}

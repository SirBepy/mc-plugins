package dev.sirbepy.mc.haru.config;

import dev.sirbepy.mc.core.Zones;
import dev.sirbepy.mc.core.npc.NpcSkin;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.UUID;

/** Snapshot of {@code config.yml}, rebuilt by {@code /haru reload}; {@code skin} is the Java-side Haru's. */
public record HaruConfig(UUID owner, ZoneId zone, String world, int rotationDays, double legendaryChance, NpcSkin skin) {

    public static final UUID DEFAULT_OWNER = UUID.fromString("8c0c13b9-057a-4ac8-aacf-7b6c77f8742b");

    public static HaruConfig from(FileConfiguration config) {
        return new HaruConfig(
                parseOwner(config.getString("owner")),
                Zones.parse(config.getString("timezone"), ZoneId.of("Europe/Zagreb")),
                config.getString("world", "world"),
                Math.max(1, config.getInt("rotation-days", 3)),
                Math.clamp(config.getDouble("legendary-chance", 0.2), 0.0, 1.0),
                new NpcSkin(config.getString("skin.texture", "").trim(), config.getString("skin.pack-texture", "").trim(),
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

}

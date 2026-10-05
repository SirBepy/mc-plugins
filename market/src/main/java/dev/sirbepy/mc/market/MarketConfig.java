package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;

/** Snapshot of {@code config.yml}. */
public final class MarketConfig {

    private final String world;
    private final ZoneId zone;
    private final int minItems;
    private final int maxItems;
    private final double priceSpread;
    private final Messages messages;

    public MarketConfig(FileConfiguration config) {
        this.world = config.getString("world", "world");
        this.zone = ZoneId.of(config.getString("zone", "Europe/Zagreb"));
        this.minItems = Math.max(0, config.getInt("items-per-stall.min", 2));
        this.maxItems = Math.max(minItems, config.getInt("items-per-stall.max", 3));
        this.priceSpread = Math.clamp(config.getDouble("price-spread", 0.2), 0.0, 0.9);
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    public String world() {
        return world;
    }

    public ZoneId zone() {
        return zone;
    }

    public int minItems() {
        return minItems;
    }

    public int maxItems() {
        return maxItems;
    }

    public double priceSpread() {
        return priceSpread;
    }

    public Messages messages() {
        return messages;
    }
}

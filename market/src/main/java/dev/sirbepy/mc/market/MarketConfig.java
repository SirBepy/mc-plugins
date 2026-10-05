package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Snapshot of {@code config.yml}. */
public final class MarketConfig {

    private static final long TICKS_PER_DAY = 24000L;

    private final String world;
    private final boolean realDays;
    private final ZoneId zone;
    private final int minItems;
    private final int maxItems;
    private final double priceSpread;
    private final Messages messages;

    public MarketConfig(FileConfiguration config) {
        this.world = config.getString("world", "world");
        this.realDays = "real-day".equals(config.getString("rotate-on", "minecraft-day").toLowerCase(Locale.ROOT));
        this.zone = ZoneId.of(config.getString("zone", "Europe/Zagreb"));
        this.minItems = Math.max(0, config.getInt("items-per-stall.min", 2));
        this.maxItems = Math.max(minItems, config.getInt("items-per-stall.max", 3));
        this.priceSpread = Math.clamp(config.getDouble("price-spread", 0.2), 0.0, 0.9);
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    public String world() {
        return world;
    }

    /**
     * Which market day it is: the in-game day of {@code fullTime} (a new one every sunrise, sleeping
     * included), or with {@code rotate-on: real-day} the calendar date in {@code zone}.
     */
    public String period(long fullTime, Instant now) {
        return realDays ? LocalDate.ofInstant(now, zone).toString() : "minecraft-day-" + Math.floorDiv(fullTime, TICKS_PER_DAY);
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

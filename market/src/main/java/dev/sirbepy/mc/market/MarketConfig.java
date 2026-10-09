package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Snapshot of {@code config.yml}. */
public final class MarketConfig {

    private static final long TICKS_PER_DAY = 24000L;
    // The live default LuckPerms group holds huskhomes.max_homes.3 (Joe, 2026-10-09), so every
    // player already has 3 homes; the Homes shop only ever sells above that floor.
    private static final int BASE_HOMES = 3;

    private final String world;
    private final boolean realDays;
    private final ZoneId zone;
    private final int minItems;
    private final int maxItems;
    private final double priceSpread;
    private final int headsPerDay;
    private final double wantedMedian;
    private final double wantedSigma;
    private final long wantedMin;
    private final long wantedMax;
    private final double wantedOldHeadShare;
    private final Map<Integer, BigDecimal> homePrices;
    private final int maxHomes;
    private final BigDecimal cosmeticsPrice;
    private final Messages messages;

    public MarketConfig(FileConfiguration config) {
        this.world = config.getString("world", "world");
        this.realDays = "real-day".equals(config.getString("rotate-on", "minecraft-day").toLowerCase(Locale.ROOT));
        this.zone = ZoneId.of(config.getString("zone", "Europe/Zagreb"));
        this.minItems = Math.max(0, config.getInt("items-per-stall.min", 2));
        this.maxItems = Math.max(minItems, config.getInt("items-per-stall.max", 3));
        this.priceSpread = Math.clamp(config.getDouble("price-spread", 0.2), 0.0, 0.9);
        this.headsPerDay = Math.max(0, config.getInt("heads-per-day", 5));
        this.wantedMedian = Math.max(0.01, config.getDouble("wanted.median", 100));
        this.wantedSigma = Math.max(0.0, config.getDouble("wanted.sigma", 0.8));
        this.wantedMin = Math.max(1, config.getLong("wanted.min", 10));
        this.wantedMax = Math.max(wantedMin, config.getLong("wanted.max", 1000));
        this.wantedOldHeadShare = Math.clamp(config.getDouble("wanted.old-head-share", 0.75), 0.0, 1.0);
        this.maxHomes = Math.max(BASE_HOMES, config.getInt("homes.max", 6));
        Map<Integer, BigDecimal> prices = new LinkedHashMap<>();
        for (int n = BASE_HOMES + 1; n <= maxHomes; n++) {
            prices.put(n, BigDecimal.valueOf(config.getLong("homes.prices." + n, defaultHomePrice(n))));
        }
        this.homePrices = Map.copyOf(prices);
        this.cosmeticsPrice = BigDecimal.valueOf(config.getLong("cosmetics.price", 2500));
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    // Only reached for a tier missing its own config.yml entry: today's shipped tiers (4/5/6) always
    // have one, this only backstops a config that raises homes.max without adding the new price.
    private static long defaultHomePrice(int n) {
        return switch (n) {
            case 4 -> 500;
            case 5 -> 1500;
            case 6 -> 4000;
            default -> 1500L * (n - BASE_HOMES);
        };
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

    /** How many mobs' heads the Heads stall wants each market day. */
    public int headsPerDay() {
        return headsPerDay;
    }

    /** The Wanted board's price is log-normal: {@code median} dollars at the middle of the curve,
     * {@code sigma} its spread, clamped to [{@code min}, {@code max}]. */
    public double wantedMedian() {
        return wantedMedian;
    }

    public double wantedSigma() {
        return wantedSigma;
    }

    public long wantedMin() {
        return wantedMin;
    }

    public long wantedMax() {
        return wantedMax;
    }

    /** What a head taken before the bounty was posted (or with no kill stamp at all) pays, as a
     * fraction of the full price (Joe, 2026-10-09: 0.75, a fresh kill pays full). */
    public double wantedOldHeadShare() {
        return wantedOldHeadShare;
    }

    public Messages messages() {
        return messages;
    }

    /** The Homes shop's floor: everyone's homes already, from the default LuckPerms group. */
    public int baseHomes() {
        return BASE_HOMES;
    }

    /** The most homes the shop sells up to (6, Joe 2026-10-09). */
    public int maxHomes() {
        return maxHomes;
    }

    /** What the Homes shop charges for home number {@code n} (4, 5 or 6 by default), or null above {@link #maxHomes()}. */
    public BigDecimal homePrice(int n) {
        return homePrices.get(n);
    }

    /** What one Cosmetic Token costs at the Cosmetics shop. */
    public BigDecimal cosmeticsPrice() {
        return cosmeticsPrice;
    }
}

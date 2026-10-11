package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.core.Zones;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    private final double wantedBase;
    private final double wantedPerHour;
    private final long wantedCap;
    private final double wantedSpread;
    private final double wantedOnlineChance;
    private final double wantedOldHeadShare;
    private final double wantedSurvivorShare;
    private final int wantedMinOnline;
    private final Map<Integer, BigDecimal> homePrices;
    private final int maxHomes;
    private final BigDecimal cosmeticsPrice;
    private final Messages messages;

    public MarketConfig(FileConfiguration config) {
        this.world = config.getString("world", "world");
        this.realDays = "real-day".equals(config.getString("rotate-on", "minecraft-day").toLowerCase(Locale.ROOT));
        this.zone = Zones.parse(config.getString("zone"), ZoneId.of("Europe/Zagreb"));
        this.minItems = Math.max(0, config.getInt("items-per-stall.min", 2));
        this.maxItems = Math.max(minItems, config.getInt("items-per-stall.max", 3));
        this.priceSpread = Math.clamp(config.getDouble("price-spread", 0.2), 0.0, 0.9);
        this.headsPerDay = Math.max(0, config.getInt("heads-per-day", 5));
        // New key names (cap, not max): live's config.yml still holds the old log-normal block's
        // wanted.max: 1000, which would silently become the cap if the name were reused.
        this.wantedBase = Math.max(0.0, config.getDouble("wanted.base", 50));
        this.wantedPerHour = Math.max(0.0, config.getDouble("wanted.per-hour", 25));
        this.wantedCap = Math.max(1, config.getLong("wanted.cap", 500));
        this.wantedSpread = Math.clamp(config.getDouble("wanted.spread", 0.1), 0.0, 0.9);
        this.wantedOnlineChance = Math.clamp(config.getDouble("wanted.online-chance", 0.8), 0.0, 1.0);
        this.wantedOldHeadShare = Math.clamp(config.getDouble("wanted.old-head-share", 0.5), 0.0, 1.0);
        this.wantedSurvivorShare = Math.clamp(config.getDouble("wanted.survivor-share", 0.5), 0.0, 1.0);
        this.wantedMinOnline = Math.max(0, config.getInt("wanted.min-online", 4));
        this.maxHomes = Math.max(BASE_HOMES, config.getInt("homes.max", 6));
        Map<Integer, BigDecimal> prices = new LinkedHashMap<>();
        for (int n = BASE_HOMES + 1; n <= maxHomes; n++) {
            prices.put(n, BigDecimal.valueOf(config.getLong("homes.prices." + n, defaultHomePrice(n))));
        }
        this.homePrices = Map.copyOf(prices);
        this.cosmeticsPrice = BigDecimal.valueOf(config.getLong("cosmetics.price", 2500));
        this.messages = new Messages(messagesWithJarFallback(config));
    }

    /**
     * The {@code messages} section, with any key missing from it filled in from the jar's own
     * bundled {@code config.yml}. Live's {@code plugins/Market/config.yml} only gets a key a newer
     * jar introduced once {@code MarketPlugin.onEnable()}'s {@code copyDefaults}/{@code saveConfig}
     * dance runs it through a restart; a plain {@code new MarketConfig(yaml)} in a test, or a
     * config.yml that genuinely predates a key, never goes through that JavaPlugin lifecycle at all.
     * Doing the fallback here instead makes it provable by a unit test and keeps {@code Messages}
     * itself (core/) untouched - its own missing-key fallback is still the visible
     * {@code [missing message: ...]} marker, now only reached if a key is absent from BOTH files.
     */
    private static ConfigurationSection messagesWithJarFallback(FileConfiguration config) {
        YamlConfiguration merged = new YamlConfiguration();
        try (InputStream stream = MarketConfig.class.getResourceAsStream("/config.yml")) {
            if (stream != null) {
                ConfigurationSection jarDefaults = YamlConfiguration
                        .loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getConfigurationSection("messages");
                copyStrings(jarDefaults, merged);
            }
        } catch (IOException e) {
            // A jar resource stream only throws on close(); nothing written above is lost either way.
        }
        copyStrings(config.getConfigurationSection("messages"), merged);
        return merged;
    }

    private static void copyStrings(ConfigurationSection from, ConfigurationSection into) {
        if (from == null) {
            return;
        }
        for (String key : from.getKeys(true)) {
            if (from.isString(key)) {
                into.set(key, from.getString(key));
            }
        }
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

    /** The Wanted bounty for a target who just died, before the random factor (Joe, 2026-10-09: $50). */
    public double wantedBase() {
        return wantedBase;
    }

    /** What each hour of play time alive adds to the bounty ($25). */
    public double wantedPerHour() {
        return wantedPerHour;
    }

    /** The most a bounty is ever worth, random factor included ($500). */
    public long wantedCap() {
        return wantedCap;
    }

    /** The random factor rolled once per bounty lies in 1 +- spread (0.1). */
    public double wantedSpread() {
        return wantedSpread;
    }

    /** How often the target comes from the online players rather than everyone who ever joined (0.8). */
    public double wantedOnlineChance() {
        return wantedOnlineChance;
    }

    /** What a head taken before the bounty was posted (or with no kill stamp at all) pays, as a
     * fraction of the full price (Joe, 2026-10-09: 0.5, lowered from 0.75 the same day; a fresh kill pays full). */
    public double wantedOldHeadShare() {
        return wantedOldHeadShare;
    }

    /** What a target who played and didn't die before the board moved on is paid, as a fraction of
     * the bounty's value then (Joe, 2026-10-09: 0.5). */
    public double wantedSurvivorShare() {
        return wantedSurvivorShare;
    }

    /** How many players must be online when a bounty would go up (Joe, 2026-10-10: no bounties with
     * only 3 on); fewer and that day has none, and the next day tries again. */
    public int wantedMinOnline() {
        return wantedMinOnline;
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

package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Today's buyers per stall, each at a price rolled around its base. Picks and prices are random and
 * saved to {@code state.yml}, so a restart keeps the day's market and nothing about tomorrow's can be
 * worked out in advance.
 */
public final class Market {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final MarketLayout layout;
    private final Supplier<MarketConfig> config;
    private final Supplier<LocalDate> today;
    private final Random random;
    private final File stateFile;
    private final Logger logger;

    private LocalDate day;
    private Map<String, List<Offer>> picks = Map.of();

    public Market(MarketLayout layout, Supplier<MarketConfig> config, Supplier<LocalDate> today, Random random,
                  File stateFile, Logger logger) {
        this.layout = layout;
        this.config = config;
        this.today = today;
        this.random = random;
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    /** Picks new buyers if the day has changed since the last pick. Returns whether it did. */
    public boolean refresh() {
        if (today.get().equals(day)) {
            return false;
        }
        rotate();
        return true;
    }

    /** Picks new buyers now, whatever the day. */
    public void rotate() {
        MarketConfig cfg = config.get();
        Map<String, List<Offer>> next = new LinkedHashMap<>();
        for (Stall stall : layout.stalls()) {
            int cap = Math.min(stall.buys().size(), stall.slots().size());
            int count = Math.min(cap, cfg.minItems() + random.nextInt(cfg.maxItems() - cfg.minItems() + 1));
            List<Offer> pool = new ArrayList<>(stall.buys());
            Collections.shuffle(pool, random);
            List<Offer> chosen = new ArrayList<>(pool.subList(0, count));
            // Pool order on the signs, so a stall's layout reads the same from day to day.
            chosen.sort(Comparator.comparingInt(stall.buys()::indexOf));
            next.put(stall.id(), chosen.stream().map(o -> roll(o, cfg.priceSpread())).toList());
        }
        day = today.get();
        picks = next;
        save();
    }

    /** The offer at its base price times a uniform factor in [1 - spread, 1 + spread], to the cent, never below $0.01. */
    private Offer roll(Offer offer, double spread) {
        double factor = 1 + spread * (2 * random.nextDouble() - 1);
        BigDecimal price = offer.price().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        return new Offer(offer.item(), offer.amount(), price.max(MIN_PRICE));
    }

    /** Writes today's buyers onto every stall's signs and clears the unused sign spots. */
    public void apply(World world) {
        for (Stall stall : layout.stalls()) {
            SignWriter.write(world, layout.signBlock(), stall, picks.getOrDefault(stall.id(), List.of()));
        }
    }

    public List<Offer> buying(String stallId) {
        return picks.getOrDefault(stallId, List.of());
    }

    public LocalDate day() {
        return day;
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + ", picking new buyers", e);
            return;
        }
        try {
            String date = yaml.getString("day");
            day = date == null ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            logger.warning("Bad day in " + stateFile + ", picking new buyers");
            return;
        }
        Map<String, List<Offer>> loaded = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("buying");
        for (Stall stall : layout.stalls()) {
            ConfigurationSection prices = section == null ? null : section.getConfigurationSection(stall.id());
            List<Offer> offers = new ArrayList<>();
            // An item dropped from the pool since the pick just disappears from today's signs.
            for (Offer base : stall.buys()) {
                String saved = prices == null ? null : prices.getString(base.item());
                if (saved != null) {
                    try {
                        offers.add(new Offer(base.item(), base.amount(), new BigDecimal(saved)));
                    } catch (NumberFormatException e) {
                        logger.warning("Bad price for " + base.item() + " in " + stateFile + "; using its base price");
                        offers.add(base);
                    }
                }
            }
            loaded.put(stall.id(), List.copyOf(offers));
        }
        picks = loaded;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("day", day.toString());
        for (Map.Entry<String, List<Offer>> entry : picks.entrySet()) {
            for (Offer offer : entry.getValue()) {
                yaml.set("buying." + entry.getKey() + "." + offer.item(), offer.price().toPlainString());
            }
        }
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart today would pick again", e);
        }
    }
}

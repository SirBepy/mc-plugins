package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The current market day's buyers per stall, each at a price rolled around its base. A market day is
 * whatever the {@code period} supplier says (an in-game day by default). Picks and prices are random
 * and saved to {@code state.yml}, so a restart keeps the day's market and nothing about the next one
 * can be worked out in advance. The Heads stall's wanted mobs rotate with the stalls, each with
 * its own price factor for the day.
 */
public final class Market {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    // Only Market.value below needs hours-alive-from-ticks, so it stays private here rather than a
    // third copy of 20 * 60 * 60.
    private static final double TICKS_PER_HOUR = 20 * 60 * 60;

    private final MarketLayout layout;
    private final MobHeads heads;
    private final Supplier<MarketConfig> config;
    private final Supplier<String> period;
    private final Random random;
    private final File stateFile;
    private final Logger logger;

    private String day;
    private Map<String, List<Offer>> picks = Map.of();
    private Map<String, BigDecimal> headFactors = Map.of();

    public Market(MarketLayout layout, MobHeads heads, Supplier<MarketConfig> config, Supplier<String> period,
                  Random random, File stateFile, Logger logger) {
        this.layout = layout;
        this.heads = heads;
        this.config = config;
        this.period = period;
        this.random = random;
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    /** Picks new buyers if the day has changed since the last pick. Returns whether it did. */
    public boolean refresh() {
        if (period.get().equals(day)) {
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
        // After the stalls, so a seed's stall picks don't depend on the head catalogue.
        List<MobHeads.Mob> mobs = new ArrayList<>(heads.mobs());
        Collections.shuffle(mobs, random);
        // Capped by the Heads stall's own sign count too, same as a sell stall is capped by its slots.
        int headSlots = layout.headStall() == null ? 0 : layout.headStall().slots().size();
        int headCount = Math.min(Math.min(cfg.headsPerDay(), headSlots), mobs.size());
        List<MobHeads.Mob> wanted = new ArrayList<>(mobs.subList(0, headCount));
        wanted.sort(Comparator.comparingInt(heads.mobs()::indexOf));
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        for (MobHeads.Mob mob : wanted) {
            factors.put(mob.id(), BigDecimal.valueOf(factor(cfg.priceSpread())).setScale(4, RoundingMode.HALF_UP));
        }
        day = period.get();
        picks = next;
        headFactors = factors;
        save();
    }

    /** A uniform factor in [1 - spread, 1 + spread]. */
    private double factor(double spread) {
        return factor(random, spread);
    }

    /** Uniform in [1 - spread, 1 + spread]: this stall roll and the Wanted board's bounty factor
     * are the same rule, so it lives here once instead of twice. */
    static double factor(Random random, double spread) {
        return 1 + spread * (2 * random.nextDouble() - 1);
    }

    /** {@code (base + perHour * hours alive) * factor}, rounded to the dollar, never above
     * {@code cap}. The Wanted board's bounty value curve. */
    static BigDecimal value(long aliveTicks, double factor, double base, double perHour, long cap) {
        double raw = (base + perHour * aliveTicks / TICKS_PER_HOUR) * factor;
        return BigDecimal.valueOf(Math.min(cap, Math.round(raw)));
    }

    /** {@code value} times a share (old head, survivor), rounded to the dollar. */
    static BigDecimal share(BigDecimal value, double share) {
        return BigDecimal.valueOf(Math.round(value.doubleValue() * share));
    }

    /** The offer at its base price times a {@link #factor}, to the cent, never below $0.01. */
    private Offer roll(Offer offer, double spread) {
        return new Offer(offer.item(), offer.amount(), scaled(offer.price(), BigDecimal.valueOf(factor(spread))));
    }

    /** Also /market price's top and bottom, so they round exactly like a real day's roll. */
    static BigDecimal scaled(BigDecimal base, BigDecimal factor) {
        return base.multiply(factor).setScale(2, RoundingMode.HALF_UP).max(MIN_PRICE);
    }

    /** Today's wanted mobs for the Heads stall, in catalogue order. */
    public List<MobHeads.Mob> wantedMobs() {
        return headFactors.keySet().stream().map(heads::mob).filter(Objects::nonNull).toList();
    }

    /** What the Heads stall pays today for one of this head, or null when its mob isn't wanted today. */
    public BigDecimal headPrice(MobHeads.Head head) {
        BigDecimal factor = headFactors.get(head.mob());
        return factor == null ? null : scaled(head.price(), factor);
    }

    public MobHeads heads() {
        return heads;
    }

    /** Writes today's buyers onto every stall's signs, clears the unused sign spots and refreshes the price book's lectern. */
    public void apply(World world) {
        for (Stall stall : layout.stalls()) {
            SignWriter.write(world, layout.signBlock(), stall, picks.getOrDefault(stall.id(), List.of()));
        }
        if (layout.priceLectern() != null) {
            LecternWriter.write(world, layout.priceLectern(), PriceList.book(layout, heads, config.get()));
        }
    }

    public MarketLayout layout() {
        return layout;
    }

    public List<Offer> buying(String stallId) {
        return picks.getOrDefault(stallId, List.of());
    }

    public String day() {
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
        day = yaml.getString("day");
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
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        ConfigurationSection saved = yaml.getConfigurationSection("heads");
        // A mob dropped from the catalogue since the pick just stops being wanted.
        for (MobHeads.Mob mob : heads.mobs()) {
            String factor = saved == null ? null : saved.getString(mob.id());
            if (factor != null) {
                try {
                    factors.put(mob.id(), new BigDecimal(factor));
                } catch (NumberFormatException e) {
                    logger.warning("Bad head price factor for " + mob.id() + " in " + stateFile + "; using its base price");
                    factors.put(mob.id(), BigDecimal.ONE);
                }
            }
        }
        headFactors = factors;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("day", day);
        for (Map.Entry<String, List<Offer>> entry : picks.entrySet()) {
            for (Offer offer : entry.getValue()) {
                yaml.set("buying." + entry.getKey() + "." + offer.item(), offer.price().toPlainString());
            }
        }
        headFactors.forEach((mob, factor) -> yaml.set("heads." + mob, factor.toPlainString()));
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would pick again", e);
        }
    }
}

package dev.sirbepy.mc.market;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generated {@code prices.yml}: a suggested price per item, plus what enchantments, curses and
 * potion contents do to it. Built by server/market-prices/generate.mjs.
 */
public final class PriceBook {

    /**
     * What /suggested-price says about one item stack. {@code each} is null unless PRICED. {@code item}
     * is the bare item's share of it after {@code wear} (the fraction of durability used up), and
     * {@code enchants} the rest, normal enchantments before curses.
     */
    public record Quote(Kind kind, Double each, double item, double wear, List<EnchantPrice> enchants) {

        static Quote of(Kind kind) {
            return new Quote(kind, null, 0, 0, List.of());
        }
    }

    public enum Kind { PRICED, UNTRADEABLE, UNKNOWN }

    /**
     * One enchantment on the item: {@code add} dollars, or for a curse a {@code factor} on the whole
     * price. Both are null for an enchantment the book has no price for (a datapack's own).
     */
    public record EnchantPrice(Enchantment enchantment, int level, Double add, Double factor) {

        boolean curse() {
            return factor != null;
        }
    }

    /** An enchantment's price at {@code maxLevel}. */
    private record EnchantValue(int maxLevel, double price) {

        /**
         * Each level below max halves the price, since two books of a level make the next one in an
         * anvil; each level past max (only from commands or wishes) adds the max price once more, so
         * a Sharpness X sword is dear but not astronomical.
         */
        double at(int level) {
            if (level <= maxLevel) {
                return price * Math.pow(2, level - maxLevel);
            }
            return price * (1 + level - maxLevel);
        }
    }

    private final Map<String, Double> items = new HashMap<>();
    private final Map<String, EnchantValue> enchantments = new HashMap<>();
    private final Map<String, Double> curses = new HashMap<>();
    private final Map<String, Double> potions = new HashMap<>();
    private final Set<String> untradeable = new HashSet<>();

    public static PriceBook load(InputStream stream) {
        return new PriceBook(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public PriceBook(ConfigurationSection root) {
        read(root.getConfigurationSection("items"), items);
        read(root.getConfigurationSection("curses"), curses);
        read(root.getConfigurationSection("potions"), potions);
        ConfigurationSection enchants = root.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                ConfigurationSection e = enchants.getConfigurationSection(key);
                if (e != null) {
                    enchantments.put(key, new EnchantValue(Math.max(1, e.getInt("max-level", 1)), e.getDouble("price")));
                }
            }
        }
        untradeable.addAll(root.getStringList("untradeable"));
    }

    private static void read(ConfigurationSection section, Map<String, Double> into) {
        if (section != null) {
            for (String key : section.getKeys(false)) {
                into.put(key, section.getDouble(key));
            }
        }
    }

    /**
     * The item's price with its potion contents, scaled down by wear (a tool at half durability is
     * worth half), plus its enchantments (stored ones too, for books), which a repair doesn't take
     * away; then each curse multiplies the lot.
     */
    public Quote quote(ItemStack stack) {
        String id = stack.getType().getKey().getKey();
        if (untradeable.contains(id)) {
            return Quote.of(Kind.UNTRADEABLE);
        }
        Double base = items.get(id);
        if (base == null) {
            return Quote.of(Kind.UNKNOWN);
        }
        double item = base;
        double wear = 0;
        List<EnchantPrice> enchants = new ArrayList<>();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta instanceof PotionMeta potion && potion.getBasePotionType() != null) {
                item += potions.getOrDefault(potion.getBasePotionType().getKey().getKey(), 0.0);
            }
            int max = stack.getType().getMaxDurability();
            if (meta instanceof Damageable damageable && max > 0 && damageable.hasDamage()) {
                wear = Math.min(max, damageable.getDamage()) / (double) max;
                item *= 1 - wear;
            }
            price(meta.getEnchants(), enchants);
            if (meta instanceof EnchantmentStorageMeta stored) {
                price(stored.getStoredEnchants(), enchants);
            }
        }
        enchants.sort(Comparator.comparing(EnchantPrice::curse));
        double each = item;
        for (EnchantPrice e : enchants) {
            if (e.add() != null) {
                each += e.add();
            }
        }
        for (EnchantPrice e : enchants) {
            if (e.factor() != null) {
                each *= e.factor();
            }
        }
        return new Quote(Kind.PRICED, each, item, wear, List.copyOf(enchants));
    }

    private void price(Map<Enchantment, Integer> enchants, List<EnchantPrice> into) {
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            NamespacedKey key = e.getKey().getKey();
            // prices.yml holds vanilla ids only; a datapack's enchantment of the same name is not that one.
            String id = NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : null;
            EnchantValue value = id == null ? null : enchantments.get(id);
            Double curse = id == null ? null : curses.get(id);
            into.add(new EnchantPrice(e.getKey(), e.getValue(), value == null ? null : value.at(e.getValue()), curse));
        }
    }
}

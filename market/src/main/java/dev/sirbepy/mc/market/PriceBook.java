package dev.sirbepy.mc.market;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The generated {@code prices.yml}: a suggested price per item, plus what enchantments and potion
 * contents add. Built by server/market-prices/generate.mjs.
 */
public final class PriceBook {

    /** What /suggested-price says about one item stack. {@code each} is null unless PRICED. */
    public record Quote(Kind kind, Double each) {
    }

    public enum Kind { PRICED, UNTRADEABLE, UNKNOWN }

    private final Map<String, Double> items = new HashMap<>();
    private final Map<String, Double> enchantments = new HashMap<>();
    private final Map<String, Double> potions = new HashMap<>();
    private final Set<String> untradeable = new HashSet<>();

    public static PriceBook load(InputStream stream) {
        return new PriceBook(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public PriceBook(ConfigurationSection root) {
        read(root.getConfigurationSection("items"), items);
        read(root.getConfigurationSection("enchantments"), enchantments);
        read(root.getConfigurationSection("potions"), potions);
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
     * The item's price, plus its enchantments (stored ones too, for books) and potion contents,
     * scaled down by wear: a tool at half durability is worth half.
     */
    public Quote quote(ItemStack stack) {
        String id = stack.getType().getKey().getKey();
        if (untradeable.contains(id)) {
            return new Quote(Kind.UNTRADEABLE, null);
        }
        Double base = items.get(id);
        if (base == null) {
            return new Quote(Kind.UNKNOWN, null);
        }
        double price = base;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            price += enchantValue(meta.getEnchants());
            if (meta instanceof EnchantmentStorageMeta stored) {
                price += enchantValue(stored.getStoredEnchants());
            }
            if (meta instanceof PotionMeta potion && potion.getBasePotionType() != null) {
                price += potions.getOrDefault(potion.getBasePotionType().getKey().getKey(), 0.0);
            }
            int max = stack.getType().getMaxDurability();
            if (meta instanceof Damageable damageable && max > 0 && damageable.hasDamage()) {
                price *= Math.max(0, max - damageable.getDamage()) / (double) max;
            }
        }
        return new Quote(Kind.PRICED, price);
    }

    private double enchantValue(Map<Enchantment, Integer> enchants) {
        double total = 0;
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            total += enchantments.getOrDefault(e.getKey().getKey().getKey(), 0.0) * e.getValue();
        }
        return total;
    }
}

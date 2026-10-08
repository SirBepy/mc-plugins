package dev.sirbepy.mc.market;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The generated {@code stalls.yml}: sign block type, the price-book lectern's and the Head collector's
 * spots (each null when the file has none) and every stall, in file order.
 */
public record MarketLayout(Material signBlock, Stall.Slot priceLectern, Stall.Slot headCollector, List<Stall> stalls) {

    public static MarketLayout load(InputStream stream) {
        return from(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public static MarketLayout from(ConfigurationSection root) {
        Material signBlock = Material.matchMaterial(root.getString("sign-block", "spruce_wall_sign"));
        if (signBlock == null) {
            throw new IllegalArgumentException("Unknown sign-block in stalls.yml: " + root.getString("sign-block"));
        }
        Stall.Slot priceLectern = spot(root.getConfigurationSection("price-lectern"));
        Stall.Slot headCollector = spot(root.getConfigurationSection("head-collector"));
        List<Stall> stalls = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("stalls");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection stall = section.getConfigurationSection(id);
                DyeColor color = DyeColor.valueOf(stall.getString("color", "black").toUpperCase(Locale.ROOT));
                List<Stall.Slot> slots = new ArrayList<>();
                for (Map<?, ?> slot : stall.getMapList("slots")) {
                    slots.add(new Stall.Slot(number(slot, "x").intValue(), number(slot, "y").intValue(),
                            number(slot, "z").intValue(),
                            BlockFace.valueOf(String.valueOf(slot.get("facing")).toUpperCase(Locale.ROOT))));
                }
                List<Offer> buys = new ArrayList<>();
                for (Map<?, ?> offer : stall.getMapList("buys")) {
                    String item = String.valueOf(offer.get("item"));
                    if (isPlayerHead(item)) {
                        throw new IllegalArgumentException("stalls.yml stall " + id + " may not buy " + item
                                + ": cartel trophies, Dragon Balls and every custom head are player heads");
                    }
                    // Via the decimal string, so 1.28 stays 1.28 rather than its binary approximation.
                    buys.add(new Offer(item, number(offer, "amount").intValue(),
                            new BigDecimal(String.valueOf(number(offer, "price")))));
                }
                stalls.add(new Stall(id, stall.getString("name", id), color, List.copyOf(slots), List.copyOf(buys)));
            }
        }
        return new MarketLayout(signBlock, priceLectern, headCollector, List.copyOf(stalls));
    }

    private static Stall.Slot spot(ConfigurationSection section) {
        return section == null ? null : new Stall.Slot(section.getInt("x"), section.getInt("y"), section.getInt("z"),
                BlockFace.valueOf(section.getString("facing", "north").toUpperCase(Locale.ROOT)));
    }

    /**
     * Whether an Essentials item name means a player head: the vanilla ids, with or without the
     * namespace, and Essentials 2.22's only alias for one, {@code playerhead}.
     */
    static boolean isPlayerHead(String item) {
        String key = item.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("_", "");
        return key.equals("playerhead") || key.equals("playerwallhead");
    }

    private static Number number(Map<?, ?> map, String key) {
        if (map.get(key) instanceof Number n) {
            return n;
        }
        throw new IllegalArgumentException("stalls.yml entry " + map + " needs a number for " + key);
    }
}

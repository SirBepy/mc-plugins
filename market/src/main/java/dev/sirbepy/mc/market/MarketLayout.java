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

/** The generated {@code stalls.yml}: sign block type plus every stall, in file order. */
public record MarketLayout(Material signBlock, List<Stall> stalls) {

    public static MarketLayout load(InputStream stream) {
        return from(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public static MarketLayout from(ConfigurationSection root) {
        Material signBlock = Material.matchMaterial(root.getString("sign-block", "spruce_wall_sign"));
        if (signBlock == null) {
            throw new IllegalArgumentException("Unknown sign-block in stalls.yml: " + root.getString("sign-block"));
        }
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
                    // Via the decimal string, so 1.28 stays 1.28 rather than its binary approximation.
                    buys.add(new Offer(String.valueOf(offer.get("item")), number(offer, "amount").intValue(),
                            new BigDecimal(String.valueOf(number(offer, "price")))));
                }
                stalls.add(new Stall(id, color, List.copyOf(slots), List.copyOf(buys)));
            }
        }
        return new MarketLayout(signBlock, List.copyOf(stalls));
    }

    private static Number number(Map<?, ?> map, String key) {
        if (map.get(key) instanceof Number n) {
            return n;
        }
        throw new IllegalArgumentException("stalls.yml entry " + map + " needs a number for " + key);
    }
}

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The generated {@code stalls.yml}: sign block type, the price-book lectern's spot (null when the
 * file has none), the Heads stall's 4 sign spots, the Wanted board's sign and head spots, every
 * shop stall (Homes, Cosmetics, ... - only their look is known so far, a later builder adds their
 * behaviour) and every sell stall, in file order.
 */
public record MarketLayout(Material signBlock, Stall.Slot priceLectern, HeadStall headStall, WantedSpot wantedBoard,
                           Map<String, Shop> shops, List<Stall> stalls) {

    /**
     * The Heads stall: a wall sign per mob Market wants today, dyed like a regular stall. It has no
     * price pool of its own in stalls.yml - today's wanted mobs come from heads.yml via Market, same
     * as the old Head collector NPC it replaces (Joe, 2026-10-09: "i just wanted a stall that buys
     * heads").
     */
    public record HeadStall(String name, DyeColor color, List<Stall.Slot> slots) {
    }

    /** Player of the Day: a player_wall_head at {@code head} and a wall sign at {@code sign}. */
    public record WantedSpot(Stall.Slot sign, Stall.Slot head) {
    }

    /** A later builder's stall (Homes, Cosmetics, ...): only its sign spot and look are known yet. */
    public record Shop(String name, DyeColor color, Stall.Slot sign) {
    }

    public static MarketLayout load(InputStream stream) {
        return from(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public static MarketLayout from(ConfigurationSection root) {
        Material signBlock = Material.matchMaterial(root.getString("sign-block", "spruce_wall_sign"));
        if (signBlock == null) {
            throw new IllegalArgumentException("Unknown sign-block in stalls.yml: " + root.getString("sign-block"));
        }
        Stall.Slot priceLectern = spot(root.getConfigurationSection("price-lectern"));
        HeadStall headStall = headStall(root.getConfigurationSection("head-stall"));
        WantedSpot wantedBoard = wantedSpot(root.getConfigurationSection("wanted-board"));
        Map<String, Shop> shops = shops(root.getConfigurationSection("shops"));
        List<Stall> stalls = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("stalls");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection stall = section.getConfigurationSection(id);
                DyeColor color = DyeColor.valueOf(stall.getString("color", "black").toUpperCase(Locale.ROOT));
                List<Stall.Slot> slots = new ArrayList<>();
                for (Map<?, ?> slot : stall.getMapList("slots")) {
                    slots.add(slot(slot));
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
        return new MarketLayout(signBlock, priceLectern, headStall, wantedBoard, shops, List.copyOf(stalls));
    }

    private static HeadStall headStall(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        DyeColor color = DyeColor.valueOf(section.getString("color", "black").toUpperCase(Locale.ROOT));
        List<Stall.Slot> slots = new ArrayList<>();
        for (Map<?, ?> slot : section.getMapList("slots")) {
            slots.add(slot(slot));
        }
        return new HeadStall(section.getString("name", "Heads"), color, List.copyOf(slots));
    }

    private static WantedSpot wantedSpot(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new WantedSpot(spot(section.getConfigurationSection("sign")), spot(section.getConfigurationSection("head")));
    }

    private static Map<String, Shop> shops(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Shop> shops = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection shop = section.getConfigurationSection(id);
            if (shop == null) {
                continue;
            }
            DyeColor color = DyeColor.valueOf(shop.getString("color", "black").toUpperCase(Locale.ROOT));
            shops.put(id, new Shop(shop.getString("name", id), color, spot(shop.getConfigurationSection("sign"))));
        }
        return Map.copyOf(shops);
    }

    private static Stall.Slot spot(ConfigurationSection section) {
        return section == null ? null : new Stall.Slot(section.getInt("x"), section.getInt("y"), section.getInt("z"),
                BlockFace.valueOf(section.getString("facing", "north").toUpperCase(Locale.ROOT)));
    }

    private static Stall.Slot slot(Map<?, ?> slot) {
        return new Stall.Slot(number(slot, "x").intValue(), number(slot, "y").intValue(), number(slot, "z").intValue(),
                BlockFace.valueOf(String.valueOf(slot.get("facing")).toUpperCase(Locale.ROOT)));
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

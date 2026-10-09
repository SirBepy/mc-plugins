package dev.sirbepy.mc.merfolk.trade;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The wish's three sea professions. Each borrows a vanilla job (its job site block and robe), and a
 * merfolk only becomes one when it takes that job at a job site that is underwater; on land the same
 * job stays the vanilla one.
 */
public enum Specialist {

    FISHER("fisherman", "Fisher"),
    MASON("mason", "Mason"),
    TREASURER("cartographer", "Treasurer");

    /** Vanilla profession key, compared by string so nothing here touches the registry at class load. */
    private final String professionKey;
    private final String title;

    Specialist(String professionKey, String title) {
        this.professionKey = professionKey;
        this.title = title;
    }

    public String title() {
        return title;
    }

    public String key() {
        return name().toLowerCase();
    }

    public static Specialist forProfession(Villager.Profession profession) {
        String key = profession.getKey().getKey();
        for (Specialist specialist : values()) {
            if (specialist.professionKey.equals(key)) return specialist;
        }
        return null;
    }

    public static Specialist byKey(String key) {
        for (Specialist specialist : values()) {
            if (specialist.key().equals(key)) return specialist;
        }
        return null;
    }

    /**
     * Every trade a specialist of this kind offers up to {@code level} (1 novice .. 5 master), in the
     * order they unlock. Vanilla adds a job's trades level by level; specialists do the same so
     * trading with them still levels them up.
     */
    public List<MerchantRecipe> tradesUpTo(int level, UUID villager) {
        List<List<MerchantRecipe>> byLevel = byLevel(villager);
        List<MerchantRecipe> trades = new ArrayList<>();
        for (int i = 0; i < Math.min(level, byLevel.size()); i++) {
            trades.addAll(byLevel.get(i));
        }
        return trades;
    }

    private List<List<MerchantRecipe>> byLevel(UUID villager) {
        return switch (this) {
            case FISHER -> List.of(
                    List.of(Trades.sell(1, Material.COD, 6, 16, 2), Trades.buy(Material.KELP, 24, 16, 2)),
                    List.of(Trades.sell(1, Material.INK_SAC, 4, 12, 5), Trades.sell(1, Material.SALMON, 6, 16, 5)),
                    List.of(Trades.sell(2, Material.SEA_PICKLE, 4, 12, 10), Trades.sell(2, Material.TROPICAL_FISH, 3, 12, 10)),
                    List.of(Trades.sell(3, Trades.coral(villager, 1), 4, 12, 15), Trades.sell(3, Trades.coralFan(villager, 2), 4, 12, 15)),
                    List.of(Trades.sell(5, Material.NAUTILUS_SHELL, 1, 8, 30)));
            case MASON -> List.of(
                    List.of(Trades.sell(1, Material.PRISMARINE_SHARD, 8, 16, 1), Trades.sell(1, Material.PRISMARINE, 4, 16, 1)),
                    List.of(Trades.sell(1, Material.PRISMARINE_BRICKS, 4, 16, 5), Trades.sell(2, Material.PRISMARINE_CRYSTALS, 4, 12, 5)),
                    List.of(Trades.sell(1, Material.DARK_PRISMARINE, 4, 16, 10), Trades.sell(1, Material.SEA_LANTERN, 1, 12, 10)),
                    List.of(Trades.sell(2, Trades.coralBlock(villager, 3), 4, 12, 15), Trades.sell(2, Trades.coralBlock(villager, 4), 4, 12, 15)),
                    List.of(Trades.sell(3, Material.SEA_LANTERN, 4, 12, 30)));
            // All three rares from the first day: the wish priced them, and kingdom-wide caps
            // (RareLedger), not levels, are what keep them rare.
            case TREASURER -> List.of(List.of(
                    Trades.rare(4, Material.TRIDENT, 1, 10),
                    Trades.rare(2, Material.SPONGE, 3, 10),
                    Trades.rare(8, Material.HEART_OF_THE_SEA, 1, 10)));
        };
    }
}

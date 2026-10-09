package dev.sirbepy.mc.merfolk.trade;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;

import java.util.UUID;

/**
 * The one extra sea trade a merfolk with an ordinary (non-specialist) job offers on top of its
 * vanilla trades. Commons only, at novice prices; never a rare.
 */
public final class SeaTrade {

    private SeaTrade() {
    }

    /** Null for jobs with no trades (none, nitwit). */
    public static MerchantRecipe forProfession(Villager.Profession profession, UUID villager) {
        return switch (profession.getKey().getKey()) {
            case "farmer" -> Trades.sell(1, Material.KELP, 24, 12, 2);
            case "fisherman", "cleric" -> Trades.sell(2, Material.SEA_PICKLE, 4, 12, 2);
            case "shepherd", "librarian" -> Trades.sell(1, Material.INK_SAC, 4, 12, 2);
            case "fletcher", "toolsmith", "butcher" -> Trades.sell(1, Material.COD, 6, 12, 2);
            case "armorer", "weaponsmith" -> Trades.sell(1, Material.SALMON, 6, 12, 2);
            case "cartographer" -> Trades.sell(1, Material.SEAGRASS, 8, 12, 2);
            case "leatherworker" -> Trades.sell(2, Material.TROPICAL_FISH, 3, 12, 2);
            case "mason" -> Trades.sell(3, Trades.coral(villager, 5), 4, 12, 2);
            default -> null;
        };
    }
}

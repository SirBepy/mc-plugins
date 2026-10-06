package dev.sirbepy.mc.haru.shop.catalog;

import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Offer;
import dev.sirbepy.mc.haru.shop.Restock;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;

/**
 * Haru's permanent emerald trades, open to every player (only the owner's trust unlocks the deeper
 * shelves). Joe's balance spec (2026-10-06) keeps them 15-25% better than the same vanilla trade, with
 * limited stock that comes back every few Minecraft days, so Haru never undercuts the spawn market or
 * player shops: only things a vanilla villager or wandering trader sells are here.
 *
 * <p>Each line names the vanilla trade it beats, read from the 26.3 jar's
 * {@code data/minecraft/villager_trade/} files. Enchanted books compare against the librarian's classic
 * average price (2 + half of (5 + 10 x level) + 3 x level, doubled for Mending), which also wants a book.
 */
public final class EverydayWares {

    private EverydayWares() {
    }

    public static List<Offer> offers() {
        return List.of(
                ware("ev_golden_carrots", Material.GOLDEN_CARROT, 6, 5, 4, Tier.STRANGER), // farmer 3E -> 3
                ware("ev_bread", Material.BREAD, 15, 2, 4, Tier.STRANGER), // farmer 1E -> 6
                ware("ev_porkchops", Material.COOKED_PORKCHOP, 6, 1, 6, Tier.STRANGER), // butcher 1E -> 5
                ware("ev_arrows", Material.ARROW, 20, 1, 6, Tier.STRANGER), // fletcher 1E -> 16
                ware("ev_cherry_logs", Material.CHERRY_LOG, 10, 1, 8, Tier.STRANGER), // wandering trader 1E -> 8
                ware("ev_glass", Material.GLASS, 5, 1, 8, Tier.STRANGER), // librarian 1E -> 4
                ware("ev_bricks", Material.BRICK, 12, 1, 6, Tier.STRANGER), // mason 1E -> 10
                ware("ev_lanterns", Material.LANTERN, 6, 5, 3, Tier.STRANGER), // librarian 1E -> 1
                ware("ev_redstone", Material.REDSTONE, 5, 2, 4, Tier.STRANGER), // cleric 1E -> 2
                ware("ev_lapis", Material.LAPIS_LAZULI, 6, 5, 4, Tier.STRANGER), // cleric 1E -> 1

                ware("ev_pumpkin_pie", Material.PUMPKIN_PIE, 5, 1, 4, Tier.ACQUAINTANCE), // farmer 1E -> 4
                ware("ev_moss", Material.MOSS_BLOCK, 5, 2, 3, Tier.ACQUAINTANCE), // wandering trader 1E -> 2
                ware("ev_cherry_sapling", Material.CHERRY_SAPLING, 1, 4, 2, Tier.ACQUAINTANCE), // wandering trader 5E -> 1
                ware("ev_saddle", Material.SADDLE, 1, 5, 1, Tier.ACQUAINTANCE), // leatherworker 6E -> 1
                ware("ev_name_tags", Material.NAME_TAG, 6, 5, 1, Tier.ACQUAINTANCE), // wandering trader 1E -> 1
                ware("ev_xp_bottles", Material.EXPERIENCE_BOTTLE, 2, 5, 4, Tier.ACQUAINTANCE), // cleric 3E -> 1

                ware("ev_ender_pearl", Material.ENDER_PEARL, 1, 4, 4, Tier.FRIEND), // cleric 5E -> 1
                ware("ev_bookshelf", Material.BOOKSHELF, 1, 7, 3, Tier.FRIEND), // librarian 9E -> 1
                ware("ev_quartz", Material.QUARTZ_BLOCK, 6, 5, 3, Tier.FRIEND), // mason 1E -> 1
                ware("ev_cake", Material.CAKE, 2, 5, 1, Tier.FRIEND), // farmer 3E -> 1
                book("ev_book_unbreaking", 23, Tier.FRIEND, Enchantment.UNBREAKING, 3), // librarian ~28E
                book("ev_book_mending", 20, Tier.FRIEND, Enchantment.MENDING, 1), // librarian ~24E

                book("ev_book_efficiency", 37, Tier.TRUSTED, Enchantment.EFFICIENCY, 5), // librarian ~44E
                book("ev_book_protection", 30, Tier.TRUSTED, Enchantment.PROTECTION, 4), // librarian ~36E
                book("ev_book_silk_touch", 10, Tier.TRUSTED, Enchantment.SILK_TOUCH, 1), // librarian ~12E

                book("ev_book_fortune", 23, Tier.KINDRED, Enchantment.FORTUNE, 3), // librarian ~28E
                book("ev_book_sharpness", 37, Tier.KINDRED, Enchantment.SHARPNESS, 5), // librarian ~44E
                book("ev_book_looting", 23, Tier.KINDRED, Enchantment.LOOTING, 3), // librarian ~28E
                book("ev_book_feather_falling", 30, Tier.KINDRED, Enchantment.FEATHER_FALLING, 4)); // librarian ~36E
    }

    private static Offer ware(String id, Material material, int amount, int emeralds, int stock, Tier tier) {
        return Offer.of(id, () -> Goods.of(material, amount))
                .emeralds(emeralds).stock(stock, Restock.ROTATION).tier(tier);
    }

    private static Offer book(String id, int emeralds, Tier tier, Enchantment enchantment, int level) {
        return Offer.of(id, () -> Goods.book(enchantment, level))
                .emeralds(emeralds).stock(1, Restock.ROTATION).tier(tier);
    }
}

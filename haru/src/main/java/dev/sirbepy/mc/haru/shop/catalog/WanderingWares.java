package dev.sirbepy.mc.haru.shop.catalog;

import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Offer;
import dev.sirbepy.mc.haru.shop.Restock;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.List;
import java.util.function.Supplier;

/**
 * The rotating shelf (owner only): a handful of rarer wares, a fresh set every 3 Minecraft days, plus
 * the pool a Legendary Trade is drawn from (about 1 rotation in 5, single use). Every rotation's stock
 * is its own, and everything here is bound to the buyer so none of it can be resold. Gear stays at
 * vanilla's own maximum enchantments (rulebook: no over-max, nothing End-only while the End is closed)
 * and is never netherite (Joe, 2026-10-06), with one exception he asked for: a second Katana, the
 * server pack's {@code katana} look on a netherite sword, sold once ever.
 *
 * <p>A rotation lasts about an hour of real time, so the strong wares ({@link #rare}) and every Legendary
 * Trade also carry a weekly cap: Joe wants OP items in low stock however often the shelf turns over.
 */
public final class WanderingWares {

    private WanderingWares() {
    }

    public static List<Offer> pool() {
        return List.of(
                ware("rot_trim_silence", Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 18, Tier.FRIEND),
                ware("rot_trim_ward", Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 10, Tier.ACQUAINTANCE),
                ware("rot_trim_eye", Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 10, Tier.ACQUAINTANCE),
                ware("rot_trim_vex", Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 10, Tier.ACQUAINTANCE),
                ware("rot_trim_spire", Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 8, Tier.STRANGER),
                ware("rot_trim_flow", Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 6, Tier.STRANGER),
                ware("rot_trim_bolt", Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 6, Tier.STRANGER),
                ware("rot_trim_rib", Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 6, Tier.STRANGER),
                ware("rot_trim_wild", Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 4, Tier.STRANGER),
                rare("rot_heart_of_the_sea", Material.HEART_OF_THE_SEA, 20, Tier.FRIEND),
                ware("rot_nautilus", Material.NAUTILUS_SHELL, 1, 4, Tier.STRANGER), // wandering trader 5E -> 1
                ware("rot_disc_pigstep", Material.MUSIC_DISC_PIGSTEP, 1, 10, Tier.STRANGER),
                ware("rot_disc_otherside", Material.MUSIC_DISC_OTHERSIDE, 1, 10, Tier.STRANGER),
                ware("rot_disc_relic", Material.MUSIC_DISC_RELIC, 1, 8, Tier.STRANGER),
                ware("rot_disc_creator", Material.MUSIC_DISC_CREATOR, 1, 12, Tier.ACQUAINTANCE),
                ware("rot_disc_precipice", Material.MUSIC_DISC_PRECIPICE, 1, 8, Tier.STRANGER),
                ware("rot_sniffer_egg", Material.SNIFFER_EGG, 1, 10, Tier.STRANGER),
                offer("rot_blue_axolotl", Goods::blueAxolotl, 12, Tier.ACQUAINTANCE),
                ware("rot_echo_shards", Material.ECHO_SHARD, 4, 8, Tier.FRIEND),
                rare("rot_heavy_core", Material.HEAVY_CORE, 32, Tier.TRUSTED),
                rare("rot_totem", Material.TOTEM_OF_UNDYING, 24, Tier.FRIEND),
                rare("rot_enchanted_apple", Material.ENCHANTED_GOLDEN_APPLE, 40, Tier.TRUSTED),
                ware("rot_ominous_bottle", Material.OMINOUS_BOTTLE, 1, 10, Tier.ACQUAINTANCE),
                rare("rot_trial_key", Material.OMINOUS_TRIAL_KEY, 16, Tier.FRIEND),
                rare("rot_wither_skull", Material.WITHER_SKELETON_SKULL, 20, Tier.TRUSTED),
                ware("rot_breeze_rods", Material.BREEZE_ROD, 8, 6, Tier.STRANGER),
                rare("rot_trident", Material.TRIDENT, 16, Tier.FRIEND),
                ware("rot_turtle_helmet", Material.TURTLE_HELMET, 1, 6, Tier.STRANGER),
                ware("rot_scutes", Material.ARMADILLO_SCUTE, 8, 4, Tier.STRANGER),
                ware("rot_glow_ink", Material.GLOW_INK_SAC, 16, 2, Tier.STRANGER),
                ware("rot_sea_lanterns", Material.SEA_LANTERN, 32, 6, Tier.STRANGER),
                ware("rot_pink_petals", Material.PINK_PETALS, 32, 1, Tier.STRANGER),
                ware("rot_spore_blossoms", Material.SPORE_BLOSSOM, 2, 4, Tier.STRANGER),
                ware("rot_azalea", Material.FLOWERING_AZALEA, 8, 2, Tier.STRANGER),
                book("rot_book_soul_speed", 8, Tier.STRANGER, Enchantment.SOUL_SPEED, 3),
                book("rot_book_swift_sneak", 12, Tier.FRIEND, Enchantment.SWIFT_SNEAK, 3),
                book("rot_book_frost_walker", 6, Tier.STRANGER, Enchantment.FROST_WALKER, 2),
                book("rot_book_riptide", 8, Tier.STRANGER, Enchantment.RIPTIDE, 3),
                book("rot_book_channeling", 8, Tier.STRANGER, Enchantment.CHANNELING, 1),
                book("rot_book_wind_burst", 14, Tier.TRUSTED, Enchantment.WIND_BURST, 3),
                book("rot_book_density", 8, Tier.ACQUAINTANCE, Enchantment.DENSITY, 5),
                book("rot_book_breach", 10, Tier.ACQUAINTANCE, Enchantment.BREACH, 4),
                book("rot_book_depth_strider", 6, Tier.STRANGER, Enchantment.DEPTH_STRIDER, 3),
                book("rot_book_thorns", 6, Tier.STRANGER, Enchantment.THORNS, 3));
    }

    /** Legendary Trades: one may join a rotation, priced in emeralds and Spirit Petals. */
    public static List<Offer> legendaries() {
        return List.of(
                legend("leg_blossom_blade", 48, 50, () -> Goods.named(Goods.enchanted(Material.DIAMOND_SWORD,
                                Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 3, Enchantment.SWEEPING_EDGE, 3,
                                Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                        "Blossom Blade", Goods.LEGEND, "Petals fall where it cuts.")),
                legend("leg_mountain_pick", 48, 50, () -> Goods.named(Goods.enchanted(Material.DIAMOND_PICKAXE,
                                Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3,
                                Enchantment.MENDING, 1),
                        "Pick of the Mountain Spirit", Goods.LEGEND, "The mountain opens for it.")),
                legend("leg_koi_rod", 24, 20, () -> Goods.named(Goods.enchanted(Material.FISHING_ROD,
                                Enchantment.LUCK_OF_THE_SEA, 3, Enchantment.LURE, 3, Enchantment.UNBREAKING, 3,
                                Enchantment.MENDING, 1),
                        "Koi King's Rod", Goods.LEGEND, "Every fish in the pond knows its name.")),
                legend("leg_moon_bow", 40, 30, () -> Goods.named(Goods.enchanted(Material.BOW,
                                Enchantment.POWER, 5, Enchantment.PUNCH, 2, Enchantment.FLAME, 1,
                                Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3),
                        "Bow of the Moon Rabbit", Goods.LEGEND, "Strung with moonlight.")),
                legend("leg_raijin_trident", 56, 50, () -> Goods.named(Goods.enchanted(Material.TRIDENT,
                                Enchantment.LOYALTY, 3, Enchantment.CHANNELING, 1, Enchantment.IMPALING, 5,
                                Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                        "Raijin's Trident", Goods.LEGEND, "The storm answers when it is thrown.")),
                legend("leg_sanctuary_crown", 48, 50, WanderingWares::sanctuaryCrown),
                legend("leg_fujin_mace", 64, 60, () -> Goods.named(Goods.enchanted(Material.MACE,
                                Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 3, Enchantment.UNBREAKING, 3,
                                Enchantment.MENDING, 1),
                        "Fujin's Mace", Goods.LEGEND, "Carries the wind god's bag of gales.")),
                legend("leg_golden_feast", 56, 50, () -> Goods.of(Material.ENCHANTED_GOLDEN_APPLE, 2)),
                legend("leg_katana", 64, 60, WanderingWares::katana).stock(1, Restock.ONCE),
                legend("leg_twin_totems", 48, 40, () -> Goods.named(Goods.of(Material.TOTEM_OF_UNDYING, 2),
                        "Twin Totems", Goods.LEGEND, "Carved from one cherry root.")));
    }

    /** Same look as ImMijo's own Katana (server pack, {@code custom_model_data} string {@code katana}). */
    private static ItemStack katana() {
        ItemStack item = Goods.enchanted(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5,
                Enchantment.SWEEPING_EDGE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        ItemMeta meta = item.getItemMeta();
        CustomModelDataComponent model = meta.getCustomModelDataComponent();
        model.setStrings(List.of("katana"));
        meta.setCustomModelDataComponent(model);
        item.setItemMeta(meta);
        return Goods.named(item, "Katana", Goods.LEGEND, "Its twin hangs on a wall somewhere. Only ever one more.");
    }

    private static ItemStack sanctuaryCrown() {
        ItemStack item = Goods.enchanted(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4,
                Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1, Enchantment.UNBREAKING, 3,
                Enchantment.MENDING, 1);
        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        meta.setTrim(new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.SILENCE));
        item.setItemMeta(meta);
        return Goods.named(item, "Crown of the Sanctuary", Goods.LEGEND, "Worn by the keepers before Haru.");
    }

    /** One a week at most, whichever rotations it shows up in. */
    private static Offer rare(String id, Material material, int emeralds, Tier tier) {
        return ware(id, material, 1, emeralds, tier).stock(1, Restock.REAL_WEEK);
    }

    private static Offer ware(String id, Material material, int amount, int emeralds, Tier tier) {
        return offer(id, () -> Goods.of(material, amount), emeralds, tier);
    }

    private static Offer book(String id, int emeralds, Tier tier, Enchantment enchantment, int level) {
        return offer(id, () -> Goods.book(enchantment, level), emeralds, tier);
    }

    private static Offer offer(String id, Supplier<ItemStack> item, int emeralds, Tier tier) {
        return Offer.of(id, item).emeralds(emeralds).stock(1, Restock.ROTATION).tier(tier).bindToBuyer();
    }

    private static Offer legend(String id, int emeralds, int petals, Supplier<ItemStack> item) {
        return Offer.of(id, item).emeralds(emeralds).petals(petals).stock(1, Restock.REAL_WEEK).bindToBuyer();
    }
}

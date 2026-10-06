package dev.sirbepy.mc.haru.shop.catalog;

import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.item.HaruItems;
import dev.sirbepy.mc.haru.item.Heads;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Offer;
import dev.sirbepy.mc.haru.shop.Restock;
import dev.sirbepy.mc.haru.shop.Reward;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * What Spirit Petals buy (owner only): cosmetics, the Sanctuary collection, blessings and useful
 * things. No money and nothing to resell: every item is bound to the buyer.
 */
public final class PetalExchange {

    public static final String AURA_SAKURA = "aura:sakura";
    public static final String AURA_FIREFLIES = "aura:fireflies";
    public static final String AURA_WISPS = "aura:wisps";

    private PetalExchange() {
    }

    public static List<Offer> offers(HaruItems items) {
        return List.of(
                aura("pet_aura_sakura", AURA_SAKURA, Material.CHERRY_LEAVES, "Sakura Aura",
                        "Cherry petals drift around you.", 40, Tier.STRANGER),
                aura("pet_aura_fireflies", AURA_FIREFLIES, Material.FIREFLY_BUSH, "Firefly Aura",
                        "Fireflies follow you through the dusk.", 60, Tier.ACQUAINTANCE),
                aura("pet_aura_wisps", AURA_WISPS, Material.END_ROD, "Spirit Wisp Aura",
                        "Little lights of the sanctuary keep you company.", 80, Tier.FRIEND),
                Offer.of("pet_banner", () -> Goods.named(Goods.sakuraBanner(), "Sakura Banner", Goods.SAKURA,
                        "Hang it where the wind can find it.")).petals(6).bindToBuyer(),
                Offer.of("pet_hanabi", () -> Goods.named(Goods.hanabi(16), "Hanabi", Goods.SAKURA,
                        "Fireworks that bloom pink and white.")).petals(8).bindToBuyer(),
                Offer.of("pet_shrine_robe", PetalExchange::shrineRobe).petals(10).tier(Tier.ACQUAINTANCE).bindToBuyer(),

                blessing("pet_bless_miner", "haste", 1, 60, Material.GOLDEN_PICKAXE, "Blessing of the Deep Miner",
                        "Haste II for 1 hour.", 12, Tier.STRANGER),
                blessing("pet_bless_sight", "night_vision", 0, 120, Material.GOLDEN_CARROT, "Blessing of Clear Sight",
                        "Night Vision for 2 hours.", 6, Tier.STRANGER),
                Offer.of("pet_bless_tides", Reward.bundle(
                                Reward.blessing("water_breathing", 0, 60), Reward.blessing("dolphins_grace", 0, 60)),
                        () -> icon(Material.HEART_OF_THE_SEA, "Blessing of the Tides",
                                "Water Breathing and Dolphin's Grace for 1 hour.")).petals(10),
                blessing("pet_bless_swift", "speed", 1, 60, Material.SUGAR, "Blessing of the Swift Wind",
                        "Speed II for 1 hour.", 8, Tier.STRANGER),
                blessing("pet_bless_fortune", "luck", 1, 60, Material.RABBIT_FOOT, "Blessing of Fortune",
                        "Luck II for 1 hour: better fishing and loot.", 10, Tier.STRANGER),
                blessing("pet_bless_fire", "fire_resistance", 0, 60, Material.MAGMA_CREAM, "Blessing of the Ember",
                        "Fire Resistance for 1 hour.", 12, Tier.ACQUAINTANCE),
                blessing("pet_bless_hero", "hero_of_the_village", 0, 30, Material.BELL, "Blessing of the Village Hero",
                        "Hero of the Village I for 30 minutes: villagers like you a little more.", 20, Tier.FRIEND),

                Offer.of("pet_homeward", () -> items.homewardPetal(2)).petals(6).bindToBuyer(),
                Offer.of("pet_xp", () -> Goods.of(Material.EXPERIENCE_BOTTLE, 16)).petals(8).bindToBuyer(),
                Offer.of("pet_name_tags", () -> Goods.of(Material.NAME_TAG, 3)).petals(5).bindToBuyer());
    }

    /** Each piece of the Sanctuary collection; the menu shows three of them a day. */
    public static List<Offer> collectibles() {
        return Heads.COLLECTION.stream()
                .map(c -> Offer.of("col_" + c.id(), Reward.bundle(Reward.item(c::item), new Reward.Unlock("head:" + c.id())),
                        c::item).petals(15).stock(2, Restock.REAL_DAY).bindToBuyer())
                .toList();
    }

    private static Offer aura(String id, String unlock, Material icon, String name, String lore, int petals, Tier tier) {
        return Offer.of(id, new Reward.Unlock(unlock), () -> icon(icon, name, lore, "Toggle it under Keepsakes."))
                .petals(petals).stock(1, Restock.ONCE).tier(tier);
    }

    private static Offer blessing(String id, String effect, int amplifier, int minutes, Material icon, String name,
                                  String lore, int petals, Tier tier) {
        return Offer.of(id, Reward.blessing(effect, amplifier, minutes),
                        () -> icon(icon, name, lore, "Lasts through death; buying again adds time."))
                .petals(petals).tier(tier);
    }

    static ItemStack icon(Material material, String name, String... lore) {
        return icon(material, name, Goods.SAKURA, lore);
    }

    static ItemStack icon(Material material, String name, TextColor color, String... lore) {
        return Goods.named(new ItemStack(material), name, color, lore);
    }

    private static ItemStack shrineRobe() {
        ItemStack robe = Goods.dyedLeather(Material.LEATHER_CHESTPLATE, 0xF4A7B9);
        robe.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return Goods.named(robe, "Shrine Keeper's Robe", Goods.SAKURA, "Sewn from the sanctuary's spare curtains.");
    }
}

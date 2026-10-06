package dev.sirbepy.mc.haru.shop.catalog;

import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.item.HaruItems;
import dev.sirbepy.mc.haru.item.Heads;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Offer;
import dev.sirbepy.mc.haru.shop.Restock;
import dev.sirbepy.mc.haru.shop.Reward;
import org.bukkit.Material;

import java.util.List;

/**
 * Only at {@link Tier#SAKURA_BOUND}, one tier (Joe's balance spec, 2026-10-06): unique cosmetics and
 * collectibles, blocks survival can't obtain (decoration only), and useful things inside the Dragon
 * Balls rulebook's Shenron guide values. No money, no over-max gear, nothing End-only, and every item
 * is bound to the owner so none of it can be resold. Restocks weekly; one-of-a-kind pieces once ever.
 */
public final class SecretStock {

    public static final String WARD = "ward";
    public static final String AURA_SPIRIT_GLOW = "aura:spirit_glow";
    public static final int WARD_DAYS = 7;

    private SecretStock() {
    }

    public static List<Offer> offers(HaruItems items) {
        return List.of(
                secret(Offer.of("sec_aura_spirit_glow", new Reward.Unlock(AURA_SPIRIT_GLOW),
                        () -> PetalExchange.icon(Material.GLOW_INK_SAC, "Spirit Glow Aura", Goods.LEGEND,
                                "The sanctuary's own light follows you.", "Toggle it under Keepsakes."))
                        .petals(120).stock(1, Restock.ONCE)),
                secret(Offer.of("sec_spirit_pearl", () -> Goods.named(Goods.head(Heads.PINK_ORB), "Spirit Pearl",
                        Goods.LEGEND, "A petal that never fell.", "Sanctuary Collection")).petals(40).stock(1, Restock.ONCE)),
                secret(Offer.of("sec_light", () -> Goods.of(Material.LIGHT, 16)).emeralds(4).stock(2, Restock.REAL_WEEK)),
                secret(Offer.of("sec_budding_amethyst", () -> Goods.of(Material.BUDDING_AMETHYST, 1))
                        .emeralds(20).stock(1, Restock.REAL_WEEK)),
                secret(Offer.of("sec_reinforced_deepslate", () -> Goods.of(Material.REINFORCED_DEEPSLATE, 4))
                        .emeralds(6).stock(1, Restock.REAL_WEEK)),
                secret(Offer.of("sec_petrified_slab", () -> Goods.named(Goods.of(Material.PETRIFIED_OAK_SLAB, 4),
                        "Petrified Oak Slab", Goods.SAKURA, "Stone that remembers being wood."))
                        .emeralds(1).stock(1, Restock.REAL_WEEK)),
                secret(Offer.of("sec_rebirth", items::rebirthPetal).petals(80).stock(1, Restock.REAL_WEEK)),
                secret(Offer.of("sec_spirit_horn", items::spiritHorn).emeralds(16).petals(30).stock(1, Restock.ONCE)),
                secret(Offer.of("sec_ward", new Reward.Charm(WARD, WARD_DAYS), () -> PetalExchange.icon(
                        Material.PHANTOM_MEMBRANE, "Charm of Restful Nights", Goods.LEGEND,
                        "For a week, phantoms never come for you,",
                        "however long you stay awake. Buying again adds a week.")).petals(30)));
    }

    private static Offer secret(Offer offer) {
        return offer.tier(Tier.SAKURA_BOUND).bindToBuyer();
    }
}

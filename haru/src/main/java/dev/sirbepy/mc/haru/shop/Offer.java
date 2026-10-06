package dev.sirbepy.mc.haru.shop;

import dev.sirbepy.mc.haru.rep.Tier;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/**
 * One thing Haru sells. {@code icon} is what the menu shows (for item rewards, the item itself);
 * emerald prices get the owner's reputation discount, petal prices never do. {@code bound} items are
 * bound to the buyer ({@code Bound}), so nothing from the owner-only shelves can be resold.
 */
public record Offer(String id, Reward reward, Supplier<ItemStack> icon, int emeralds, int petals, int stock,
                    Restock restock, Tier minTier, boolean bound) {

    /** Starts an offer with no price, unlimited stock and no tier gate; chain the builders. */
    public static Offer of(String id, Supplier<ItemStack> item) {
        return new Offer(id, Reward.item(item), item, 0, 0, 0, Restock.UNLIMITED, Tier.STRANGER, false);
    }

    public static Offer of(String id, Reward reward, Supplier<ItemStack> icon) {
        return new Offer(id, reward, icon, 0, 0, 0, Restock.UNLIMITED, Tier.STRANGER, false);
    }

    public Offer emeralds(int amount) {
        return new Offer(id, reward, icon, amount, petals, stock, restock, minTier, bound);
    }

    public Offer petals(int amount) {
        return new Offer(id, reward, icon, emeralds, amount, stock, restock, minTier, bound);
    }

    public Offer stock(int amount, Restock when) {
        return new Offer(id, reward, icon, emeralds, petals, amount, when, minTier, bound);
    }

    public Offer tier(Tier tier) {
        return new Offer(id, reward, icon, emeralds, petals, stock, restock, tier, bound);
    }

    public Offer bindToBuyer() {
        return new Offer(id, reward, icon, emeralds, petals, stock, restock, minTier, true);
    }

    public boolean limited() {
        return restock != Restock.UNLIMITED;
    }
}

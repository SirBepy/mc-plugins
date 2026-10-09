package dev.sirbepy.mc.merfolk.trade;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;
import java.util.UUID;

/** Small builders for vanilla-shaped trades. */
final class Trades {

    /**
     * Vanilla's own multiplier for common trades (VillagerTrades uses 0.05 for nearly every
     * emerald trade), so demand and reputation move these prices the way they move any villager's.
     */
    static final float COMMON_PRICE_MULTIPLIER = 0.05f;

    private static final List<Material> CORALS = List.of(
            Material.TUBE_CORAL, Material.BRAIN_CORAL, Material.BUBBLE_CORAL, Material.FIRE_CORAL, Material.HORN_CORAL);
    private static final List<Material> CORAL_FANS = List.of(
            Material.TUBE_CORAL_FAN, Material.BRAIN_CORAL_FAN, Material.BUBBLE_CORAL_FAN, Material.FIRE_CORAL_FAN, Material.HORN_CORAL_FAN);
    private static final List<Material> CORAL_BLOCKS = List.of(
            Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK);

    private Trades() {
    }

    /** {@code emeralds} emeralds buy {@code count} of {@code result}. */
    static MerchantRecipe sell(int emeralds, Material result, int count, int maxUses, int villagerXp) {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(result, count), 0, maxUses, true, villagerXp, COMMON_PRICE_MULTIPLIER);
        recipe.addIngredient(new ItemStack(Material.EMERALD, emeralds));
        return recipe;
    }

    /** The villager buys {@code count} of {@code wanted} for one emerald. */
    static MerchantRecipe buy(Material wanted, int count, int maxUses, int villagerXp) {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.EMERALD), 0, maxUses, true, villagerXp, COMMON_PRICE_MULTIPLIER);
        recipe.addIngredient(new ItemStack(wanted, count));
        return recipe;
    }

    /**
     * A diamond-priced rare. Discounts are off and the multiplier is 0 because vanilla's Hero of the
     * Village and cured-villager discounts cut the first ingredient, which would make a trident cost
     * fewer than the 4 diamonds the wish set.
     */
    static MerchantRecipe rare(int diamonds, Material result, int maxUses, int villagerXp) {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(result), 0, maxUses, true, villagerXp, 0f, true);
        recipe.addIngredient(new ItemStack(Material.DIAMOND, diamonds));
        return recipe;
    }

    /** One of the 5 coral colours, fixed per villager so its offer doesn't change between restarts. */
    static Material coral(UUID villager, int salt) {
        return pick(CORALS, villager, salt);
    }

    static Material coralFan(UUID villager, int salt) {
        return pick(CORAL_FANS, villager, salt);
    }

    static Material coralBlock(UUID villager, int salt) {
        return pick(CORAL_BLOCKS, villager, salt);
    }

    private static Material pick(List<Material> options, UUID villager, int salt) {
        long mixed = villager.getLeastSignificantBits() ^ villager.getMostSignificantBits() ^ (salt * 0x9E3779B97F4A7C15L);
        return options.get((int) Math.floorMod(mixed, (long) options.size()));
    }
}

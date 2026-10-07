package dev.sirbepy.mc.guild.contract;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Iron-tier supply bundles for rare contracts and trials. The dragon's cap: nothing above iron tier,
 * nothing enchanted, no shulkers or elytra. {@link #ALLOWED} is what tests hold every bundle to.
 */
public final class Bundles {

    public static final Set<Material> ALLOWED = Set.of(Material.IRON_INGOT, Material.BREAD, Material.COOKED_BEEF,
            Material.COOKED_PORKCHOP, Material.ARROW, Material.TORCH, Material.COAL, Material.GOLDEN_CARROT,
            Material.RAIL, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.LANTERN,
            Material.COOKED_SALMON, Material.CAKE);

    private static final List<List<ItemStack>> BUNDLES = List.of(
            List.of(new ItemStack(Material.IRON_INGOT, 12), new ItemStack(Material.COAL, 16)),
            List.of(new ItemStack(Material.COOKED_BEEF, 16), new ItemStack(Material.BREAD, 16)),
            List.of(new ItemStack(Material.ARROW, 48), new ItemStack(Material.TORCH, 32)),
            List.of(new ItemStack(Material.GOLDEN_CARROT, 12)),
            List.of(new ItemStack(Material.RAIL, 32), new ItemStack(Material.LANTERN, 8)),
            List.of(new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.TORCH, 32)),
            List.of(new ItemStack(Material.IRON_AXE), new ItemStack(Material.COOKED_PORKCHOP, 16)),
            List.of(new ItemStack(Material.COOKED_SALMON, 16), new ItemStack(Material.CAKE)));

    private Bundles() {
    }

    public static List<ItemStack> roll(Random random) {
        return BUNDLES.get(random.nextInt(BUNDLES.size())).stream().map(ItemStack::clone).toList();
    }

    static List<List<ItemStack>> all() {
        return BUNDLES;
    }
}

package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the Ashen Tyrant's {@code GuardianTierRow.lootTable()} reference key into real drops,
 * same shape and same deliberate simplification as {@code sentinel.SentinelLoot}: every Normal
 * kill drops the Ashen Core relic at a flat 100% rate (not a balance pass, a foundation-dispatch
 * placeholder), plus filler scaled by tier. Heroic and Mythic scale the same filler by their tier
 * ordinal so the table is never empty if a kill somehow reaches them, but their own real loot
 * (tied to mechanics that don't exist yet) is a follow-up dispatch's job.
 */
public final class TyrantLoot {

    private final AshenCore ashenCore;

    public TyrantLoot(AshenCore ashenCore) {
        this.ashenCore = ashenCore;
    }

    public List<ItemStack> forTier(Tier tier) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(ashenCore.create());
        int scale = tier.ordinal() + 1;
        drops.add(new ItemStack(Material.GOLD_INGOT, 4 * scale));
        drops.add(new ItemStack(Material.NETHER_BRICK, 8 * scale));
        return drops;
    }
}

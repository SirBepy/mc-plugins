package dev.sirbepy.mc.minibosses.sovereign;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the Void Sovereign's {@code GuardianTierRow.lootTable()} reference key into real drops,
 * same shape and same deliberate simplification as {@code sentinel.SentinelLoot}/
 * {@code tyrant.TyrantLoot}: every Normal kill drops the Hollow Crown relic at a flat 100% rate
 * (not a balance pass, a foundation-dispatch placeholder), plus filler scaled by tier. Heroic and
 * Mythic scale the same filler by their tier ordinal so the table is never empty if a kill somehow
 * reaches them, but their own real loot (tied to mechanics that don't exist yet) is a follow-up
 * dispatch's job.
 */
public final class SovereignLoot {

    private final HollowCrown hollowCrown;

    public SovereignLoot(HollowCrown hollowCrown) {
        this.hollowCrown = hollowCrown;
    }

    public List<ItemStack> forTier(Tier tier) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(hollowCrown.create());
        int scale = tier.ordinal() + 1;
        drops.add(new ItemStack(Material.ENDER_PEARL, 4 * scale));
        drops.add(new ItemStack(Material.END_STONE, 8 * scale));
        return drops;
    }
}

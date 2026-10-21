package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.relic.RelicRanks;
import dev.sirbepy.mc.minibosses.relic.Sigils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the Overgrown Sentinel's {@code GuardianTierRow.lootTable()} reference key into real
 * drops - the per-Guardian code {@code GuardianTierRow}'s own javadoc says a later todo would
 * write. Normal tier is this dispatch's real, tested content: every Normal kill drops the Heart
 * of the Wild relic (a flat 100% rate, a deliberate simplification for this foundation dispatch -
 * drop-rate tuning is a balance pass, not a scope item here) plus filler scaled by tier. Heroic
 * and Mythic scale the same filler by their tier ordinal so the table is never empty if a kill
 * somehow reaches them, but their own real loot (tied to mechanics that don't exist yet) is a
 * follow-up dispatch's job. Heroic and Mythic also drop {@link Sigils}, the tokens that rank the
 * Heart up: Heroic one Verdant Sigil, Mythic two Verdant Sigils plus one Mythic Verdant Sigil.
 */
public final class SentinelLoot {

    private final HeartOfTheWild heartOfTheWild;
    private final RelicRanks ranks;
    private final Sigils sigils;

    public SentinelLoot(HeartOfTheWild heartOfTheWild, RelicRanks ranks, Sigils sigils) {
        this.heartOfTheWild = heartOfTheWild;
        this.ranks = ranks;
        this.sigils = sigils;
    }

    public List<ItemStack> forTier(Tier tier) {
        List<ItemStack> drops = new ArrayList<>();
        // Stamped so a fresh drop shows "Rank I" in its lore like every later rank does.
        drops.add(ranks.withRank(heartOfTheWild.create(), 1));
        int scale = tier.ordinal() + 1;
        drops.add(new ItemStack(Material.EMERALD, 4 * scale));
        drops.add(new ItemStack(Material.MOSS_BLOCK, 8 * scale));
        if (tier == Tier.HEROIC) {
            drops.add(sigils.create(SentinelFight.GUARDIAN_ID, Tier.HEROIC, 1));
        } else if (tier == Tier.MYTHIC) {
            drops.add(sigils.create(SentinelFight.GUARDIAN_ID, Tier.HEROIC, 2));
            drops.add(sigils.create(SentinelFight.GUARDIAN_ID, Tier.MYTHIC, 1));
        }
        return drops;
    }
}

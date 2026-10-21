package dev.sirbepy.mc.minibosses.relic;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * The smithing-table rules, kept apart from the event so they run without an inventory:
 * <ul>
 *   <li>rank I to II: Heroic sigil in the template slot, the relic as base, Heroic sigil as addition;</li>
 *   <li>rank II to III: Mythic sigil in the template slot, the relic as base, Heroic sigil as addition.</li>
 * </ul>
 * Every sigil must belong to the relic's own Guardian. The result is the base item itself (same
 * material, same identifying PDC) one rank up, so a relic keeps working through every upgrade.
 */
public final class RelicUpgrades {

    private final Relics relics;
    private final RelicRanks ranks;
    private final Sigils sigils;

    public RelicUpgrades(Relics relics, RelicRanks ranks, Sigils sigils) {
        this.relics = relics;
        this.ranks = ranks;
        this.sigils = sigils;
    }

    /** The upgraded relic for these three smithing slots, or empty when they are not a valid upgrade. */
    public Optional<ItemStack> result(ItemStack template, ItemStack base, ItemStack addition) {
        Optional<String> guardian = relics.guardianOf(base);
        if (guardian.isEmpty()) {
            return Optional.empty();
        }
        int rank = ranks.rankOf(base);
        if (rank >= RelicRanks.MAX_RANK) {
            return Optional.empty();
        }
        Tier templateTier = rank == 1 ? Tier.HEROIC : Tier.MYTHIC;
        if (!sigils.is(template, guardian.get(), templateTier)
                || !sigils.is(addition, guardian.get(), Tier.HEROIC)) {
            return Optional.empty();
        }
        ItemStack upgraded = ranks.withRank(base, rank + 1);
        // Vanilla takes one item from each slot, so a stacked base leaves the rest behind.
        upgraded.setAmount(1);
        return Optional.of(upgraded);
    }

    /** True when the three slots hold what the registered smithing recipe matches on material alone:
     * paper, one of the relic materials, paper. Such a combination is always answered by this class,
     * with an upgrade or with no result. */
    public boolean matchesRecipeShape(ItemStack template, ItemStack base, ItemStack addition) {
        return template != null && addition != null && base != null
                && template.getType() == Material.PAPER
                && addition.getType() == Material.PAPER
                && relics.materials().contains(base.getType());
    }

    public boolean touchesSigil(ItemStack... slots) {
        for (ItemStack slot : slots) {
            if (sigils.isSigil(slot)) {
                return true;
            }
        }
        return false;
    }
}

package dev.sirbepy.mc.minibosses.relic;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which Guardian each relic belongs to. Each relic class already owns its own marker-PDC check
 * ({@code HeartOfTheWild::isHeartOfTheWild} and siblings), so this registry stores those checks by
 * method reference rather than re-reading the markers, and the rank/upgrade/active layers stay
 * generic over all three relics.
 */
public final class Relics {

    private record Entry(Material material, Predicate<ItemStack> check) {
    }

    private final Map<String, Entry> byGuardian = new LinkedHashMap<>();

    public void register(String guardianId, Material material, Predicate<ItemStack> check) {
        byGuardian.put(guardianId, new Entry(material, check));
    }

    /** The Guardian whose relic {@code item} is, or empty for anything else (vanilla look-alikes included). */
    public Optional<String> guardianOf(ItemStack item) {
        for (Map.Entry<String, Entry> entry : byGuardian.entrySet()) {
            if (entry.getValue().check().test(item)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** Every registered relic material, for the smithing recipe's base slot. */
    public List<Material> materials() {
        return byGuardian.values().stream().map(Entry::material).distinct().toList();
    }
}

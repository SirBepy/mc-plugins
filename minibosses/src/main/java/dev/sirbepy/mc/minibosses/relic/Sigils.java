package dev.sirbepy.mc.minibosses.relic;

import dev.sirbepy.mc.minibosses.registry.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Crafting tokens a Guardian drops on Heroic and Mythic kills, spent at a smithing table to rank a
 * relic up. A sigil is a {@code PAPER} stack re-skinned with a vanilla item model, so it needs no
 * resource-pack texture; it is identified only by its two PDC strings (guardian id, tier), never
 * by name or material. Adding a Guardian's sigils is one {@link Kind} line in {@link #KINDS}.
 */
public final class Sigils {

    /** Per-Guardian presentation: item names, the vanilla models borrowed for the two tiers, and the
     * relic the sigil upgrades (only used for lore wording). */
    private record Kind(String guardianName, String relicName,
                        String heroicName, NamedTextColor heroicColor, NamespacedKey heroicModel,
                        String mythicName, NamedTextColor mythicColor, NamespacedKey mythicModel) {
    }

    // Heroic borrows the turtle scute (a pale green scale) and Mythic the emerald with a glint
    // override: the emerald's colour reads "Sentinel" at a glance, and the glint plus gold name
    // keep it apart from the plain emeralds the same kill drops.
    private static final Map<String, Kind> KINDS = Map.of(
            "overgrown_sentinel", new Kind("Overgrown Sentinel", "Heart of the Wild",
                    "Verdant Sigil", NamedTextColor.GREEN, NamespacedKey.minecraft("turtle_scute"),
                    "Mythic Verdant Sigil", NamedTextColor.GOLD, NamespacedKey.minecraft("emerald")));

    private final NamespacedKey guardianKey;
    private final NamespacedKey tierKey;

    public Sigils(JavaPlugin plugin) {
        this.guardianKey = new NamespacedKey(plugin, "sigil_guardian");
        this.tierKey = new NamespacedKey(plugin, "sigil_tier");
    }

    /** A sigil stack for {@code guardianId} at {@code tier} (HEROIC or MYTHIC only). */
    public ItemStack create(String guardianId, Tier tier, int amount) {
        Kind kind = KINDS.get(guardianId);
        if (kind == null || tier == Tier.NORMAL) {
            throw new IllegalArgumentException("No " + tier + " sigil for guardian " + guardianId);
        }
        boolean mythic = tier == Tier.MYTHIC;
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(mythic ? kind.mythicName() : kind.heroicName(),
                mythic ? kind.mythicColor() : kind.heroicColor()).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(mythic ? kind.mythicModel() : kind.heroicModel());
        if (mythic) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.lore(List.of(
                gray("Shed by the " + kind.guardianName() + "."),
                gray(mythic
                        ? "At a smithing table, raises a rank II " + kind.relicName() + " to rank III."
                        : "At a smithing table, two raise a " + kind.relicName() + " to rank II.")));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(guardianKey, PersistentDataType.STRING, guardianId);
        pdc.set(tierKey, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSigil(ItemStack item) {
        return guardianOf(item).isPresent() && tierOf(item).isPresent();
    }

    public Optional<String> guardianOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer()
                .get(guardianKey, PersistentDataType.STRING));
    }

    public Optional<Tier> tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (stored == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Tier.valueOf(stored));
        } catch (IllegalArgumentException unknownTier) {
            return Optional.empty();
        }
    }

    public boolean is(ItemStack item, String guardianId, Tier tier) {
        return guardianOf(item).filter(guardianId::equals).isPresent()
                && tierOf(item).filter(tier::equals).isPresent();
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}

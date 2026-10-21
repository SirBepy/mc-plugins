package dev.sirbepy.mc.minibosses.relic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Rank (I to III) of a Guardian relic, stored as an integer PDC on the same item the relic's own
 * marker identifies. Absent means rank I, so every relic dropped before ranks existed keeps
 * working unchanged. The rank also shows as the last lore line; {@link #withRank} replaces that
 * line instead of stacking a second one, and leaves the relic's own lore untouched.
 */
public final class RelicRanks {

    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 3;

    private static final String[] ROMAN = {"I", "II", "III"};
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final NamespacedKey rankKey;

    public RelicRanks(JavaPlugin plugin) {
        this.rankKey = new NamespacedKey(plugin, "relic_rank");
    }

    public int rankOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return MIN_RANK;
        }
        Integer stored = item.getItemMeta().getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);
        return stored == null ? MIN_RANK : clamp(stored);
    }

    /** A copy of {@code item} at {@code rank} (clamped to I..III) with the rank lore line refreshed. */
    public ItemStack withRank(ItemStack item, int rank) {
        int clamped = clamp(rank);
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, clamped);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        // The rank line is always last; matching its text (not just its position) keeps a relic
        // whose own lore was never rank-stamped from losing its final real line.
        if (!lore.isEmpty() && isRankLine(lore.get(lore.size() - 1))) {
            lore.remove(lore.size() - 1);
        }
        lore.add(Component.text("Rank " + roman(clamped), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    public static String roman(int rank) {
        return ROMAN[clamp(rank) - 1];
    }

    private static boolean isRankLine(Component line) {
        String text = PLAIN.serialize(line);
        for (String numeral : ROMAN) {
            if (text.equals("Rank " + numeral)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int rank) {
        return Math.max(MIN_RANK, Math.min(MAX_RANK, rank));
    }
}

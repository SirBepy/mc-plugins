package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Builds and identifies the Dragon Radar compass, for any of the 3 sets. Identified ONLY by the
 * {@code dragonballs:radar} PDC marker (never by the custom-model-data string, which only drives the
 * resource pack's needle vs. no-signal screen and is swapped in place as the hunt state changes).
 * The companion {@code dragonballs:radar_set} marker says WHICH set it points at; its absence means
 * OVERWORLD, so every radar predating this split (including a live, already-purchased one) keeps
 * acting exactly as it does today.
 */
public final class RadarItems {

    static final String ACTIVE_MODEL = "dragon_radar";
    static final String IDLE_MODEL = "dragon_radar_idle";

    private final NamespacedKey radarKey;
    private final NamespacedKey setKey;

    public RadarItems(JavaPlugin plugin) {
        this.radarKey = new NamespacedKey(plugin, "radar");
        this.setKey = new NamespacedKey(plugin, "radar_set");
    }

    public NamespacedKey radarKey() {
        return radarKey;
    }

    public boolean isRadar(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(radarKey, PersistentDataType.BOOLEAN);
    }

    /** Which of the 3 sets {@code item} points at; {@link BallSet#OVERWORLD} for a non-radar item or
     * one with no {@code radar_set} PDC entry (every radar that predates this split). */
    public BallSet setOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return BallSet.OVERWORLD;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
        BallSet set = id == null ? null : BallSet.byId(id);
        return set == null ? BallSet.OVERWORLD : set;
    }

    /** The overworld radar: today's default purchase, unchanged. */
    public ItemStack create() {
        return create(BallSet.OVERWORLD);
    }

    /** The purchasable item for {@code set}: starts showing the active needle screen (swapped to
     * idle, if there's nothing to point at, the next time {@code RadarTask} runs). */
    public ItemStack create(BallSet set) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName(set), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Points at the nearest awake Dragon Ball.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Never says how far.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(radarKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, set.id());
        applyModel(meta, activeModel(set));
        item.setItemMeta(meta);
        return item;
    }

    /** "Dragon Radar"/"Nether Dragon Radar"/"End Dragon Radar", for purchase/shop messages. */
    public static String displayName(BallSet set) {
        String word = set.displayWord();
        return (word.isEmpty() ? "" : word + " ") + "Dragon Radar";
    }

    public boolean isShowingActive(ItemStack item) {
        return hasModel(item, activeModel(setOf(item)));
    }

    public boolean isShowingIdle(ItemStack item) {
        return hasModel(item, idleModel(setOf(item)));
    }

    /**
     * Swaps only the custom-model-data string (name/lore/PDC untouched, so the set it points at
     * never changes here). Callers must only invoke this when the active/idle state actually
     * changes: rewriting a held item's components every tick would replay the hand re-equip animation.
     */
    public void setActive(ItemStack item, boolean active) {
        BallSet set = setOf(item);
        ItemMeta meta = item.getItemMeta();
        applyModel(meta, active ? activeModel(set) : idleModel(set));
        item.setItemMeta(meta);
    }

    private static String activeModel(BallSet set) {
        return switch (set) {
            case OVERWORLD -> ACTIVE_MODEL;
            case NETHER -> "dragon_radar_nether";
            case END -> "dragon_radar_end";
        };
    }

    private static String idleModel(BallSet set) {
        return switch (set) {
            case OVERWORLD -> IDLE_MODEL;
            case NETHER -> "dragon_radar_nether_idle";
            case END -> "dragon_radar_end_idle";
        };
    }

    private boolean hasModel(ItemStack item, String model) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent() && meta.getCustomModelDataComponent().getStrings().contains(model);
    }

    private void applyModel(ItemMeta meta, String model) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(model));
        meta.setCustomModelDataComponent(cmd);
    }
}

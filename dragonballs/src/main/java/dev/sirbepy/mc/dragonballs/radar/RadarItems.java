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
 * resource pack's distance-band screens vs. the no-signal screen and is swapped in place as the
 * hunt state changes).
 * The companion {@code dragonballs:radar_set} marker says WHICH set it points at; its absence means
 * OVERWORLD, so every radar predating this split (including a live, already-purchased one) keeps
 * acting exactly as it does today.
 */
public final class RadarItems {

    static final String ACTIVE_MODEL = "dragon_radar";
    static final String IDLE_MODEL = "dragon_radar_idle";

    private static final List<Component> LORE = List.of(
            Component.text("Points at an awake Dragon Ball.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
            Component.text("The dot moves in as you get closer.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
            Component.text("Sneak + right-click: track the next ball.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

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

    /** The purchasable item for {@code set}: starts showing the "Getting warm" screen, until
     * {@code RadarTask} restamps it with the real reading (or idle) on its next run. */
    public ItemStack create(BallSet set) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(baseName(set));
        meta.lore(LORE);
        meta.getPersistentDataContainer().set(radarKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, set.id());
        applyModel(meta, activeModel(set, RadarBand.WARM));
        item.setItemMeta(meta);
        return item;
    }

    /** "Default Radar"/"Nether Dragon Radar"/"End Dragon Radar", for purchase/shop messages. */
    public static String displayName(BallSet set) {
        String word = set.displayWord();
        return word.isEmpty() ? "Default Radar" : word + " Dragon Radar";
    }

    public boolean isShowingActive(ItemStack item) {
        BallSet set = setOf(item);
        for (RadarBand band : RadarBand.values()) {
            if (hasModel(item, activeModel(set, band))) {
                return true;
            }
        }
        return false;
    }

    public boolean isShowingIdle(ItemStack item) {
        return hasModel(item, idleModel(setOf(item)));
    }

    public boolean isShowingBand(ItemStack item, RadarBand band) {
        return hasModel(item, activeModel(setOf(item), band));
    }

    /**
     * Restamps the radar's screen, name and lore for {@code reading} ({@code null} = no signal),
     * leaving the PDC (and so which set it points at) alone. Returns whether anything changed:
     * callers put the item back in the hand only then, since rewriting a held item replays the
     * hand re-equip animation. A changed name is also what makes the client pop it up over the
     * hotbar, which is how the holder reads the band and the ball without an action bar
     * (AuraSkills redraws its own every 5 ticks).
     */
    public boolean show(ItemStack item, RadarReading reading) {
        BallSet set = setOf(item);
        String model = reading == null ? idleModel(set) : activeModel(set, reading.band());
        Component name = reading == null ? baseName(set) : readingName(set, reading);
        ItemMeta meta = item.getItemMeta();
        if (hasModel(item, model) && name.equals(meta.displayName()) && LORE.equals(meta.lore())) {
            return false;
        }
        applyModel(meta, model);
        meta.displayName(name);
        meta.lore(LORE);
        item.setItemMeta(meta);
        return true;
    }

    private static Component baseName(BallSet set) {
        return Component.text(displayName(set), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
    }

    /** e.g. "Dragon Radar: 4-star (nearest), Close" or, for a ball the holder picked, "Dragon Radar: 4-star, Close". */
    static Component readingName(BallSet set, RadarReading reading) {
        Component name = Component.text(displayName(set), NamedTextColor.GOLD)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(reading.star() + "-star", NamedTextColor.YELLOW));
        if (!reading.picked()) {
            name = name.append(Component.text(" (nearest)", NamedTextColor.GRAY));
        }
        return name.append(Component.text(", ", NamedTextColor.GRAY))
                .append(Component.text(reading.band().word(), reading.band().color()))
                .decoration(TextDecoration.ITALIC, false);
    }

    static String activeModel(BallSet set, RadarBand band) {
        String base = switch (set) {
            case OVERWORLD -> ACTIVE_MODEL;
            case NETHER -> "dragon_radar_nether";
            case END -> "dragon_radar_end";
        };
        return base + band.modelSuffix();
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

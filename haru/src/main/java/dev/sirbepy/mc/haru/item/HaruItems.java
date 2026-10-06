package dev.sirbepy.mc.haru.item;

import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Haru's own items with behaviour behind them ({@code HaruItemListener}). Identified only by the
 * {@code haru:item} PDC marker, so renaming one in an anvil keeps it working and a lookalike never does.
 */
public final class HaruItems {

    public static final String HOMEWARD_PETAL = "homeward_petal";
    public static final String REBIRTH_PETAL = "rebirth_petal";
    public static final String SPIRIT_HORN = "spirit_horn";

    private final NamespacedKey key;

    public HaruItems(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "item");
    }

    public NamespacedKey key() {
        return key;
    }

    /** The marker id, or null for anything that isn't one of Haru's items. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean is(ItemStack item, String id) {
        return id.equals(idOf(item));
    }

    public ItemStack homewardPetal(int amount) {
        ItemStack item = Goods.named(new ItemStack(Material.PINK_DYE, amount), "Homeward Petal", Goods.SAKURA,
                "Hold it up and stand still for a breath:",
                "the wind carries you back to Haru.");
        return mark(Goods.glint(item), HOMEWARD_PETAL);
    }

    public ItemStack rebirthPetal() {
        ItemStack item = Goods.named(new ItemStack(Material.AMETHYST_SHARD), "Petal of Rebirth", Goods.LEGEND,
                "Carry it. If you fall, it falls instead,",
                "and everything you hold stays with you.",
                "Dragon Balls still scatter from a fallen hand.");
        return mark(Goods.glint(item), REBIRTH_PETAL);
    }

    public ItemStack spiritHorn() {
        ItemStack item = new ItemStack(Material.GOAT_HORN);
        MusicInstrumentMeta meta = (MusicInstrumentMeta) item.getItemMeta();
        meta.setInstrument(MusicInstrument.SEEK_GOAT_HORN);
        item.setItemMeta(meta);
        Goods.named(item, "Spirit Horn", Goods.LEGEND,
                "Blow it and every hostile creature",
                "within 48 blocks glows for 30 seconds.",
                "Rests for a minute after each call.");
        return mark(Goods.glint(item), SPIRIT_HORN);
    }

    private ItemStack mark(ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
}

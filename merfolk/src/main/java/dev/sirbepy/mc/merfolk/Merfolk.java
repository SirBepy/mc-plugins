package dev.sirbepy.mc.merfolk;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * What makes a villager a merfolk: a tag in its persistent data (so it survives restarts and chunk
 * unloads), its variant's villager type (the server pack repaints that type as the merfolk body) and a
 * kelp item on its head that the pack draws as fins. Everything else about it stays a vanilla villager.
 */
public final class Merfolk {

    public static final NamespacedKey TAG = new NamespacedKey("merfolk", "merfolk");
    /** On a zombie villager that was a merfolk, so curing it gives the merfolk back. */
    public static final NamespacedKey ZOMBIE_TAG = new NamespacedKey("merfolk", "zombie_merfolk");

    /**
     * The looks, one per villager type, since the client picks a villager's body texture by type alone.
     * Swamp and jungle are the two types no village generates, so ordinary villagers rarely wear them.
     */
    public enum Variant {
        KELP(Villager.Type.SWAMP, "merfolk_head"),
        CORAL(Villager.Type.JUNGLE, "merfolk_head_coral");

        public final Villager.Type type;
        public final NamespacedKey headModel;

        Variant(Villager.Type type, String headModel) {
            this.type = type;
            this.headModel = new NamespacedKey("claudesmp", headModel);
        }

        /** Fixed per villager, so a merfolk keeps its look across restarts without storing it. */
        public static Variant of(UUID id) {
            return (id.getLeastSignificantBits() & 1) == 0 ? KELP : CORAL;
        }

        static boolean isMerfolkType(Villager.Type type) {
            for (Variant v : values()) if (v.type == type) return true;
            return false;
        }
    }

    private Merfolk() {
    }

    public static boolean is(Entity entity) {
        return entity instanceof Villager && has(entity.getPersistentDataContainer(), TAG);
    }

    static boolean has(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.has(key, PersistentDataType.BYTE);
    }

    /** Tags and dresses the villager; safe to call again on one that already is a merfolk. */
    public static void make(Villager villager) {
        villager.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
        dress(villager);
    }

    /** Re-applies the look, for merfolk whose head item or type something else changed. */
    public static void dress(Villager villager) {
        Variant variant = Variant.of(villager.getUniqueId());
        if (villager.getVillagerType() != variant.type) {
            villager.setVillagerType(variant.type);
        }
        EntityEquipment equipment = villager.getEquipment();
        ItemStack helmet = equipment.getHelmet();
        if (!isHead(helmet) || !variant.headModel.equals(helmet.getItemMeta().getItemModel())) {
            equipment.setHelmet(head(variant));
        }
        // The fins are only a look; killing a merfolk must not drop a stack of kelp.
        equipment.setHelmetDropChance(0f);
    }

    static ItemStack head(Variant variant) {
        ItemStack item = new ItemStack(Material.KELP);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(variant.headModel);
        item.setItemMeta(meta);
        return item;
    }

    /** Any variant's fins. */
    static boolean isHead(ItemStack item) {
        if (item == null || item.getType() != Material.KELP || !item.hasItemMeta()) return false;
        NamespacedKey model = item.getItemMeta().getItemModel();
        for (Variant v : Variant.values()) if (v.headModel.equals(model)) return true;
        return false;
    }
}

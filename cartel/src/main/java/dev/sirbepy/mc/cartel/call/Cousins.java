package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Supplier;

/**
 * Spawns Leonel and Marco Salamanca: adult zombies fully covered by iron armor whose
 * {@code claudesmp:salamanca} equipment texture (server pack) paints the cousins' bald heads,
 * sunglasses and grey suits over every visible part of the zombie. The armor keeps iron's stats;
 * only the look changes. Players without the pack see plain zombies with their names and swords.
 */
public final class Cousins {

    public static final String TAG = "cartel.cousin";
    public static final NamespacedKey LOOK = new NamespacedKey("claudesmp", "salamanca");
    public static final String PISTOL_LOOK = "cartel_pistol";

    private final NamespacedKey key;
    private final Supplier<CartelConfig> config;

    public Cousins(Plugin plugin, Supplier<CartelConfig> config) {
        this.key = new NamespacedKey(plugin, "cousin");
        this.config = config;
    }

    public boolean isCousin(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    public Mob spawn(Location at, String name) {
        return at.getWorld().spawn(at, Zombie.class, zombie -> dress(zombie, name));
    }

    void mark(Entity entity) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        entity.addScoreboardTag(TAG);
    }

    void dress(Zombie zombie, String name) {
        CartelConfig c = config.get();
        mark(zombie);
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        zombie.setCanBreakDoors(false);
        // Never saved with the chunk: a restart or an unloaded chunk simply ends the call.
        zombie.setPersistent(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);
        zombie.customName(Component.text(name, NamedTextColor.DARK_RED));
        zombie.setCustomNameVisible(true);

        setBase(zombie, Attribute.MAX_HEALTH, c.health());
        zombie.setHealth(c.health());
        setBase(zombie, Attribute.MOVEMENT_SPEED, c.speed());
        setBase(zombie, Attribute.FOLLOW_RANGE, 64);
        // A hurt zombie can call in more zombies on hard difficulty; the cousins come alone.
        setBase(zombie, Attribute.SPAWN_REINFORCEMENTS, 0);

        EntityEquipment gear = zombie.getEquipment();
        gear.setHelmet(armor(Material.IRON_HELMET, EquipmentSlot.HEAD));
        gear.setChestplate(armor(Material.IRON_CHESTPLATE, EquipmentSlot.CHEST));
        gear.setLeggings(armor(Material.IRON_LEGGINGS, EquipmentSlot.LEGS));
        gear.setBoots(armor(Material.IRON_BOOTS, EquipmentSlot.FEET));
        gear.setItemInMainHand(sword());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            gear.setDropChance(slot, 0f);
        }
    }

    private static ItemStack armor(Material material, EquipmentSlot slot) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        EquippableComponent look = meta.getEquippable();
        look.setSlot(slot);
        look.setModel(LOOK);
        meta.setEquippable(look);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack sword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 10);
        ItemMeta meta = sword.getItemMeta();
        meta.setUnbreakable(true);
        sword.setItemMeta(meta);
        return sword;
    }

    /**
     * Marco's sidearm (see {@link Sidearm}): a plain bow, no enchantments, drawn as a black pistol by
     * the server pack's {@code cartel_pistol} look. Without the pack it is just a bow.
     */
    public static ItemStack pistol() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.setUnbreakable(true);
        CustomModelDataComponent look = meta.getCustomModelDataComponent();
        look.setStrings(List.of(PISTOL_LOOK));
        meta.setCustomModelDataComponent(look);
        bow.setItemMeta(meta);
        return bow;
    }

    private static void setBase(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}

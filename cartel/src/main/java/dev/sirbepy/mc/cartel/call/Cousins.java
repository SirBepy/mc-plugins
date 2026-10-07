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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Spawns Leonel and Marco Salamanca: adult zombies in a chainmail chestplate and leggings, with two
 * armorless pieces on the head and feet, all carrying the {@code claudesmp:salamanca} equipment
 * texture (server pack) that paints the cousins' bald heads, sunglasses, grey suits and boots over
 * every visible part of the zombie. Only the chainmail counts as armor (Joe, 2026-10-07). Players
 * without the pack see plain zombies with their names and crossbows.
 */
public final class Cousins {

    public static final String TAG = "cartel.cousin";
    public static final NamespacedKey LOOK = new NamespacedKey("claudesmp", "salamanca");
    public static final String PISTOL_LOOK = "cartel_pistol";

    private final Plugin plugin;
    private final NamespacedKey key;
    private final Supplier<CartelConfig> config;
    private final Random random = new Random();

    public Cousins(Plugin plugin, Supplier<CartelConfig> config) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "cousin");
        this.config = config;
    }

    public boolean isCousin(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    /** {@code friendly}: whoever the cousin must never fire through (see {@link RangedGoal}). */
    public Mob spawn(Location at, String name, Predicate<Entity> friendly) {
        Zombie cousin = at.getWorld().spawn(at, Zombie.class, zombie -> dress(zombie, name));
        RangedGoal.attach(plugin, cousin, friendly, random);
        return cousin;
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
        outfit().forEach((slot, item) -> {
            gear.setItem(slot, item);
            gear.setDropChance(slot, 0f);
        });
        gear.setDropChance(EquipmentSlot.OFF_HAND, 0f);
    }

    /** Everything a cousin wears and holds, by slot. */
    public static Map<EquipmentSlot, ItemStack> outfit() {
        Map<EquipmentSlot, ItemStack> outfit = new EnumMap<>(EquipmentSlot.class);
        outfit.put(EquipmentSlot.HEAD, look(Material.PAPER, EquipmentSlot.HEAD));
        outfit.put(EquipmentSlot.CHEST, look(Material.CHAINMAIL_CHESTPLATE, EquipmentSlot.CHEST));
        outfit.put(EquipmentSlot.LEGS, look(Material.CHAINMAIL_LEGGINGS, EquipmentSlot.LEGS));
        outfit.put(EquipmentSlot.FEET, look(Material.PAPER, EquipmentSlot.FEET));
        outfit.put(EquipmentSlot.HAND, crossbow());
        return outfit;
    }

    /**
     * {@code material} worn in {@code slot} with the Salamanca look. Paper has no armor value, so on
     * the head and feet it is only the look: the client draws any item whose equippable component
     * has an asset id through the armor layer, never as a held or head item.
     */
    private static ItemStack look(Material material, EquipmentSlot slot) {
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

    /**
     * The cousins' crossbow, drawn as a black pistol by the server pack's {@code cartel_pistol} look
     * (a plain crossbow without the pack). The enchantments are what Joe asked for; Power isn't a
     * crossbow enchantment and the plugin fires the bolts, so {@link RangedGoal#load} is what applies
     * all three.
     */
    public static ItemStack crossbow() {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        crossbow.addUnsafeEnchantment(Enchantment.PIERCING, RangedGoal.PIERCING);
        crossbow.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 3);
        crossbow.addUnsafeEnchantment(Enchantment.POWER, 5);
        ItemMeta meta = crossbow.getItemMeta();
        meta.setUnbreakable(true);
        CustomModelDataComponent look = meta.getCustomModelDataComponent();
        look.setStrings(List.of(PISTOL_LOOK));
        meta.setCustomModelDataComponent(look);
        crossbow.setItemMeta(meta);
        return crossbow;
    }

    private static void setBase(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}

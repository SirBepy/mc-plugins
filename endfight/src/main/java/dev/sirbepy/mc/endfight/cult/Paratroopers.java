package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * The last wave's Nether paratroopers (Joe, 2026-10-09): Incendium's castle reinforcements, piglin
 * brutes in gold armor gliding in on elytras, here dropped from high above the island when the
 * dragon is nearly dead. Built like Incendium's own
 * ({@code data/incendium/function/admin/spawn/castle/paratrooper.mcfunction}: 50 health, speed
 * 0.345, follow range 100), but their elytras never drop, and they don't turn into zombified
 * piglins outside the Nether.
 */
final class Paratroopers {

    /** The wave composition type name that spawns one of these instead of a plain mob. */
    static final String TYPE = "paratrooper";
    /** How far above the ground they start gliding. */
    static final int DROP_HEIGHT = 30;
    static final double HEALTH = 50.0;
    static final double SPEED = 0.345;
    static final double FOLLOW_RANGE = 100.0;
    static final float ELYTRA_DROP_CHANCE = 0f;

    private Paratroopers() {
    }

    static boolean isParatrooper(String typeName) {
        return TYPE.equalsIgnoreCase(typeName.trim());
    }

    /** Spawns one gliding in {@link #DROP_HEIGHT} above (x, groundY, z), already a cult mob. */
    static PiglinBrute spawn(World world, int x, int groundY, int z) {
        Location at = new Location(world, x + 0.5, groundY + DROP_HEIGHT, z + 0.5);
        return world.spawn(at, PiglinBrute.class, (Consumer<PiglinBrute>) Paratroopers::equip);
    }

    static void equip(PiglinBrute brute) {
        CultIdentity.apply(brute);
        brute.setImmuneToZombification(true);
        setBase(brute, Attribute.MAX_HEALTH, HEALTH);
        setBase(brute, Attribute.MOVEMENT_SPEED, SPEED);
        setBase(brute, Attribute.FOLLOW_RANGE, FOLLOW_RANGE);
        brute.setHealth(HEALTH);
        EntityEquipment equipment = brute.getEquipment();
        equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        equipment.setChestplate(new ItemStack(Material.ELYTRA));
        equipment.setChestplateDropChance(ELYTRA_DROP_CHANCE);
        equipment.setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        equipment.setBoots(new ItemStack(Material.GOLDEN_BOOTS));
        brute.setGliding(true);
    }

    private static void setBase(PiglinBrute brute, Attribute attribute, double value) {
        AttributeInstance instance = brute.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}

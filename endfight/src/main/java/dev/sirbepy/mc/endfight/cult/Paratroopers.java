package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/**
 * The last wave's Nether paratroopers (Joe, 2026-10-09): Incendium's castle reinforcements, piglin
 * brutes in gold armor gliding in on elytras, here leaping off the dragon when it is nearly dead.
 * Built like Incendium's own
 * ({@code data/incendium/function/admin/spawn/castle/paratrooper.mcfunction}: 50 health, speed
 * 0.345, follow range 100), but their elytras never drop, and they don't turn into zombified
 * piglins outside the Nether.
 */
final class Paratroopers {

    /** The wave composition type name that spawns one of these instead of a plain mob. */
    static final String TYPE = "paratrooper";
    static final double HEALTH = 50.0;
    static final double SPEED = 0.345;
    static final double FOLLOW_RANGE = 100.0;
    static final float ELYTRA_DROP_CHANCE = 0f;
    /** The push off the dragon, towards their target: a glide needs forward speed to carry. */
    static final double LAUNCH_SPEED = 0.8;

    private Paratroopers() {
    }

    static boolean isParatrooper(String typeName) {
        return TYPE.equalsIgnoreCase(typeName.trim());
    }

    /** Spawns one gliding from {@code at}, launched towards {@code toward} (null: straight on). */
    static PiglinBrute spawn(Location at, Location toward) {
        PiglinBrute brute = at.getWorld().spawn(at, PiglinBrute.class, (Consumer<PiglinBrute>) Paratroopers::equip);
        brute.setVelocity(launch(at, toward));
        return brute;
    }

    static Vector launch(Location from, Location toward) {
        if (toward == null) {
            return new Vector(0, -0.1, 0);
        }
        Vector flat = new Vector(toward.getX() - from.getX(), 0, toward.getZ() - from.getZ());
        if (flat.lengthSquared() < 1e-6) {
            return new Vector(0, -0.1, 0);
        }
        return flat.normalize().multiply(LAUNCH_SPEED).setY(-0.1);
    }

    static void equip(PiglinBrute brute) {
        CultIdentity.apply(brute, TYPE);
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

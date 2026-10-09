package dev.sirbepy.mc.merfolk.bed;

import dev.sirbepy.mc.merfolk.Merfolk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * How merfolk eat, find a home and multiply. Vanilla villagers breed on bread, carrots, potatoes and
 * beetroot and need a free bed; merfolk instead pick up raw cod, raw salmon, tropical fish and kelp
 * thrown to them, and need a free Kelp Bed. One bed homes one merfolk, so beds cap the population
 * the way vanilla beds cap a village.
 */
public final class Breeding {

    static final NamespacedKey FOOD = new NamespacedKey("merfolk", "food");
    /** Food points per item, mirroring vanilla's bread = 4 and crop = 1. */
    static final Map<Material, Integer> FOODS = Map.of(
            Material.COD, 4, Material.SALMON, 4, Material.TROPICAL_FISH, 4, Material.KELP, 1);
    /** Vanilla's Villager.canBreed: 12 food points. */
    static final int WILLING = 12;
    /** Vanilla villagers keep at most a few loaves; this stops one merfolk swallowing a whole stack. */
    static final int FULL = 36;
    /** Vanilla's 5 minute breeding cooldown (Villager.setAge(6000) after making a baby). */
    static final int COOLDOWN_TICKS = 6000;
    /** How far a merfolk looks for a free Kelp Bed: vanilla's bed (home POI) search radius. */
    static final double BED_RANGE = 48;
    /**
     * Vanilla villagers walk up to each other to breed. Underwater a villager's brain finds no walk
     * targets (its strolls only aim at dry land) and Paper's moveTo didn't bring two merfolk 3 blocks
     * apart together in 45 s on the e2e server, so being this close is enough.
     */
    static final double PARTNER_RANGE = 8;

    private final KelpBeds beds;
    private final Supplier<Collection<Villager>> merfolk;

    public Breeding(KelpBeds beds, Supplier<Collection<Villager>> merfolk) {
        this.beds = beds;
        this.merfolk = merfolk;
    }

    public static int food(Villager villager) {
        return villager.getPersistentDataContainer().getOrDefault(FOOD, PersistentDataType.INTEGER, 0);
    }

    static void setFood(Villager villager, int food) {
        villager.getPersistentDataContainer().set(FOOD, PersistentDataType.INTEGER, food);
    }

    /**
     * Thrown food floats up through the water, so each merfolk checks a column from its feet to a
     * little above its head rather than just its own box.
     */
    public void eat() {
        for (Villager villager : merfolk.get()) {
            if (!villager.isAdult() || food(villager) >= FULL) continue;
            for (Item item : villager.getWorld().getNearbyEntitiesByType(Item.class,
                    villager.getLocation().add(0, 1.5, 0), 1.5, 2, 1.5)) {
                int eaten = feed(villager, item);
                if (eaten > 0) villager.playPickupItemAnimation(item, eaten);
                if (food(villager) >= FULL) break;
            }
        }
    }

    /** Moves as much of the item's stack into the merfolk's food as fits; returns how many items it took. */
    static int feed(Villager villager, Item item) {
        ItemStack stack = item.getItemStack();
        Integer points = FOODS.get(stack.getType());
        if (points == null || !item.isValid()) return 0;
        int food = food(villager);
        int wanted = (FULL - food + points - 1) / points;
        int taken = Math.min(wanted, stack.getAmount());
        if (taken <= 0) return 0;
        setFood(villager, Math.min(FULL, food + taken * points));
        if (taken == stack.getAmount()) {
            item.remove();
        } else {
            stack.setAmount(stack.getAmount() - taken);
            item.setItemStack(stack);
        }
        return taken;
    }

    /** Homeless merfolk claim a free bed nearby; willing pairs near each other make a baby. */
    public void tick() {
        Collection<Villager> all = merfolk.get();
        for (Villager villager : all) {
            if (beds.homeOf(villager.getUniqueId()) != null) continue;
            KelpBed free = beds.nearestFree(villager.getLocation(), BED_RANGE);
            if (free != null) beds.claim(free, villager.getUniqueId());
        }
        Set<UUID> done = new HashSet<>();
        List<Villager> willing = all.stream().filter(Breeding::willing).toList();
        for (Villager villager : willing) {
            if (done.contains(villager.getUniqueId())) continue;
            Villager partner = nearestPartner(villager, willing, done);
            if (partner == null) continue;
            KelpBed bed = beds.nearestFree(villager.getLocation(), BED_RANGE);
            // Like vanilla: with no free bed there is no baby, and the food isn't spent.
            if (bed == null) continue;
            done.add(villager.getUniqueId());
            done.add(partner.getUniqueId());
            makeBaby(villager, partner, bed);
        }
    }

    static boolean willing(Villager villager) {
        return villager.isValid() && villager.isAdult() && villager.getAge() == 0 && !villager.isSleeping()
                && food(villager) >= WILLING;
    }

    private static Villager nearestPartner(Villager villager, List<Villager> willing, Set<UUID> done) {
        Villager best = null;
        double bestDistance = PARTNER_RANGE * PARTNER_RANGE;
        for (Villager other : willing) {
            if (other == villager || done.contains(other.getUniqueId()) || other.getWorld() != villager.getWorld()) continue;
            double distance = other.getLocation().distanceSquared(villager.getLocation());
            if (distance <= bestDistance) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void makeBaby(Villager mother, Villager father, KelpBed bed) {
        for (Villager parent : List.of(mother, father)) {
            setFood(parent, food(parent) - WILLING);
            parent.setAge(COOLDOWN_TICKS);
        }
        World world = mother.getWorld();
        for (Villager parent : List.of(mother, father)) {
            world.spawnParticle(Particle.HEART, parent.getLocation().add(0, 2.2, 0), 4, 0.3, 0.3, 0.3);
        }
        // Born on the Kelp Bed that becomes its home.
        Location at = bed.centre(world).add(0, 0.6, 0);
        Villager baby = world.spawn(at, Villager.class, v -> {
            v.setBaby();
            Merfolk.make(v);
        });
        beds.claim(bed, baby.getUniqueId());
        world.spawnParticle(Particle.HEART, at.clone().add(0, 0.8, 0), 6, 0.5, 0.4, 0.5);
    }
}

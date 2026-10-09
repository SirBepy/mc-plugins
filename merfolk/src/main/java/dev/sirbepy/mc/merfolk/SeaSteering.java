package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.bed.KelpBed;
import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Walks merfolk to the places a villager's day needs, because underwater a villager can't path:
 * its paths run along the water's surface, which a sinking merfolk never reaches (Paper's moveTo
 * didn't move one towards its Kelp Bed in 40 s on the e2e server), and turning that off made merfolk
 * shoot to the surface instead. So in water a merfolk is pushed straight towards, in order: its own
 * Kelp Bed at night, a job site it has picked but not yet claimed, and its job site in working
 * hours. Vanilla's own "climb out" boost carries it over a 1-block ledge on the way.
 */
public final class SeaSteering {

    /** Vanilla villager schedule (Schedule.VILLAGER_DEFAULT): rest from 12000, work 2000-9000. */
    static final long WORK_START = 2000;
    static final long WORK_END = 9000;
    static final long REST_START = 12000;
    /** Speed set every {@link #PERIOD_TICKS}; water drag (0.8 per tick) makes it about 1.5 blocks/s. */
    static final double PUSH = 0.15;
    public static final long PERIOD_TICKS = 4;
    /** Close enough: vanilla claims a job site from within 2 blocks of its centre. */
    static final double ARRIVED = 1.5;
    /** Beyond this the target isn't walked to (vanilla's own POI range is 48). */
    static final double RANGE = 48;

    private final KelpBeds beds;
    private final Supplier<Collection<Villager>> merfolk;

    public SeaSteering(KelpBeds beds, Supplier<Collection<Villager>> merfolk) {
        this.beds = beds;
        this.merfolk = merfolk;
    }

    public void tick() {
        for (Villager villager : merfolk.get()) {
            if (!villager.isInWater()) continue;
            Location target = target(villager);
            if (target != null) push(villager, target);
        }
    }

    Location target(Villager villager) {
        long time = villager.getWorld().getTime();
        if (time >= REST_START) {
            KelpBed home = beds.homeOf(villager.getUniqueId());
            if (home != null && home.world().equals(villager.getWorld().getName())) return home.centre(villager.getWorld());
        }
        Location potential = villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE);
        if (potential != null) return potential.toCenterLocation();
        Location job = villager.getMemory(MemoryKey.JOB_SITE);
        if (job != null && time >= WORK_START && time < WORK_END) return job.toCenterLocation();
        return null;
    }

    /**
     * Vanilla villagers find a job site by pathing to it, which never succeeds underwater (an e2e
     * merfolk 4 blocks from a sunken barrel had no job memories after 60 s). So an unemployed merfolk
     * in water is pointed at the nearest free job site block around it the way vanilla's AcquirePoi
     * would, as its potential job site; {@link #tick()} walks it there and vanilla's own
     * AssignProfessionFromJobSite gives it the job once it's within 2 blocks.
     */
    public void findJobs() {
        Collection<Villager> all = merfolk.get();
        for (Villager villager : all) {
            if (!villager.isInWater() || !villager.isAdult() || villager.getProfession() != Villager.Profession.NONE
                    || villager.getMemory(MemoryKey.POTENTIAL_JOB_SITE) != null || villager.getMemory(MemoryKey.JOB_SITE) != null) {
                continue;
            }
            Location site = nearestFreeJobSite(villager, takenSites(villager));
            if (site != null) villager.setMemory(MemoryKey.POTENTIAL_JOB_SITE, site);
        }
    }

    /** Job sites any villager near this one already works at or is heading for. */
    private static Set<Location> takenSites(Villager near) {
        Set<Location> taken = new HashSet<>();
        for (Villager other : near.getWorld().getNearbyEntitiesByType(Villager.class, near.getLocation(), RANGE)) {
            for (MemoryKey<Location> key : List.of(MemoryKey.JOB_SITE, MemoryKey.POTENTIAL_JOB_SITE)) {
                Location site = other.getMemory(key);
                if (site != null) taken.add(site.toBlockLocation());
            }
        }
        return taken;
    }

    static Location nearestFreeJobSite(Villager villager, Set<Location> taken) {
        Location at = villager.getLocation();
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -JOB_REACH; dx <= JOB_REACH; dx++) {
            for (int dy = -JOB_REACH_Y; dy <= JOB_REACH_Y; dy++) {
                for (int dz = -JOB_REACH; dz <= JOB_REACH; dz++) {
                    Block block = at.getWorld().getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (!JOB_SITES.contains(block.getType())) continue;
                    Location site = block.getLocation();
                    double distance = site.distanceSquared(at);
                    if (distance < bestDistance && !taken.contains(site)) {
                        best = site;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** How far around itself an unemployed merfolk looks for a job site block. */
    static final int JOB_REACH = 10;
    static final int JOB_REACH_Y = 3;
    /** Every vanilla job site block (PoiTypes' 13 job POIs; every cauldron is the leatherworker's). */
    static final Set<Material> JOB_SITES = EnumSet.of(Material.BARREL, Material.BLAST_FURNACE, Material.BREWING_STAND,
            Material.CARTOGRAPHY_TABLE, Material.CAULDRON, Material.WATER_CAULDRON, Material.LAVA_CAULDRON,
            Material.POWDER_SNOW_CAULDRON, Material.COMPOSTER, Material.FLETCHING_TABLE, Material.GRINDSTONE,
            Material.LECTERN, Material.LOOM, Material.SMITHING_TABLE, Material.SMOKER, Material.STONECUTTER);
    public static final long JOB_PERIOD_TICKS = 100;

    /** Sets a horizontal speed towards the target, leaving the vertical one to gravity. */
    static void push(Villager villager, Location target) {
        Location at = villager.getLocation();
        if (target.getWorld() != at.getWorld()) return;
        double dx = target.getX() - at.getX();
        double dz = target.getZ() - at.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance <= ARRIVED || distance > RANGE) return;
        Vector velocity = villager.getVelocity();
        villager.setVelocity(new Vector(dx / distance * PUSH, velocity.getY(), dz / distance * PUSH));
        villager.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0f);
    }
}

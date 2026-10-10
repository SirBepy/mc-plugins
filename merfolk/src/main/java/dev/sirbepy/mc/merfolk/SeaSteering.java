package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.WaterPath.Cell;
import dev.sirbepy.mc.merfolk.WaterPath.Step;
import dev.sirbepy.mc.merfolk.bed.KelpBed;
import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Walks and swims merfolk to the places a villager's day needs, because underwater a villager can't
 * path: its paths run along the water's surface, which a sinking merfolk never reaches (Paper's moveTo
 * didn't move one towards its Kelp Bed in 40 s on the e2e server), and turning that off made merfolk
 * shoot to the surface instead. So in water a merfolk follows its own {@link WaterPath} towards, in
 * order: its own Kelp Bed at night, a job site it has picked but not yet claimed, and its job site in
 * working hours. With none of those it wanders to a random spot on the sea floor nearby every so
 * often. Steering is a velocity push every {@link #PERIOD_TICKS}: along the floor gravity keeps it
 * down, and for a step off the floor (over a wall, up a ledge) it is made about neutrally buoyant
 * ({@link SeaBody#setAfloat}) and pushed up or down as well.
 */
public final class SeaSteering {

    /** Vanilla villager schedule (Schedule.VILLAGER_DEFAULT): rest from 12000, work 2000-9000. */
    static final long WORK_START = 2000;
    static final long WORK_END = 9000;
    static final long REST_START = 12000;
    /** Speed set every {@link #PERIOD_TICKS}; water drag (0.8 per tick) makes it about 1.5 blocks/s. */
    static final double PUSH = 0.15;
    /** Most vertical speed set towards a step above or below, about 0.4 blocks per push after drag. */
    static final double LIFT = 0.14;
    public static final long PERIOD_TICKS = 4;
    /** Close enough: vanilla claims a job site from within 2 blocks of its centre. */
    static final double ARRIVED = 1.5;
    /** Beyond this the target isn't walked to (vanilla's own POI range is 48). */
    static final double RANGE = 48;
    /**
     * Blocks one search may expand. Open water to a target 48 blocks off takes about 50; a maze that
     * runs out gets a path to the closest block found instead. Searches are rationed per tick too,
     * so a crowd of merfolk spreads its searches over several ticks.
     */
    static final int BUDGET = 1200;
    static final int SEARCHES_PER_TICK = 3;
    /** Least time between one merfolk's searches, and after a search that couldn't reach its target. */
    static final long REPLAN_TICKS = 20;
    static final long RETRY_TICKS = 100;
    /** No step reached for this long: blocked by something new (a merfolk, a placed block), search again. */
    static final long STUCK_TICKS = 40;
    /** Idle merfolk wander to a floor spot this far off, every {@link #WANDER_MIN}-{@link #WANDER_MAX} ticks. */
    static final int WANDER_RANGE = 9;
    static final long WANDER_MIN = 100;
    static final long WANDER_MAX = 300;

    private final KelpBeds beds;
    private final Supplier<Collection<Villager>> merfolk;
    private final Function<World, WaterPath.Terrain> terrain;
    private final BiConsumer<Villager, Boolean> afloat;
    private final Random random;
    private final Map<UUID, Walker> walkers = new HashMap<>();
    /** Ticks since start, counted in {@link #PERIOD_TICKS} steps. */
    private long now;

    public SeaSteering(KelpBeds beds, Supplier<Collection<Villager>> merfolk, Function<World, WaterPath.Terrain> terrain,
                       BiConsumer<Villager, Boolean> afloat, Random random) {
        this.beds = beds;
        this.merfolk = merfolk;
        this.terrain = terrain;
        this.afloat = afloat;
        this.random = random;
    }

    /** The real server's blocks, passable meaning no collision box. */
    public static Function<World, WaterPath.Terrain> blocks() {
        return world -> new BlockTerrain(world, Block::isPassable);
    }

    /** One merfolk's path and timers. */
    private static final class Walker {
        Route route;
        long nextSearch;
        long nextWander;

        Walker(long nextWander) {
            this.nextWander = nextWander;
        }
    }

    /** A path being followed: towards the block {@code goal}, or to a wander spot when {@code wander}. */
    private static final class Route {
        final List<Step> steps;
        final long goal;
        final boolean wander;
        int next;
        long progressAt;

        Route(List<Step> steps, long goal, boolean wander, long now) {
            this.steps = steps;
            this.goal = goal;
            this.wander = wander;
            this.progressAt = now;
        }

        boolean done() {
            return next >= steps.size();
        }

        boolean stuck(long now) {
            return now - progressAt > STUCK_TICKS;
        }
    }

    public void tick() {
        now += PERIOD_TICKS;
        int searches = SEARCHES_PER_TICK;
        Set<UUID> seen = new HashSet<>();
        for (Villager villager : merfolk.get()) {
            seen.add(villager.getUniqueId());
            Walker walker = walkers.computeIfAbsent(villager.getUniqueId(), id -> new Walker(now + wanderDelay()));
            if (!villager.isInWater() || villager.isTrading()) {
                walker.route = null;
                afloat.accept(villager, false);
                continue;
            }
            if (steer(villager, walker, searches > 0)) searches--;
        }
        walkers.keySet().retainAll(seen);
    }

    /** Moves one merfolk in water along; true if it used up one of this tick's searches. */
    private boolean steer(Villager villager, Walker walker, boolean maySearch) {
        Location at = villager.getLocation();
        Location target = target(villager);
        if (target != null && target.getWorld() == at.getWorld()) {
            double away = Math.hypot(target.getX() - at.getX(), target.getZ() - at.getZ());
            if (away > RANGE || (away <= ARRIVED && Math.abs(target.getY() - at.getY()) <= 2)) {
                walker.route = null;
                afloat.accept(villager, false);
                return false;
            }
            long goal = WaterPath.key(target.getBlockX(), target.getBlockY(), target.getBlockZ());
            Route route = walker.route;
            boolean searched = false;
            if ((route == null || route.wander || route.goal != goal || route.done() || route.stuck(now))
                    && maySearch && now >= walker.nextSearch) {
                route = plan(villager, target.getX(), target.getY(), target.getZ(), ARRIVED, goal, false);
                walker.route = route;
                walker.nextSearch = now + (reaches(route, target) ? REPLAN_TICKS : RETRY_TICKS);
                searched = true;
            }
            if (route == null || route.goal != goal || route.done()) {
                // No path (yet, or none that gets closer): the straight push, which still works in open water.
                afloat.accept(villager, false);
                push(villager, target);
            } else {
                follow(villager, route);
            }
            return searched;
        }
        Route route = walker.route;
        if (route != null && route.wander && !route.done() && !route.stuck(now)) {
            follow(villager, route);
            return false;
        }
        if (route != null) {
            walker.route = null;
            walker.nextWander = now + wanderDelay();
        }
        afloat.accept(villager, false);
        if (!maySearch || now < walker.nextWander) return false;
        walker.nextWander = now + wanderDelay();
        Step spot = wanderSpot(villager);
        if (spot != null) {
            walker.route = plan(villager, spot.x() + 0.5, spot.y() + 0.5, spot.z() + 0.5, 0.5,
                    WaterPath.key(spot.x(), spot.y(), spot.z()), true);
        }
        return true;
    }

    private Route plan(Villager villager, double tx, double ty, double tz, double reach, long goal, boolean wander) {
        Location at = villager.getLocation();
        WaterPath.Terrain blocks = terrain.apply(at.getWorld());
        // A merfolk sunk a hair into the floor reads as standing in it.
        int y = (int) Math.floor(at.getY() + 0.1);
        if (blocks.at(at.getBlockX(), y, at.getBlockZ()) == Cell.SOLID) y++;
        List<Step> steps = WaterPath.find(blocks, at.getBlockX(), y, at.getBlockZ(), tx, ty, tz, reach, BUDGET);
        return new Route(steps, goal, wander, now);
    }

    private static boolean reaches(Route route, Location target) {
        if (route.steps.isEmpty()) return false;
        Step last = route.steps.get(route.steps.size() - 1);
        return Math.hypot(last.x() + 0.5 - target.getX(), last.z() + 0.5 - target.getZ()) <= ARRIVED;
    }

    /**
     * Pushes towards the next step. A floor step leaves the vertical speed to gravity, which keeps the
     * merfolk on the floor; a step off the floor or above it makes the merfolk afloat and sets a
     * vertical speed towards it too.
     */
    private void follow(Villager villager, Route route) {
        Location at = villager.getLocation();
        for (int i = route.next; i < Math.min(route.next + 3, route.steps.size()); i++) {
            if (reached(at, route.steps.get(i))) {
                route.next = i + 1;
                route.progressAt = now;
            }
        }
        if (route.done()) {
            afloat.accept(villager, false);
            return;
        }
        Step step = route.steps.get(route.next);
        double dx = step.x() + 0.5 - at.getX();
        double dz = step.z() + 0.5 - at.getZ();
        double across = Math.hypot(dx, dz);
        double rise = step.y() + (step.floor() ? 0 : 0.2) - at.getY();
        boolean swim = !step.floor() || rise > 0.3;
        afloat.accept(villager, swim);
        double speed = Math.min(PUSH, across * 0.5);
        double vy = swim ? Math.max(-LIFT, Math.min(LIFT, rise * 0.25)) : villager.getVelocity().getY();
        villager.setVelocity(across < 1e-3 ? new Vector(0, vy, 0) : new Vector(dx / across * speed, vy, dz / across * speed));
        if (across > 0.05) villager.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0f);
    }

    static boolean reached(Location at, Step step) {
        return Math.hypot(at.getX() - (step.x() + 0.5), at.getZ() - (step.z() + 0.5)) < 0.6
                && at.getY() > step.y() - 0.6 && at.getY() < step.y() + 1.0;
    }

    /** A random sea floor block nearby under at least 2 blocks of water, or null if the tries found none. */
    private Step wanderSpot(Villager villager) {
        Location at = villager.getLocation();
        WaterPath.Terrain blocks = terrain.apply(at.getWorld());
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = random.nextInt(2 * WANDER_RANGE + 1) - WANDER_RANGE;
            int dz = random.nextInt(2 * WANDER_RANGE + 1) - WANDER_RANGE;
            if (dx * dx + dz * dz < 9) continue;
            int x = at.getBlockX() + dx;
            int z = at.getBlockZ() + dz;
            for (int y = at.getBlockY() + 3; y >= at.getBlockY() - 4; y--) {
                if (blocks.at(x, y, z) == Cell.WATER && blocks.at(x, y + 1, z) == Cell.WATER
                        && blocks.at(x, y - 1, z) == Cell.SOLID) {
                    return new Step(x, y, z, true);
                }
            }
        }
        return null;
    }

    private long wanderDelay() {
        return WANDER_MIN + (long) (random.nextDouble() * (WANDER_MAX - WANDER_MIN));
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

    /** Sets a horizontal speed straight towards the target, leaving the vertical one to gravity. */
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

package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.WaveSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Puts a wave's planned mobs into the world a few at a time instead of all in one tick (Joe's
 * performance rules, 2026-10-09): a wave comes out over {@code cult.waves.spread-ticks}, nothing
 * new spawns while the server's average tick time is over {@code cult.waves.max-mspt}, and the
 * queue for a world is dropped when its dragon dies.
 *
 * <p>Gate mobs march out of the fortress gates; dragon mobs are dropped off the dragon (Nether
 * troops with slow falling until they land, paratroopers gliding); damager mobs (phantoms) start
 * above the player they were sent after.
 */
public final class WaveSpawner implements Listener {

    /** How far below the dragon dropped mobs start: its wings and head hurt anything they touch. */
    static final double BELOW_DRAGON = 6.0;
    /** Never closer to the ground than this, so a low-flying dragon still gives them a fall. */
    static final int ABOVE_GROUND = 3;
    static final double DROP_SPREAD = 2.5;
    static final int SLOW_FALLING_TICKS = 60 * 20;
    static final int PHANTOM_HEIGHT = 14;
    static final double PHANTOM_SPREAD = 4.0;
    static final int MAX_CLIMB = 40;
    private static final long LANDING_CHECK_TICKS = 10L;

    private record Pending(UUID world, UUID dragon, WaveMath.PlannedSpawn spawn, int gateIndex) {
    }

    private final Plugin plugin;
    private final ArenaRegistry registry;
    private final Supplier<CultConfig> config;
    private final DoubleSupplier mspt;
    private final Logger logger;
    private final Random random = new Random();

    private final Deque<Pending> queue = new ArrayDeque<>();
    private final Set<UUID> falling = new HashSet<>();
    private int rate = 1;
    private int gateCursor;
    private long ticks;
    private BukkitTask task;

    public WaveSpawner(Plugin plugin, ArenaRegistry registry, Supplier<CultConfig> config, DoubleSupplier mspt) {
        this.plugin = plugin;
        this.registry = registry;
        this.config = config;
        this.mspt = mspt;
        this.logger = plugin.getLogger();
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Queues one wave's mobs for {@code dragon}; they start coming out on the next tick. */
    public void enqueue(EnderDragon dragon, List<WaveMath.PlannedSpawn> spawns) {
        for (WaveMath.PlannedSpawn spawn : spawns) {
            queue.addLast(new Pending(dragon.getWorld().getUID(), dragon.getUniqueId(), spawn, gateCursor++));
        }
        rate = WaveMath.perTick(queue.size(), config.get().waves().spreadTicks());
    }

    /** Mobs planned but not spawned yet; they count towards the alive cap. */
    public int queued() {
        return queue.size();
    }

    void tick() {
        ticks++;
        if (ticks % LANDING_CHECK_TICKS == 0) {
            landFallers();
        }
        if (queue.isEmpty()) {
            return;
        }
        if (mspt.getAsDouble() > config.get().waves().maxMspt()) {
            return;
        }
        for (int i = 0; i < rate && !queue.isEmpty(); i++) {
            Pending pending = queue.pollFirst();
            try {
                spawn(pending);
            } catch (RuntimeException e) {
                logger.warning("Could not spawn a " + pending.spawn().type() + " for a cult wave: " + e);
            }
        }
    }

    /** A dead dragon's army stops coming. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            UUID world = dragon.getWorld().getUID();
            queue.removeIf(p -> p.world().equals(world));
        }
    }

    private void spawn(Pending pending) {
        World world = Bukkit.getWorld(pending.world());
        if (world == null) {
            return;
        }
        WaveMath.PlannedSpawn spawn = pending.spawn();
        switch (spawn.from()) {
            case DRAGON -> spawnFromDragon(world, pending);
            case DAMAGERS -> spawnOnDamager(world, spawn);
            default -> spawnAtGate(world, spawn.type(), pending.gateIndex());
        }
    }

    private void spawnAtGate(World world, String type, int gateIndex) {
        List<WaveSpawn> points = registry.waveSpawns();
        if (points.isEmpty()) {
            logger.warning("Cult wave triggered with no wave spawn points registered; is the arena built?");
            return;
        }
        WaveSpawn point = points.get(Math.floorMod(gateIndex, points.size()));
        Location at = SpawnSpot.at(world, point.x(), point.y(), point.z());
        Mob mob = spawnMob(world, at, type);
        if (mob != null) {
            nearestFighter(world, at).ifPresent(player -> CultIdentity.sendAfter(mob, player));
        }
    }

    private void spawnFromDragon(World world, Pending pending) {
        Location at = dropPoint(world, world.getEntity(pending.dragon()));
        Location target = nearestFighter(world, at).map(Player::getLocation).orElse(null);
        String type = pending.spawn().type();
        if (Paratroopers.isParatrooper(type)) {
            PiglinBrute brute = Paratroopers.spawn(at, target);
            nearestFighter(world, at).ifPresent(player -> CultIdentity.sendAfter(brute, player));
            return;
        }
        Mob mob = spawnMob(world, at, type);
        if (mob == null) {
            return;
        }
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, true));
        falling.add(mob.getUniqueId());
        nearestFighter(world, at).ifPresent(player -> CultIdentity.sendAfter(mob, player));
    }

    private void spawnOnDamager(World world, WaveMath.PlannedSpawn spawn) {
        Player target = spawn.target() != null ? Bukkit.getPlayer(spawn.target()) : null;
        if (target == null || !target.getWorld().equals(world) || !CultRange.isFighting(target)) {
            target = nearestFighter(world, new Location(world, 0, 64, 0)).orElse(null);
        }
        if (target == null) {
            return;
        }
        Location at = target.getLocation().clone().add(
                (random.nextDouble() * 2 - 1) * PHANTOM_SPREAD, PHANTOM_HEIGHT, (random.nextDouble() * 2 - 1) * PHANTOM_SPREAD);
        Mob mob = spawnMob(world, at, spawn.type());
        if (mob != null) {
            CultIdentity.sendAfter(mob, target);
        }
    }

    /**
     * Where a mob dropped off the dragon starts: a few blocks under it, pulled in to
     * {@code cult.waves.drop-radius} of the portal if the dragon is out over the void, and at least
     * {@link #ABOVE_GROUND} over the ground there. A dragon that is gone already (or never found)
     * drops them over the portal instead.
     */
    Location dropPoint(World world, Entity dragon) {
        double radius = config.get().waves().dropRadius();
        double x = 0;
        double z = 0;
        double y = world.getHighestBlockYAt(0, 0) + 30;
        if (dragon != null && dragon.isValid()) {
            Location d = dragon.getLocation();
            double[] xz = clampToRadius(d.getX(), d.getZ(), radius);
            x = xz[0];
            z = xz[1];
            y = d.getY() - BELOW_DRAGON;
        }
        x += (random.nextDouble() * 2 - 1) * DROP_SPREAD;
        z += (random.nextDouble() * 2 - 1) * DROP_SPREAD;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int ground = world.getHighestBlockYAt(bx, bz);
        double startY = Math.max(y, ground + ABOVE_GROUND);
        // A dragon passing a pillar can put that spot inside the obsidian; climb out of it.
        int feet = (int) Math.floor(startY);
        int climbed = 0;
        while (climbed < MAX_CLIMB && (world.getBlockAt(bx, feet, bz).isSolid() || world.getBlockAt(bx, feet + 1, bz).isSolid())) {
            feet++;
            climbed++;
        }
        return new Location(world, x, climbed == 0 ? startY : feet, z);
    }

    /** (x, z) itself if within {@code radius} of the origin, else the point on that circle in the
     * same direction. */
    static double[] clampToRadius(double x, double z, double radius) {
        double distance = Math.hypot(x, z);
        if (distance <= radius || distance == 0) {
            return new double[] {x, z};
        }
        double scale = radius / distance;
        return new double[] {x * scale, z * scale};
    }

    private Mob spawnMob(World world, Location at, String type) {
        EntityType entityType = CultEntityTypes.parse(type, logger);
        if (entityType == null) {
            return null;
        }
        Entity entity = world.spawnEntity(at, entityType);
        if (!(entity instanceof Mob mob)) {
            entity.remove();
            return null;
        }
        CultIdentity.apply(mob, type);
        return mob;
    }

    /** Slow falling comes off once a dropped mob lands, so it fights at normal speed. */
    private void landFallers() {
        for (Iterator<UUID> it = falling.iterator(); it.hasNext(); ) {
            Entity entity = Bukkit.getEntity(it.next());
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                it.remove();
            } else if (living.isOnGround()) {
                living.removePotionEffect(PotionEffectType.SLOW_FALLING);
                it.remove();
            }
        }
    }

    private Optional<Player> nearestFighter(World world, Location at) {
        return CultRange.fighters(world, config.get().waves().range()).stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(at)));
    }

    /** Queued mobs per type, for {@code /endfight cult status}. */
    public Map<String, Integer> queuedByType() {
        Map<String, Integer> out = new HashMap<>();
        for (Pending pending : queue) {
            out.merge(pending.spawn().type(), 1, Integer::sum);
        }
        return out;
    }
}

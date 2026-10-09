package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.GuardPost;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Spawns the illager guards on every outpost/balcony/gatehouse/wall post, once per dragon, and
 * keeps them on their posts afterwards. Driven by its own repeating tasks rather than events: the
 * trigger is a time-based condition ("has anyone been near the portal lately"), not a single
 * moment to hook.
 */
public final class CultGuards implements Listener {

    // On the dragon itself, same pattern as guild's DragonSlayers: survives a restart mid-fight,
    // and a brand new dragon starts with none of these keys at all.
    static final NamespacedKey SPAWNED = new NamespacedKey("endfight", "guards-spawned");

    private final Plugin plugin;
    private final ArenaRegistry registry;
    private final Supplier<CultConfig> config;
    private final Logger logger;

    private BukkitTask checkTask;
    private BukkitTask tetherTask;

    public CultGuards(Plugin plugin, ArenaRegistry registry, Supplier<CultConfig> config) {
        this.plugin = plugin;
        this.registry = registry;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    public void start() {
        stop();
        CultConfig.Guards guards = config.get().guards();
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndSpawn, 0L, Math.max(1, guards.checkIntervalTicks()));
        tetherTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tetherTick, 0L, Math.max(1, guards.tetherIntervalTicks()));
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (tetherTask != null) {
            tetherTask.cancel();
            tetherTask = null;
        }
    }

    private void checkAndSpawn() {
        CultConfig cfg = config.get();
        if (!cfg.guards().enabled() || !registry.isBuilt()) {
            return;
        }
        for (World world : endWorlds()) {
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                spawnIfDue(dragon, cfg);
            }
        }
    }

    /** The natural (non-forced) spawn check for one dragon: nothing happens if it already got its
     * guards, or nobody's near the portal yet. Package-private so tests can drive it directly
     * without waiting on the repeating task. */
    boolean spawnIfDue(EnderDragon dragon, CultConfig cfg) {
        if (alreadySpawned(dragon) || CultRange.playersInRange(dragon.getWorld(), cfg.guards().range()).isEmpty()) {
            return false;
        }
        spawnAll(dragon, cfg);
        return true;
    }

    private void tetherTick() {
        if (!config.get().guards().enabled()) {
            return;
        }
        List<GuardPost> posts = registry.guardPosts();
        if (posts.isEmpty()) {
            return;
        }
        for (World world : endWorlds()) {
            GuardTether.tickAll(world, posts, config.get().guards());
        }
    }

    /** {@code /endfight cult spawn}: spawns every post with no regard for whether this dragon
     * already got its guards or anyone is nearby. */
    public int forceSpawn() {
        CultConfig cfg = config.get();
        int spawned = 0;
        for (World world : endWorlds()) {
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                spawned += spawnAll(dragon, cfg);
            }
        }
        return spawned;
    }

    /** {@code /endfight cult clear}: removes every live cult mob, guards and wave mobs alike. */
    public int clearAll() {
        int removed = 0;
        for (World world : endWorlds()) {
            for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
                if (CultMarker.isCult(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int aliveCount() {
        int total = 0;
        for (World world : endWorlds()) {
            total += CultRange.aliveCultCount(world);
        }
        return total;
    }

    /** Every remaining cult mob vanishes with its master - an assumption Joe hasn't confirmed
     * ({@code cult.die-with-dragon}, default on). Staggered over a couple of seconds rather than
     * all at once, each with a puff of soul particles where it stood. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || !config.get().dieWithDragon()) {
            return;
        }
        World world = dragon.getWorld();
        List<LivingEntity> toRemove = world.getEntitiesByClass(LivingEntity.class).stream()
                .filter(CultMarker::isCult)
                .toList();
        int delay = 0;
        for (LivingEntity entity : toRemove) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> vanish(entity), delay);
            delay += 2;
        }
    }

    private void vanish(LivingEntity entity) {
        if (!entity.isValid()) {
            return;
        }
        entity.getWorld().spawnParticle(Particle.SOUL, entity.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3);
        entity.remove();
    }

    private int spawnAll(EnderDragon dragon, CultConfig cfg) {
        List<GuardPost> posts = registry.guardPosts();
        if (posts.isEmpty()) {
            return 0;
        }
        List<GuardPlan.PlannedGuard> plan = GuardPlan.plan(posts, cfg.guards().composition());
        World world = dragon.getWorld();
        int spawned = 0;
        for (int i = 0; i < plan.size(); i++) {
            GuardPlan.PlannedGuard planned = plan.get(i);
            for (String type : planned.types()) {
                if (spawnGuard(world, planned.post(), i, type)) {
                    spawned++;
                }
            }
        }
        markSpawned(dragon);
        return spawned;
    }

    private boolean spawnGuard(World world, GuardPost post, int postIndex, String typeName) {
        EntityType type = CultEntityTypes.parse(typeName, logger);
        if (type == null) {
            return false;
        }
        Location at = SpawnSpot.at(world, post.x(), post.y(), post.z());
        Entity entity = world.spawnEntity(at, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return false;
        }
        CultIdentity.applyGuard(living, postIndex);
        if (living instanceof Creature creature) {
            CultIdentity.holdPosition(creature);
        }
        return true;
    }

    private static boolean alreadySpawned(EnderDragon dragon) {
        return dragon.getPersistentDataContainer().has(SPAWNED, PersistentDataType.BYTE);
    }

    private static void markSpawned(EnderDragon dragon) {
        dragon.getPersistentDataContainer().set(SPAWNED, PersistentDataType.BYTE, (byte) 1);
    }

    private static List<World> endWorlds() {
        return Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).toList();
    }
}

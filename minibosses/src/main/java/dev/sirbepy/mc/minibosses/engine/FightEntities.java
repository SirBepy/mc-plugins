package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Every temporary entity a fight makes (adds, markers, anchors, decoys, display entities) goes
 * through here, so it is tagged and can always be found and removed. Each one carries the
 * scoreboard tag {@link #TAG}, usable from the console as {@code @e[tag=minibosses.fight_temp]}
 * (a tag cannot contain a colon), and the owning boss's UUID in its persistent data. That second
 * mark is what lets {@link #sweep} clear leftovers of a fight that crashed with the server.
 */
public final class FightEntities {

    public static final String TAG = "minibosses.fight_temp";

    private final Plugin plugin;
    private final UUID owner;
    private final World world;
    /** Entity to whether it dies with the running attack instead of lasting the whole fight. */
    private final Map<Entity, Boolean> entities = new LinkedHashMap<>();

    public FightEntities(Plugin plugin, UUID owner, World world) {
        this.plugin = plugin;
        this.owner = owner;
        this.world = world;
    }

    /** Spawns an entity that lasts until the fight ends. */
    public <T extends Entity> T spawn(Location at, Class<T> type, Consumer<? super T> init) {
        return spawn(at, type, init, false);
    }

    /** Spawns an entity that is removed when the attack running now finishes or is cancelled. */
    public <T extends Entity> T spawnForAttack(Location at, Class<T> type, Consumer<? super T> init) {
        return spawn(at, type, init, true);
    }

    private <T extends Entity> T spawn(Location at, Class<T> type, Consumer<? super T> init, boolean attackScoped) {
        // Tagged inside the spawn callback, before the entity exists in the world, so there is no
        // moment where an untagged temporary entity could be missed by a sweep.
        T entity = world.spawn(at, type, spawned -> {
            mark(spawned);
            init.accept(spawned);
        });
        entities.put(entity, attackScoped);
        return entity;
    }

    private void mark(Entity entity) {
        entity.addScoreboardTag(TAG);
        entity.getPersistentDataContainer().set(EngineKeys.owner(plugin), PersistentDataType.STRING, owner.toString());
        // A temporary entity must never outlive a restart. Some entity types ignore this.
        entity.setPersistent(false);
    }

    public int count() {
        return entities.size();
    }

    /** Removes what the finished or cancelled attack spawned; entities that last the fight stay. */
    public void clearAttackScoped() {
        for (Iterator<Map.Entry<Entity, Boolean>> it = entities.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Boolean> entry = it.next();
            if (entry.getValue()) {
                entry.getKey().remove();
                it.remove();
            }
        }
    }

    /**
     * Removes every entity of this fight: the ones it spawned, and any tagged with this owner that
     * the fight lost track of (a chunk that unloaded and loaded again hands back a new object).
     */
    public void clear() {
        for (Entity entity : new ArrayList<>(entities.keySet())) {
            entity.remove();
        }
        entities.clear();
        sweepOwned(world.getEntities(), owner::equals, plugin);
    }

    public static boolean isTemp(Entity entity) {
        return entity.getScoreboardTags().contains(TAG);
    }

    /**
     * Removes every tagged entity in {@code candidates} whose owner is not a fight {@code running}
     * accepts, including a tagged entity with no readable owner. Returns how many went.
     */
    public static int sweep(Plugin plugin, Collection<? extends Entity> candidates, Predicate<UUID> running) {
        return sweepOwned(candidates, owner -> owner == null || !running.test(owner), plugin);
    }

    /** {@link #sweep} over every loaded world: the startup, shutdown and crash-recovery pass. */
    public static int sweepAll(Plugin plugin, Predicate<UUID> running) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += sweep(plugin, world.getEntities(), running);
        }
        return removed;
    }

    private static int sweepOwned(Collection<? extends Entity> candidates, Predicate<UUID> removeOwner, Plugin plugin) {
        int removed = 0;
        for (Entity entity : new ArrayList<>(candidates)) {
            if (entity instanceof Player || !isTemp(entity)) {
                continue;
            }
            if (removeOwner.test(ownerOf(entity, plugin))) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private static UUID ownerOf(Entity entity, Plugin plugin) {
        String raw = entity.getPersistentDataContainer().get(EngineKeys.owner(plugin), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

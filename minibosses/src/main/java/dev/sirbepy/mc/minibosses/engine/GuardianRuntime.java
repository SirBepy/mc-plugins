package dev.sirbepy.mc.minibosses.engine;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Every live Guardian fight's {@link BossBrain}, ticked from one shared repeating task, plus the
 * events that end them. A fight ends, and all its temporary entities go, when the boss dies, when
 * the boss leaves the world (chunk unload, removal), and when the plugin shuts down. After a
 * restart a boss that is still alive gets its brain back from the plan its own Guardian class
 * registered with {@link #register}.
 */
public final class GuardianRuntime implements Listener {

    /** What a Guardian's own class says about a boss found alive after a restart. */
    public record Resumed(PhasePlan plan, FightArea area) {
    }

    /** Rebuilds the plan and area for a boss of one Guardian at the tier it was summoned at. */
    @FunctionalInterface
    public interface Resolver {
        Optional<Resumed> resolve(LivingEntity boss, Tier tier);
    }

    private final Plugin plugin;
    private final Supplier<Random> rngFactory;
    private final FightEffects effects;
    private final Predicate<Player> onGround;
    private final NamespacedKey guardianKey;
    private final NamespacedKey tierKey;
    private final Map<UUID, BossBrain> brains = new LinkedHashMap<>();
    private final Map<String, Resolver> resolvers = new HashMap<>();

    @SuppressWarnings("deprecation") // the client-reported flag is all there is; it only gates the jump dodge
    public GuardianRuntime(Plugin plugin) {
        this(plugin, Random::new, FightEffects.WORLD, Player::isOnGround);
    }

    /** The full form tests use to pin the random source, capture the particles and drive "on the ground". */
    public GuardianRuntime(Plugin plugin, Supplier<Random> rngFactory, FightEffects effects,
            Predicate<Player> onGround) {
        this.plugin = plugin;
        this.rngFactory = rngFactory;
        this.effects = effects;
        this.onGround = onGround;
        // The same keys every Guardian fight stamps on its boss.
        this.guardianKey = new NamespacedKey(plugin, "guardian");
        this.tierKey = new NamespacedKey(plugin, "guardian_tier");
    }

    /** Registers how {@code guardianId}'s bosses get their plan back when found alive after a restart. */
    public void register(String guardianId, Resolver resolver) {
        resolvers.put(guardianId, resolver);
    }

    /** Starts the attack loop for {@code boss}; a boss that already has one gets a fresh one. */
    public BossBrain start(LivingEntity boss, Tier tier, PhasePlan plan, FightArea area) {
        end(boss.getUniqueId(), "restarted");
        FightEntities entities = new FightEntities(plugin, boss.getUniqueId(), area.world());
        FightContext ctx = new FightContext(plugin, boss, tier, area, rngFactory.get(), entities, effects, onGround);
        BossBrain brain = new BossBrain(ctx, plan);
        brains.put(boss.getUniqueId(), brain);
        return brain;
    }

    public Optional<BossBrain> get(UUID boss) {
        return Optional.ofNullable(brains.get(boss));
    }

    public boolean isRunning(UUID boss) {
        return brains.containsKey(boss);
    }

    /** Ends one fight's loop and removes its temporary entities; false if it was not running. */
    public boolean end(UUID boss, String reason) {
        BossBrain brain = brains.remove(boss);
        if (brain == null) {
            return false;
        }
        plugin.getLogger().fine("Guardian fight " + boss + " ended: " + reason);
        brain.end();
        return true;
    }

    /** The shared task body: one call advances every live fight by a tick. */
    public void tick() {
        for (Map.Entry<UUID, BossBrain> entry : new ArrayList<>(brains.entrySet())) {
            try {
                if (!entry.getValue().tick()) {
                    brains.remove(entry.getKey());
                }
            } catch (RuntimeException e) {
                // One broken attack must not stall every other fight, and must not repeat every tick.
                plugin.getLogger().log(Level.SEVERE, "Guardian fight " + entry.getKey() + " failed; ending it", e);
                end(entry.getKey(), "error");
            }
        }
    }

    /** Removes every temporary fight entity whose boss is not running here: after a crash, or on enable and disable. */
    public int sweepStrays() {
        return FightEntities.sweepAll(plugin, this::isRunning);
    }

    /** Plugin disable: ends every fight and clears everything it left in the world. */
    public void shutdown() {
        for (UUID boss : new ArrayList<>(brains.keySet())) {
            end(boss, "shutdown");
        }
        sweepStrays();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        // MONITOR: the Guardian's own loot and records have run by now.
        end(event.getEntity().getUniqueId(), "death");
    }

    @EventHandler
    public void onRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        // Fires for death, plain removal and a chunk unloading under a living boss; a living one
        // comes back through onEntitiesLoad.
        end(event.getEntity().getUniqueId(), "left the world");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (BossBrain brain : new ArrayList<>(brains.values())) {
            brain.playerLeft(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Chunks loading after a restart or a crash: a Guardian found alive gets its brain back, then
     * temporary entities of fights that are not running are swept (after the resume, so a resumed
     * boss keeps the ones it still owns).
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            resume(entity);
        }
        FightEntities.sweep(plugin, event.getEntities(), this::isRunning);
    }

    private void resume(Entity entity) {
        String id = entity.getPersistentDataContainer().get(guardianKey, PersistentDataType.STRING);
        if (id == null || !(entity instanceof LivingEntity boss) || boss.isDead() || isRunning(boss.getUniqueId())) {
            return;
        }
        Resolver resolver = resolvers.get(id);
        if (resolver == null) {
            return;
        }
        String tierName = boss.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        Tier tier = tierName == null ? Tier.NORMAL : Tier.valueOf(tierName);
        resolver.resolve(boss, tier).ifPresent(resumed -> start(boss, tier, resumed.plan(), resumed.area()));
    }
}

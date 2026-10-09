package dev.sirbepy.mc.endfight.reset;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Drives the whole-island reset around a dragon respawn. Polls every {@code reset.poll-interval-
 * ticks} (default 10): {@code IDLE} moves to {@code LOCKOUT} the moment any End world leaves
 * {@code NONE} (players placing the 4 crystals starts vanilla's own respawn sequence), stays
 * {@code LOCKOUT} through the whole sequence, and moves to {@code RESTORING} once the new dragon
 * has actually spawned - either the phase falls back to {@code NONE} with a living dragon, or (to
 * not depend on polling timing) the {@code ENDER_DRAGON} {@link CreatureSpawnEvent} fires first.
 * {@code RESTORING} drops back to {@code IDLE} once {@link IslandRestorer} finishes. Persists
 * nothing: a restart mid-respawn starts this watcher fresh at {@code IDLE}, and the very next poll
 * sees whatever phase vanilla has already resumed and picks the sequence up from there - no
 * special-cased recovery code needed.
 *
 * <p>Entirely inert (never touches lockout or restore) unless reset is enabled AND an island
 * snapshot exists - see {@link #resetActive()} - so the old {@code RespawnRebuildListener} rebuild
 * stays exactly as it was whenever there's nothing to restore from.
 */
public final class RespawnWatcher implements Listener {

    public enum Phase {
        IDLE, LOCKOUT, RESTORING
    }

    private final Plugin plugin;
    private final DragonBattleView battleView;
    private final Supplier<ResetConfig> config;
    private final BooleanSupplier snapshotExists;
    private final Supplier<Optional<IslandSnapshot>> loadSnapshot;
    private final RestoreStarter restorer;
    private final Consumer<World> onEnterLockout;

    private volatile Phase phase = Phase.IDLE;
    private volatile World activeWorld;

    private BukkitTask pollTask;
    private BukkitTask lockoutTask;

    public RespawnWatcher(Plugin plugin, DragonBattleView battleView, Supplier<ResetConfig> config,
                           BooleanSupplier snapshotExists, Supplier<Optional<IslandSnapshot>> loadSnapshot,
                           RestoreStarter restorer, Consumer<World> onEnterLockout) {
        this.plugin = plugin;
        this.battleView = battleView;
        this.config = config;
        this.snapshotExists = snapshotExists;
        this.loadSnapshot = loadSnapshot;
        this.restorer = restorer;
        this.onEnterLockout = onEnterLockout;
    }

    public Phase phase() {
        return phase;
    }

    /** {@code CultGuards}' "arena busy" gate: true for the whole lockout + restore window. */
    public boolean busy() {
        return phase != Phase.IDLE;
    }

    public void start() {
        stop();
        ResetConfig cfg = config.get();
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollTick, 0L, Math.max(1, cfg.pollIntervalTicks()));
        lockoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::lockoutTick, 0L, Math.max(1, cfg.lockoutTickIntervalTicks()));
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        if (lockoutTask != null) {
            lockoutTask.cancel();
            lockoutTask = null;
        }
    }

    /** Package-visible so a test can drive the state machine directly, tick by tick, with no
     * scheduler involved. */
    void pollTick() {
        if (!resetActive()) {
            return;
        }
        for (World end : endWorlds()) {
            Optional<DragonRespawnPhase> current = battleView.respawnPhase(end);
            if (current.isEmpty()) {
                continue;
            }
            DragonRespawnPhase respawnPhase = current.get();
            if (phase == Phase.IDLE && respawnPhase != DragonRespawnPhase.NONE) {
                enterLockout(end);
            } else if (phase == Phase.LOCKOUT && end.equals(activeWorld)
                    && respawnPhase == DragonRespawnPhase.NONE && battleView.hasLivingDragon(end)) {
                beginRestore(end);
            }
        }
    }

    void lockoutTick() {
        World world = activeWorld;
        if (world != null && (phase == Phase.LOCKOUT || phase == Phase.RESTORING)) {
            ArenaLockout.teleportAll(world, config.get());
        }
    }

    @EventHandler
    public void onDragonSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }
        World world = event.getEntity().getWorld();
        if (world.getEnvironment() != World.Environment.THE_END || !resetActive()) {
            return;
        }
        if (phase == Phase.LOCKOUT && world.equals(activeWorld)) {
            beginRestore(world);
        }
    }

    private void enterLockout(World end) {
        phase = Phase.LOCKOUT;
        activeWorld = end;
        onEnterLockout.accept(end);
    }

    private void beginRestore(World end) {
        if (phase == Phase.RESTORING) {
            return; // the poll and the spawn event can both fire for the same respawn
        }
        Optional<IslandSnapshot> snapshot = loadSnapshot.get();
        if (snapshot.isEmpty()) {
            phase = Phase.IDLE;
            activeWorld = null;
            return;
        }
        phase = Phase.RESTORING;
        restorer.start(end, snapshot.get(), () -> {
            phase = Phase.IDLE;
            activeWorld = null;
        });
    }

    /** {@code /endfight restore}: starts a restore right now regardless of the watcher's current
     * phase - an admin repair action, not part of the automatic respawn sequence. No-op if there's
     * no snapshot to restore from. */
    public boolean forceRestore(World end) {
        Optional<IslandSnapshot> snapshot = loadSnapshot.get();
        if (snapshot.isEmpty()) {
            return false;
        }
        phase = Phase.RESTORING;
        activeWorld = end;
        restorer.start(end, snapshot.get(), () -> {
            phase = Phase.IDLE;
            activeWorld = null;
        });
        return true;
    }

    private boolean resetActive() {
        return config.get().enabled() && snapshotExists.getAsBoolean();
    }

    private static List<World> endWorlds() {
        return Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).toList();
    }
}

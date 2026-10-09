package dev.sirbepy.mc.endfight.reset;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespawnWatcherTest {

    private ServerMock server;
    private WorldMock end;

    /** A controllable {@link DragonBattleView}: the test sets the phase/living-dragon state the
     * real {@code DragonBattle} would report, with no MockBukkit limitation in the way. */
    private static final class FakeBattleView implements DragonBattleView {
        private DragonRespawnPhase phase = DragonRespawnPhase.NONE;
        private boolean livingDragon;

        @Override
        public Optional<DragonRespawnPhase> respawnPhase(World world) {
            return Optional.of(phase);
        }

        @Override
        public boolean hasLivingDragon(World world) {
            return livingDragon;
        }
    }

    private static final class RecordingRestorer implements RestoreStarter {
        int startCount;
        Runnable pendingComplete;

        @Override
        public void start(World end, IslandSnapshot snapshot, Runnable onComplete) {
            startCount++;
            pendingComplete = onComplete;
        }

        void finish() {
            pendingComplete.run();
        }
    }

    private FakeBattleView battleView;
    private RecordingRestorer restorer;
    private AtomicInteger lockoutAnnounces;
    private boolean resetEnabled;
    private boolean snapshotPresent;
    private RespawnWatcher watcher;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        battleView = new FakeBattleView();
        restorer = new RecordingRestorer();
        lockoutAnnounces = new AtomicInteger();
        resetEnabled = true;
        snapshotPresent = true;
        var plugin = MockBukkit.createMockPlugin("EndFight");
        watcher = new RespawnWatcher(plugin, battleView, this::resetConfig,
                () -> snapshotPresent, () -> snapshotPresent ? Optional.of(stubSnapshot()) : Optional.empty(),
                restorer, world -> lockoutAnnounces.incrementAndGet());
    }

    private ResetConfig resetConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", resetEnabled);
        return new ResetConfig(yaml);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static IslandSnapshot stubSnapshot() {
        SnapshotBounds bounds = new SnapshotBounds(0, 0, 0, 0, 0, 0);
        return new IslandSnapshot(bounds, new String[] {"minecraft:air"}, new short[] {0});
    }

    @Test
    void theWholeSequenceGoesIdleToLockoutToRestoringToIdle() {
        assertEquals(RespawnWatcher.Phase.IDLE, watcher.phase());

        battleView.phase = DragonRespawnPhase.START;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.LOCKOUT, watcher.phase());
        assertEquals(1, lockoutAnnounces.get());

        battleView.phase = DragonRespawnPhase.PREPARING_TO_SUMMON_PILLARS;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.LOCKOUT, watcher.phase(), "stays in lockout through the whole sequence");

        battleView.phase = DragonRespawnPhase.SUMMONING_PILLARS;
        watcher.pollTick();
        battleView.phase = DragonRespawnPhase.SUMMONING_DRAGON;
        watcher.pollTick();
        battleView.phase = DragonRespawnPhase.END;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.LOCKOUT, watcher.phase());

        // The new dragon has spawned: phase falls back to NONE and a dragon now exists.
        battleView.phase = DragonRespawnPhase.NONE;
        battleView.livingDragon = true;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.RESTORING, watcher.phase());
        assertEquals(1, restorer.startCount);

        restorer.finish();
        assertEquals(RespawnWatcher.Phase.IDLE, watcher.phase());
    }

    @Test
    void theDragonSpawnEventAloneCanTriggerTheRestoreDuringLockout() {
        battleView.phase = DragonRespawnPhase.SUMMONING_DRAGON;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.LOCKOUT, watcher.phase());

        EnderDragon dragon = end.spawn(new Location(end, 0, 90, 0), EnderDragon.class);
        watcher.onDragonSpawn(new CreatureSpawnEvent(dragon, CreatureSpawnEvent.SpawnReason.DEFAULT));

        assertEquals(RespawnWatcher.Phase.RESTORING, watcher.phase());
        assertEquals(1, restorer.startCount);
    }

    @Test
    void aFreshWatcherPicksUpARespawnAlreadyInProgressAfterARestart() {
        // Simulates a server restart mid-respawn: the watcher starts at IDLE with no memory, but
        // vanilla has already resumed past START - the very first poll must still catch it.
        battleView.phase = DragonRespawnPhase.SUMMONING_PILLARS;

        watcher.pollTick();

        assertEquals(RespawnWatcher.Phase.LOCKOUT, watcher.phase());
    }

    @Test
    void resetDisabledInConfigNeverEngagesTheWatcherLeavingTheOldRebuildInCharge() {
        resetEnabled = false;
        assertNeverEngages();
    }

    @Test
    void noSnapshotOnDiskNeverEngagesTheWatcherEitherLeavingTheOldRebuildInCharge() {
        snapshotPresent = false;
        assertNeverEngages();
    }

    private void assertNeverEngages() {
        battleView.phase = DragonRespawnPhase.START;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.IDLE, watcher.phase(), "must never enter lockout with reset inactive");

        battleView.phase = DragonRespawnPhase.NONE;
        battleView.livingDragon = true;
        watcher.pollTick();
        assertEquals(RespawnWatcher.Phase.IDLE, watcher.phase());
        assertEquals(0, restorer.startCount);
        assertEquals(0, lockoutAnnounces.get());
        assertFalse(watcher.busy());
    }

    @Test
    void busyIsTrueForTheWholeLockoutAndRestoringWindow() {
        assertFalse(watcher.busy());

        battleView.phase = DragonRespawnPhase.START;
        watcher.pollTick();
        assertTrue(watcher.busy());

        battleView.phase = DragonRespawnPhase.NONE;
        battleView.livingDragon = true;
        watcher.pollTick();
        assertTrue(watcher.busy());

        restorer.finish();
        assertFalse(watcher.busy());
    }
}

package dev.sirbepy.mc.safechests.grant;

import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.source.Source;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the revocation-survives-a-restart bug directly: Bolt's own async save is what actually
 * loses the race (see {@link GrantManager}'s class javadoc), but the observable symptom is simpler to
 * reproduce without a real Bolt executor - put the access entry back on the protection after SafeChests
 * has already forgotten the grant, exactly as a lost async write would leave it, then reload.
 *
 * <p>This class only calls API that exists both before and after the tombstone fix (grantWhitelist,
 * revokeWhitelist, a fresh GrantManager + loadAndSweep), so {@link #startupReappliesARevokeBoltNeverPersisted()}
 * fails against the pre-tombstone {@code GrantManager}/{@code GrantStore} and passes once they record and
 * replay tombstones - the red/green proof for this fix.
 */
class TombstoneReplayTest {

    private FakeBoltAPI api;
    private GrantManager grants;
    private UUID owner;
    private UUID grantee;
    private File storeFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        api = new FakeBoltAPI();
        storeFile = new File(tempDir.toFile(), "grants.yml");
        grants = new GrantManager(new BoltAccess(api), new GrantStore(storeFile, Logger.getAnonymousLogger()), () -> "normal");
        owner = UUID.randomUUID();
        grantee = UUID.randomUUID();
    }

    private BlockProtection chest(UUID owningPlayer, int x) {
        BlockProtection protection = new BlockProtection(UUID.randomUUID(), owningPlayer, "container", 0, 0,
                new HashMap<>(), "world", x, 64, 0, "CHEST");
        api.protections.add(protection);
        return protection;
    }

    @Test
    void startupReappliesARevokeBoltNeverPersisted() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null);
        grants.revokeWhitelist(owner, grantee);

        // Bolt's SQLStore.saveBlockProtection dispatches to CompletableFuture.runAsync and discards the
        // future (confirmed in SQLStore's bytecode); a crash between the revoke above and that async
        // write landing leaves the access entry on disk even though SafeChests already forgot the grant.
        // FakeBoltAPI.saveProtection is synchronous, so simulate the lost write directly.
        chestA.getAccess().put(Source.player(grantee).toString(), "normal");

        GrantManager reloaded = new GrantManager(new BoltAccess(api),
                new GrantStore(storeFile, Logger.getAnonymousLogger()), () -> "normal");
        reloaded.loadAndSweep();

        assertFalse(chestA.getAccess().containsKey(Source.player(grantee).toString()),
                "a tombstoned revoke must be replayed on startup even if Bolt's own write never landed");
    }

    @Test
    void startupLeavesAccessAloneWhenNothingWasEverRevoked() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null);

        GrantManager reloaded = new GrantManager(new BoltAccess(api),
                new GrantStore(storeFile, Logger.getAnonymousLogger()), () -> "normal");
        reloaded.loadAndSweep();

        assertTrue(chestA.getAccess().containsKey(Source.player(grantee).toString()));
    }

    @Test
    void explicitUnwhitelistWritesATombstone() {
        chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null);

        grants.revokeWhitelist(owner, grantee);

        List<WhitelistTombstone> stored = new GrantStore(storeFile, Logger.getAnonymousLogger()).loadWhitelistTombstones();
        assertEquals(1, stored.size());
        assertEquals(owner, stored.get(0).owner());
        assertEquals(grantee, stored.get(0).grantee());
    }

    @Test
    void expirySweepWritesATombstoneJustLikeAnExplicitRevoke() {
        chest(owner, 0);
        grants.grantWhitelist(owner, grantee, Duration.ofMinutes(5));

        grants.sweepExpiredWhitelist(Instant.now().plus(Duration.ofMinutes(6)));

        List<WhitelistTombstone> stored = new GrantStore(storeFile, Logger.getAnonymousLogger()).loadWhitelistTombstones();
        assertEquals(1, stored.size());
        assertEquals(grantee, stored.get(0).grantee());
    }

    @Test
    void oneTimeRevokeWritesATombstoneKeyedByLocation() {
        BlockProtection chestA = chest(owner, 0);

        grants.grantOneTime(chestA, owner, grantee);
        grants.revokeOneTime(chestA, grantee);

        List<OneTimeTombstone> stored = new GrantStore(storeFile, Logger.getAnonymousLogger()).loadOneTimeTombstones();
        assertEquals(1, stored.size());
        OneTimeTombstone tombstone = stored.get(0);
        assertEquals(chestA.getWorld(), tombstone.world());
        assertEquals(chestA.getX(), tombstone.x());
        assertEquals(chestA.getY(), tombstone.y());
        assertEquals(chestA.getZ(), tombstone.z());
        assertEquals(grantee, tombstone.grantee());
    }

    // revokeAllOneTimeFor (the quit-time bulk revoke) and sweepStaleOneTime/applyTombstones' one-time
    // branch all call Bukkit.getWorld unconditionally, which NPEs under plain JUnit (no live Bukkit
    // server) - the same reason GrantManagerTest never exercises those paths either. Their tombstone
    // bookkeeping reuses the same recordOneTimeTombstone helper exercised below through revokeOneTime
    // and grantOneTime, which do not touch Bukkit; the live-server behavior is covered by the
    // persistence e2e (e2e/persistence.mjs) instead.

    @Test
    void aNewerActiveWhitelistGrantBeatsAnOlderTombstoneOnStartup() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null);
        grants.revokeWhitelist(owner, grantee); // tombstoned
        grants.grantWhitelist(owner, grantee, null); // re-granted after the revoke, must win

        // Even if the first revoke's access removal had not actually landed in Bolt yet, the access
        // entry is present again now (the re-grant above just wrote it) - a restart must leave it be.
        GrantManager reloaded = new GrantManager(new BoltAccess(api),
                new GrantStore(storeFile, Logger.getAnonymousLogger()), () -> "normal");
        reloaded.loadAndSweep();

        assertTrue(chestA.getAccess().containsKey(Source.player(grantee).toString()));
        assertTrue(reloaded.isWhitelisted(owner, grantee));
        assertTrue(new GrantStore(storeFile, Logger.getAnonymousLogger()).loadWhitelistTombstones().isEmpty(),
                "the re-grant must have cleared the superseded tombstone");
    }

    @Test
    void aFreshOneTimeGrantSupersedesTheTombstoneFromAnEarlierRevokeOnTheSameSpot() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantOneTime(chestA, owner, grantee);
        grants.revokeOneTime(chestA, grantee); // tombstoned
        assertEquals(1, new GrantStore(storeFile, Logger.getAnonymousLogger()).loadOneTimeTombstones().size());

        grants.grantOneTime(chestA, owner, grantee); // re-granted on the same spot, must supersede

        assertTrue(chestA.getAccess().containsKey(Source.player(grantee).toString()));
        assertEquals(1, grants.pendingOneTime().size());
        assertTrue(new GrantStore(storeFile, Logger.getAnonymousLogger()).loadOneTimeTombstones().isEmpty(),
                "the re-grant must have cleared the superseded one-time tombstone");
    }

    @Test
    void startupReplaysAnOldTombstoneAndKeepsItForAnotherRetentionWindow() {
        BlockProtection chestA = chest(owner, 0);
        GrantStore store = new GrantStore(storeFile, Logger.getAnonymousLogger());
        // An 11-minute-old revoke whose Bolt write was lost: the server was down longer than the retention.
        store.saveWhitelistTombstones(List.of(new WhitelistTombstone(owner, grantee, Instant.now().minus(Duration.ofMinutes(11)))));
        chestA.getAccess().put(Source.player(grantee).toString(), "normal");

        GrantManager reloaded = new GrantManager(new BoltAccess(api), store, () -> "normal");
        reloaded.loadAndSweep();

        assertFalse(chestA.getAccess().containsKey(Source.player(grantee).toString()));
        List<WhitelistTombstone> kept = store.loadWhitelistTombstones();
        assertEquals(1, kept.size(), "the replay is itself an async Bolt write, so the tombstone must survive the boot prune");
        reloaded.pruneTombstones(Instant.now().plus(Duration.ofMinutes(11)));
        assertTrue(store.loadWhitelistTombstones().isEmpty());
    }

    @Test
    void periodicPruneTombstonesDropsOnlyStaleEntries() {
        chest(owner, 0);
        Instant now = Instant.now();
        UUID other = UUID.randomUUID();
        grants.grantWhitelist(owner, grantee, null);
        grants.revokeWhitelist(owner, grantee);
        grants.grantWhitelist(owner, other, Duration.ofMinutes(5));
        grants.sweepExpiredWhitelist(now.plus(Duration.ofMinutes(6)));

        grants.pruneTombstones(now.plus(Duration.ofMinutes(11)));

        List<WhitelistTombstone> left = new GrantStore(storeFile, Logger.getAnonymousLogger()).loadWhitelistTombstones();
        assertEquals(1, left.size());
        assertEquals(other, left.get(0).grantee());
    }

    @Test
    void periodicReplayFinishesARevokeTheBootPassCouldNotSeeYet() {
        // Simulates Bolt's own protection load being empty/incomplete at boot time (e.g. a transient
        // error in Bolt's own store): the boot sweep finds no protection to revoke, so the tombstone it
        // wrote is the only record of the pending revoke. The chest only becomes visible to Bolt
        // afterwards, still carrying the access entry a correctly-functioning boot sweep would have
        // stripped. The periodic sweep's replay (not a second boot) must finish that revoke once Bolt's
        // set is actually complete - this is what distinguishes replayPendingTombstones from a boot-only fix.
        GrantStore store = new GrantStore(storeFile, Logger.getAnonymousLogger());
        store.saveWhitelistTombstones(List.of(new WhitelistTombstone(owner, grantee, Instant.now())));
        GrantManager reloaded = new GrantManager(new BoltAccess(api), store, () -> "normal");
        reloaded.loadAndSweep(); // Bolt has no protections yet: nothing to revoke, tombstone survives

        BlockProtection chestA = chest(owner, 0); // the chest "appears" to Bolt only now
        chestA.getAccess().put(Source.player(grantee).toString(), "normal"); // still carries stale access

        reloaded.replayPendingTombstones(); // what the periodic sweep (every 15s) now also calls

        assertFalse(chestA.getAccess().containsKey(Source.player(grantee).toString()),
                "the periodic sweep must finish a revoke the boot pass could not see yet");
    }

    @Test
    void grantsYmlRoundTripsWhitelistAndOneTimeTombstones() {
        GrantStore store = new GrantStore(storeFile, Logger.getAnonymousLogger());
        Instant when = Instant.now().minus(Duration.ofMinutes(2));
        UUID otherGrantee = UUID.randomUUID();
        store.saveWhitelistTombstones(List.of(new WhitelistTombstone(owner, grantee, when)));
        store.saveOneTimeTombstones(List.of(new OneTimeTombstone("world", 1, 64, -2, otherGrantee, when)));

        List<WhitelistTombstone> whitelistLoaded = store.loadWhitelistTombstones();
        List<OneTimeTombstone> oneTimeLoaded = store.loadOneTimeTombstones();

        assertEquals(1, whitelistLoaded.size());
        assertEquals(owner, whitelistLoaded.get(0).owner());
        assertEquals(grantee, whitelistLoaded.get(0).grantee());
        assertEquals(when.getEpochSecond(), whitelistLoaded.get(0).revokedAt().getEpochSecond());

        assertEquals(1, oneTimeLoaded.size());
        OneTimeTombstone restored = oneTimeLoaded.get(0);
        assertEquals("world", restored.world());
        assertEquals(1, restored.x());
        assertEquals(64, restored.y());
        assertEquals(-2, restored.z());
        assertEquals(otherGrantee, restored.grantee());
        assertEquals(when.getEpochSecond(), restored.revokedAt().getEpochSecond());
    }
}

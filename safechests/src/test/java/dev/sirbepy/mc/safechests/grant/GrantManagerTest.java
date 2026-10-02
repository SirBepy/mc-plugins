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

class GrantManagerTest {

    private FakeBoltAPI api;
    private GrantManager grants;
    private UUID owner;
    private UUID grantee;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        api = new FakeBoltAPI();
        File store = new File(tempDir.toFile(), "grants.yml");
        grants = new GrantManager(new BoltAccess(api), new GrantStore(store, Logger.getAnonymousLogger()), () -> "normal");
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
    void whitelistGrantAddsAccessToEveryProtectionTheOwnerHas() {
        BlockProtection chestA = chest(owner, 0);
        BlockProtection chestB = chest(owner, 1);
        chest(UUID.randomUUID(), 2); // someone else's chest, must stay untouched

        grants.grantWhitelist(owner, grantee, null);

        String key = Source.player(grantee).toString();
        assertEquals("normal", chestA.getAccess().get(key));
        assertEquals("normal", chestB.getAccess().get(key));
        assertTrue(grants.isWhitelisted(owner, grantee));
    }

    @Test
    void revokeWhitelistRemovesTheAccessEntryEverywhere() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null);

        boolean removed = grants.revokeWhitelist(owner, grantee);

        assertTrue(removed);
        assertFalse(chestA.getAccess().containsKey(Source.player(grantee).toString()));
        assertFalse(grants.isWhitelisted(owner, grantee));
    }

    @Test
    void revokingAGrantThatNeverExistedReportsFalse() {
        assertFalse(grants.revokeWhitelist(owner, grantee));
    }

    @Test
    void timedGrantExpiresAndIsRevokedBySweep() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantWhitelist(owner, grantee, Duration.ofMinutes(5));
        assertTrue(grants.isWhitelisted(owner, grantee));

        grants.sweepExpiredWhitelist(Instant.now().plus(Duration.ofMinutes(4))); // not yet expired
        assertTrue(grants.isWhitelisted(owner, grantee));
        assertTrue(chestA.getAccess().containsKey(Source.player(grantee).toString()));

        grants.sweepExpiredWhitelist(Instant.now().plus(Duration.ofMinutes(6))); // past expiry
        assertFalse(grants.isWhitelisted(owner, grantee));
        assertFalse(chestA.getAccess().containsKey(Source.player(grantee).toString()));
    }

    @Test
    void permanentGrantSurvivesAnySweep() {
        chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null);

        grants.sweepExpiredWhitelist(Instant.now().plus(Duration.ofDays(365)));

        assertTrue(grants.isWhitelisted(owner, grantee));
    }

    @Test
    void grantsSurviveAReloadFromDisk() {
        chest(owner, 0);
        grants.grantWhitelist(owner, grantee, Duration.ofMinutes(5));

        GrantManager reloaded = new GrantManager(new BoltAccess(api),
                new GrantStore(new File(tempDir.toFile(), "grants.yml"), Logger.getAnonymousLogger()), () -> "normal");
        reloaded.loadAndSweep();

        List<WhitelistGrant> restored = reloaded.whitelistFor(owner);
        assertEquals(1, restored.size());
        assertEquals(grantee, restored.get(0).grantee());
        assertFalse(restored.get(0).isPermanent());
    }

    @Test
    void newlyCreatedProtectionPicksUpAnActiveWhitelistGrant() {
        grants.grantWhitelist(owner, grantee, null);
        BlockProtection freshlyPlaced = chest(owner, 5); // created after the grant, like a newly placed chest

        grants.applyActiveGrantsTo(freshlyPlaced);

        assertEquals("normal", freshlyPlaced.getAccess().get(Source.player(grantee).toString()));
    }

    @Test
    void oneTimeGrantWritesASingleAccessEntryAndRevokeRemovesIt() {
        BlockProtection chestA = chest(owner, 0);

        grants.grantOneTime(chestA, owner, grantee);
        assertEquals("normal", chestA.getAccess().get(Source.player(grantee).toString()));
        assertEquals(1, grants.pendingOneTime().size());

        grants.revokeOneTime(chestA, grantee);
        assertFalse(chestA.getAccess().containsKey(Source.player(grantee).toString()));
        assertEquals(0, grants.pendingOneTime().size());
    }

    @Test
    void oneTimeRevokeNeverRemovesAnUnrelatedWhitelistGrant() {
        BlockProtection chestA = chest(owner, 0);
        grants.grantWhitelist(owner, grantee, null); // grantee is also permanently whitelisted
        grants.grantOneTime(chestA, owner, grantee);

        grants.revokeOneTime(chestA, grantee);

        // The one-time record is gone, but the access entry must stay: the whitelist grant still needs it.
        assertTrue(chestA.getAccess().containsKey(Source.player(grantee).toString()));
        assertEquals(0, grants.pendingOneTime().size());
    }
}

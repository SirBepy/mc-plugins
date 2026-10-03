package dev.sirbepy.mc.safechests.grant;

import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.Protection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns both grant kinds and keeps {@code grants.yml} in sync with what is actually written into Bolt
 * protections, so a restart never leaves a stale access entry behind.
 *
 * <p>Owner-wide grants (whitelist / 5 min / Always) are implemented by writing a {@code player:<uuid>}
 * access entry into every {@link BlockProtection} the owner currently has, rather than Bolt's internal
 * per-owner {@code AccessList} (what {@code /bolt trust} edits). That list exists, and {@code canAccess}
 * does consult it, but it is reached only through {@code Bolt.getStore()} on the internal {@code BoltPlugin}
 * class, not through the public {@link org.popcraft.bolt.BoltAPI} contract SafeChests is built against.
 * Writing access entries through the public {@code loadProtections()}/{@code saveProtection()} calls stays
 * forward-compatible with a Bolt update and never touches Bolt's own storage format.
 *
 * <p>Every revoke this class performs is also recorded as a tombstone in {@code grants.yml}, written
 * synchronously in the same call. This exists because {@code BoltAPI.saveProtection} persists through
 * {@code SQLStore.saveBlockProtection}'s {@code CompletableFuture.runAsync}, which is fire-and-forget:
 * a revoke's in-memory access removal can win the race against its own disk write losing to a server
 * stop. {@link #loadAndSweep()} replays every tombstone against Bolt on the next boot (removing the
 * access entry again, a no-op if it already landed) so a crash between the two can never leave stale
 * access behind. A tombstone is dropped once a later grant supersedes it, or once it outlives
 * {@link #TOMBSTONE_RETENTION} - long enough for Bolt's async executor to have long since caught up.
 */
public final class GrantManager {

    // Bolt's background save executor finishes in well under this; by the time a tombstone is this
    // old, replaying it again on a future boot would only be dead weight, not a correctness need.
    private static final Duration TOMBSTONE_RETENTION = Duration.ofMinutes(10);

    private final BoltAccess bolt;
    private final GrantStore store;
    private final Supplier<String> accessType;

    private final List<WhitelistGrant> whitelist = new ArrayList<>();
    private final List<OneTimeGrant> oneTime = new ArrayList<>();
    private final List<WhitelistTombstone> whitelistTombstones = new ArrayList<>();
    private final List<OneTimeTombstone> oneTimeTombstones = new ArrayList<>();

    public GrantManager(BoltAccess bolt, GrantStore store, Supplier<String> accessType) {
        this.bolt = bolt;
        this.store = store;
        this.accessType = accessType;
    }

    /**
     * Loads persisted grants and tombstones, replays the tombstones against Bolt, revokes anything
     * already expired, sweeps one-time grants left over from a crash, and prunes stale tombstones.
     */
    public void loadAndSweep() {
        whitelist.addAll(store.loadWhitelistGrants());
        oneTime.addAll(store.loadOneTimeGrants());
        whitelistTombstones.addAll(store.loadWhitelistTombstones());
        oneTimeTombstones.addAll(store.loadOneTimeTombstones());
        applyTombstones();
        sweepExpiredWhitelist(Instant.now());
        sweepStaleOneTime();
        pruneTombstones(Instant.now());
    }

    /** Re-applies every pending tombstone, unless a grant made since the revoke now covers it again. */
    private void applyTombstones() {
        for (WhitelistTombstone tombstone : whitelistTombstones) {
            if (isWhitelisted(tombstone.owner(), tombstone.grantee())) {
                continue;
            }
            removeWhitelistAccessEntries(tombstone.owner(), tombstone.grantee());
        }
        for (OneTimeTombstone tombstone : oneTimeTombstones) {
            World world = Bukkit.getWorld(tombstone.world());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(tombstone.x(), tombstone.y(), tombstone.z());
            Protection protection = bolt.api().findProtection(block);
            if (protection == null || isWhitelisted(protection.getOwner(), tombstone.grantee())
                    || coveredByActiveOneTime(tombstone)) {
                continue;
            }
            bolt.revokeAccess(protection, tombstone.grantee());
        }
        // The replay is itself an async Bolt write, so the retention clock restarts now: after a long
        // downtime the boot-time prune would otherwise drop a tombstone whose replay hasn't landed yet.
        if (!whitelistTombstones.isEmpty() || !oneTimeTombstones.isEmpty()) {
            Instant now = Instant.now();
            whitelistTombstones.replaceAll(t -> new WhitelistTombstone(t.owner(), t.grantee(), now));
            oneTimeTombstones.replaceAll(t -> new OneTimeTombstone(t.world(), t.x(), t.y(), t.z(), t.grantee(), now));
            store.saveWhitelistTombstones(whitelistTombstones);
            store.saveOneTimeTombstones(oneTimeTombstones);
        }
    }

    private boolean coveredByActiveOneTime(OneTimeTombstone tombstone) {
        return oneTime.stream().anyMatch(g -> g.grantee().equals(tombstone.grantee())
                && g.world().equals(tombstone.world())
                && g.x() == tombstone.x() && g.y() == tombstone.y() && g.z() == tombstone.z());
    }

    /** Drops tombstones old enough that Bolt's async write for the revoke they cover is long since done. */
    public void pruneTombstones(Instant now) {
        boolean whitelistChanged = whitelistTombstones.removeIf(t -> isStale(t.revokedAt(), now));
        if (whitelistChanged) {
            store.saveWhitelistTombstones(whitelistTombstones);
        }
        boolean oneTimeChanged = oneTimeTombstones.removeIf(t -> isStale(t.revokedAt(), now));
        if (oneTimeChanged) {
            store.saveOneTimeTombstones(oneTimeTombstones);
        }
    }

    private static boolean isStale(Instant revokedAt, Instant now) {
        return Duration.between(revokedAt, now).compareTo(TOMBSTONE_RETENTION) >= 0;
    }

    private void recordWhitelistTombstone(UUID owner, UUID grantee, Instant when) {
        whitelistTombstones.removeIf(t -> t.owner().equals(owner) && t.grantee().equals(grantee));
        whitelistTombstones.add(new WhitelistTombstone(owner, grantee, when));
    }

    private void recordOneTimeTombstone(String world, int x, int y, int z, UUID grantee, Instant when) {
        oneTimeTombstones.removeIf(t -> t.grantee().equals(grantee)
                && t.world().equals(world) && t.x() == x && t.y() == y && t.z() == z);
        oneTimeTombstones.add(new OneTimeTombstone(world, x, y, z, grantee, when));
    }

    public void sweepExpiredWhitelist(Instant now) {
        List<WhitelistGrant> expired = whitelist.stream().filter(g -> g.isExpired(now)).toList();
        for (WhitelistGrant grant : expired) {
            removeWhitelistAccessEntries(grant.owner(), grant.grantee());
            recordWhitelistTombstone(grant.owner(), grant.grantee(), now);
        }
        if (!expired.isEmpty()) {
            whitelist.removeAll(expired);
            store.saveWhitelistGrants(whitelist);
            store.saveWhitelistTombstones(whitelistTombstones);
        }
    }

    /** A one-time record surviving to startup means the server died with the chest still open; revoke it. */
    private void sweepStaleOneTime() {
        if (oneTime.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (OneTimeGrant grant : oneTime) {
            World world = Bukkit.getWorld(grant.world());
            if (world != null) {
                Block block = world.getBlockAt(grant.x(), grant.y(), grant.z());
                Protection protection = bolt.api().findProtection(block);
                if (protection != null) {
                    bolt.revokeAccess(protection, grant.grantee());
                }
            }
            recordOneTimeTombstone(grant.world(), grant.x(), grant.y(), grant.z(), grant.grantee(), now);
        }
        oneTime.clear();
        store.saveOneTimeGrants(oneTime);
        store.saveOneTimeTombstones(oneTimeTombstones);
    }

    public WhitelistGrant grantWhitelist(UUID owner, UUID grantee, Duration duration) {
        revokeWhitelist(owner, grantee);
        Instant expiresAt = duration == null ? null : Instant.now().plus(duration);
        WhitelistGrant grant = new WhitelistGrant(owner, grantee, expiresAt);
        whitelist.add(grant);
        store.saveWhitelistGrants(whitelist);
        // A fresh grant supersedes any tombstone left by a just-replaced grant above, or an earlier
        // unrelated revoke: a restart must never re-revoke access the owner just gave again.
        if (whitelistTombstones.removeIf(t -> t.owner().equals(owner) && t.grantee().equals(grantee))) {
            store.saveWhitelistTombstones(whitelistTombstones);
        }
        for (BlockProtection protection : bolt.blockProtectionsOwnedBy(owner)) {
            bolt.grantAccess(protection, grantee, accessType.get());
        }
        return grant;
    }

    /** @return true if a grant existed and was revoked */
    public boolean revokeWhitelist(UUID owner, UUID grantee) {
        boolean removed = whitelist.removeIf(g -> g.owner().equals(owner) && g.grantee().equals(grantee));
        if (removed) {
            store.saveWhitelistGrants(whitelist);
            removeWhitelistAccessEntries(owner, grantee);
            recordWhitelistTombstone(owner, grantee, Instant.now());
            store.saveWhitelistTombstones(whitelistTombstones);
        }
        return removed;
    }

    private void removeWhitelistAccessEntries(UUID owner, UUID grantee) {
        for (BlockProtection protection : bolt.blockProtectionsOwnedBy(owner)) {
            bolt.revokeAccess(protection, grantee);
        }
    }

    public List<WhitelistGrant> whitelistFor(UUID owner) {
        return whitelist.stream().filter(g -> g.owner().equals(owner)).toList();
    }

    public boolean isWhitelisted(UUID owner, UUID grantee) {
        return whitelist.stream().anyMatch(g -> g.owner().equals(owner) && g.grantee().equals(grantee));
    }

    /** Applies any active whitelist grants for the owner to a protection just created (a newly placed chest). */
    public void applyActiveGrantsTo(Protection protection) {
        if (!(protection instanceof BlockProtection)) {
            return;
        }
        for (WhitelistGrant grant : whitelistFor(protection.getOwner())) {
            bolt.grantAccess(protection, grant.grantee(), accessType.get());
        }
    }

    public void grantOneTime(Protection protection, UUID owner, UUID grantee) {
        if (!(protection instanceof BlockProtection blockProtection)) {
            return;
        }
        bolt.grantAccess(protection, grantee, accessType.get());
        OneTimeGrant record = new OneTimeGrant(owner, grantee, blockProtection.getWorld(), blockProtection.getX(), blockProtection.getY(), blockProtection.getZ());
        oneTime.add(record);
        store.saveOneTimeGrants(oneTime);
        // A fresh grant on this exact spot supersedes any pending revoke tombstone for it.
        if (oneTimeTombstones.removeIf(t -> t.grantee().equals(grantee) && t.world().equals(record.world())
                && t.x() == record.x() && t.y() == record.y() && t.z() == record.z())) {
            store.saveOneTimeTombstones(oneTimeTombstones);
        }
    }

    /** Revokes a one-time grant for this exact protection + grantee, if one is pending. */
    public void revokeOneTime(Protection protection, UUID grantee) {
        if (!(protection instanceof BlockProtection blockProtection)) {
            return;
        }
        boolean removed = oneTime.removeIf(g -> g.grantee().equals(grantee)
                && g.world().equals(blockProtection.getWorld())
                && g.x() == blockProtection.getX() && g.y() == blockProtection.getY() && g.z() == blockProtection.getZ());
        if (removed) {
            store.saveOneTimeGrants(oneTime);
            recordOneTimeTombstone(blockProtection.getWorld(), blockProtection.getX(), blockProtection.getY(),
                    blockProtection.getZ(), grantee, Instant.now());
            store.saveOneTimeTombstones(oneTimeTombstones);
        }
        // Revoke the access entry regardless: if it is not whitelisted some other way, it has no business staying.
        if (removed && !isWhitelisted(protection.getOwner(), grantee)) {
            bolt.revokeAccess(protection, grantee);
        }
    }

    /** Revokes every pending one-time grant held by a player, e.g. on quit. */
    public void revokeAllOneTimeFor(UUID grantee) {
        List<OneTimeGrant> held = oneTime.stream().filter(g -> g.grantee().equals(grantee)).toList();
        Instant now = Instant.now();
        for (OneTimeGrant grant : held) {
            World world = Bukkit.getWorld(grant.world());
            if (world != null) {
                Block block = world.getBlockAt(grant.x(), grant.y(), grant.z());
                Protection protection = bolt.api().findProtection(block);
                if (protection != null && !isWhitelisted(protection.getOwner(), grantee)) {
                    bolt.revokeAccess(protection, grantee);
                }
            }
            recordOneTimeTombstone(grant.world(), grant.x(), grant.y(), grant.z(), grantee, now);
        }
        if (!held.isEmpty()) {
            oneTime.removeAll(held);
            store.saveOneTimeGrants(oneTime);
            store.saveOneTimeTombstones(oneTimeTombstones);
        }
    }

    public List<OneTimeGrant> pendingOneTime() {
        return List.copyOf(oneTime);
    }
}

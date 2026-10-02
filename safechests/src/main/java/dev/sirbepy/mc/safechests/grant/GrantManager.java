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
 */
public final class GrantManager {

    private final BoltAccess bolt;
    private final GrantStore store;
    private final Supplier<String> accessType;

    private final List<WhitelistGrant> whitelist = new ArrayList<>();
    private final List<OneTimeGrant> oneTime = new ArrayList<>();

    public GrantManager(BoltAccess bolt, GrantStore store, Supplier<String> accessType) {
        this.bolt = bolt;
        this.store = store;
        this.accessType = accessType;
    }

    /** Loads persisted grants, revokes anything already expired, and sweeps one-time grants left over from a crash. */
    public void loadAndSweep() {
        whitelist.addAll(store.loadWhitelistGrants());
        oneTime.addAll(store.loadOneTimeGrants());
        sweepExpiredWhitelist(Instant.now());
        sweepStaleOneTime();
    }

    public void sweepExpiredWhitelist(Instant now) {
        List<WhitelistGrant> expired = whitelist.stream().filter(g -> g.isExpired(now)).toList();
        for (WhitelistGrant grant : expired) {
            removeWhitelistAccessEntries(grant.owner(), grant.grantee());
        }
        if (!expired.isEmpty()) {
            whitelist.removeAll(expired);
            store.saveWhitelistGrants(whitelist);
        }
    }

    /** A one-time record surviving to startup means the server died with the chest still open; revoke it. */
    private void sweepStaleOneTime() {
        if (oneTime.isEmpty()) {
            return;
        }
        for (OneTimeGrant grant : oneTime) {
            World world = Bukkit.getWorld(grant.world());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(grant.x(), grant.y(), grant.z());
            Protection protection = bolt.api().findProtection(block);
            if (protection != null) {
                bolt.revokeAccess(protection, grant.grantee());
            }
        }
        oneTime.clear();
        store.saveOneTimeGrants(oneTime);
    }

    public WhitelistGrant grantWhitelist(UUID owner, UUID grantee, Duration duration) {
        revokeWhitelist(owner, grantee);
        Instant expiresAt = duration == null ? null : Instant.now().plus(duration);
        WhitelistGrant grant = new WhitelistGrant(owner, grantee, expiresAt);
        whitelist.add(grant);
        store.saveWhitelistGrants(whitelist);
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
        }
        // Revoke the access entry regardless: if it is not whitelisted some other way, it has no business staying.
        if (removed && !isWhitelisted(protection.getOwner(), grantee)) {
            bolt.revokeAccess(protection, grantee);
        }
    }

    /** Revokes every pending one-time grant held by a player, e.g. on quit. */
    public void revokeAllOneTimeFor(UUID grantee) {
        List<OneTimeGrant> held = oneTime.stream().filter(g -> g.grantee().equals(grantee)).toList();
        for (OneTimeGrant grant : held) {
            World world = Bukkit.getWorld(grant.world());
            if (world != null) {
                Block block = world.getBlockAt(grant.x(), grant.y(), grant.z());
                Protection protection = bolt.api().findProtection(block);
                if (protection != null && !isWhitelisted(protection.getOwner(), grantee)) {
                    bolt.revokeAccess(protection, grantee);
                }
            }
        }
        if (!held.isEmpty()) {
            oneTime.removeAll(held);
            store.saveOneTimeGrants(oneTime);
        }
    }

    public List<OneTimeGrant> pendingOneTime() {
        return List.copyOf(oneTime);
    }
}

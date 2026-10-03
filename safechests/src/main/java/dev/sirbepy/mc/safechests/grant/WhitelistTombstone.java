package dev.sirbepy.mc.safechests.grant;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof that a whitelist revoke happened, kept around in case Bolt's async
 * {@code saveProtection} write for it had not landed yet when the server stopped.
 * Replayed on {@link GrantManager#loadAndSweep()} and pruned once {@link GrantManager}'s
 * retention window has passed, by which point Bolt's write is guaranteed to have landed.
 */
public record WhitelistTombstone(UUID owner, UUID grantee, Instant revokedAt) {
}

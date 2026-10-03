package dev.sirbepy.mc.safechests.grant;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof that a one-time grant's revoke happened, keyed by the protection's block location rather
 * than an owner (a one-time grant's own record carries no durable identity beyond where it sits).
 * See {@link WhitelistTombstone} for why this exists and how it is replayed.
 */
public record OneTimeTombstone(String world, int x, int y, int z, UUID grantee, Instant revokedAt) {
}

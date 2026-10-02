package dev.sirbepy.mc.safechests.grant;

import java.util.UUID;

/**
 * A single-open grant on one block protection. Persisted so a crash mid-open does not leave the access
 * entry on the chest forever; revoked on close, on quit, and swept on the next startup.
 */
public record OneTimeGrant(UUID owner, UUID grantee, String world, int x, int y, int z) {
}

package dev.sirbepy.mc.safechests.grant;

import java.time.Instant;
import java.util.UUID;

/** An owner-wide grant: {@code grantee} may open every chest {@code owner} has. Null expiry means permanent. */
public record WhitelistGrant(UUID owner, UUID grantee, Instant expiresAt) {

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}

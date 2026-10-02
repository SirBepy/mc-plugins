package dev.sirbepy.mc.safechests.request;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending request to open a locked chest. {@code id} is a random, unguessable UUID string: knowing it is
 * not enough to fulfill it, {@link RequestManager#validate(String, UUID)} also requires the responder's
 * UUID to match {@link #owner()}.
 */
public record AccessRequest(String id, UUID requester, UUID owner, String world, int x, int y, int z, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}

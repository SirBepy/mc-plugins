package dev.sirbepy.mc.safechests.request;

import org.bukkit.block.Block;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pending requests keyed by a random id, the per-player denial debounce, and the last chest a player
 * was denied at (so {@code /safe-chests-one-time} works when run from the clickable button, not just
 * while still looking at the chest).
 */
public final class RequestManager {

    // LinkedHashMap so pendingForOwner() can return requests oldest-first without a separate
    // creation-time field on AccessRequest.
    private final Map<String, AccessRequest> pending = new LinkedHashMap<>();
    private final Map<UUID, Instant> lastDenialAt = new HashMap<>();
    private final Map<UUID, Block> lastDeniedBlock = new HashMap<>();
    // Owners with a SafeChests answer form currently open, so auto-open never stacks a second
    // form on top of one the owner hasn't answered yet.
    private final Set<UUID> openFormOwners = new HashSet<>();
    private final Supplier<Instant> clock;

    public RequestManager() {
        this(Instant::now);
    }

    public RequestManager(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** @return true if a denial message should be sent now, false if still within the debounce window. */
    public boolean shouldNotifyDenial(UUID player, Duration debounce) {
        Instant now = clock.get();
        Instant last = lastDenialAt.get(player);
        if (last != null && now.isBefore(last.plus(debounce))) {
            return false;
        }
        lastDenialAt.put(player, now);
        return true;
    }

    public void rememberDeniedBlock(UUID player, Block block) {
        lastDeniedBlock.put(player, block);
    }

    public Optional<Block> lastDeniedBlock(UUID player) {
        return Optional.ofNullable(lastDeniedBlock.get(player));
    }

    /** Called on quit: nothing left to debounce or feed `/safe-chests-one-time` once the player is gone. */
    public void forgetPlayer(UUID player) {
        lastDenialAt.remove(player);
        lastDeniedBlock.remove(player);
        openFormOwners.remove(player);
    }

    /** @return the owner's live, non-expired requests, oldest first. Does not evict expired ones. */
    public List<AccessRequest> pendingForOwner(UUID owner) {
        Instant now = clock.get();
        return pending.values().stream()
                .filter(request -> request.owner().equals(owner) && !request.isExpired(now))
                .toList();
    }

    /** Marks that the owner now has a SafeChests answer/chooser form open. */
    public void markFormOpen(UUID owner) {
        openFormOwners.add(owner);
    }

    /**
     * Clears the owner's open-form flag. Called whenever a form's button click answers a request
     * (core's {@link dev.sirbepy.mc.core.bedrock.BedrockForms} gives no callback for a form closed
     * without a click), and when the owner explicitly runs {@code /safe-chests-pending} again.
     */
    public void clearFormOpen(UUID owner) {
        openFormOwners.remove(owner);
    }

    public boolean hasOpenForm(UUID owner) {
        return openFormOwners.contains(owner);
    }

    public AccessRequest create(UUID requester, UUID owner, String world, int x, int y, int z, Duration expiry) {
        String id = UUID.randomUUID().toString();
        AccessRequest request = new AccessRequest(id, requester, owner, world, x, y, z, clock.get().plus(expiry));
        pending.put(id, request);
        return request;
    }

    /**
     * Returns the request only if it exists, has not expired, and {@code claimedOwner} is really its
     * owner. This is the sole authorization check an owner's [Allow once]/[5 min]/[Always]/[Deny] button
     * runs through: the request id alone is not accepted as proof.
     */
    public Optional<AccessRequest> validate(String id, UUID claimedOwner) {
        AccessRequest request = pending.get(id);
        if (request == null) {
            return Optional.empty();
        }
        if (request.isExpired(clock.get())) {
            pending.remove(id);
            return Optional.empty();
        }
        if (!request.owner().equals(claimedOwner)) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public void remove(String id) {
        pending.remove(id);
    }

    /** @return requests that expired and were removed, so the caller can notify requesters. */
    public List<AccessRequest> sweepExpired() {
        Instant now = clock.get();
        List<AccessRequest> expired = pending.values().stream().filter(r -> r.isExpired(now)).toList();
        expired.forEach(r -> pending.remove(r.id()));
        return new ArrayList<>(expired);
    }

    public int pendingCount() {
        return pending.size();
    }
}

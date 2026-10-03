package dev.sirbepy.mc.tag.logic;

import java.util.List;
import java.util.UUID;

/**
 * Pure selection logic for who may receive an AFK auto-pass of IT. Kept Bukkit-free (candidates
 * are plain facts the caller already resolved) so the owner's eligibility rules are unit
 * testable without a server.
 */
public final class AfkEligibility {

    private AfkEligibility() {
    }

    /** One online, non-IT player's facts relevant to receiving an auto-pass. */
    public record Candidate(UUID uuid, boolean afk, boolean optedOut, boolean survivalOrAdventure) {
    }

    /**
     * Online, not AFK, not opted out, survival/adventure. The previous IT (who the current IT was
     * tagged or passed from) is avoided unless they are the only eligible player, so a two-person
     * game doesn't deadlock.
     */
    public static List<UUID> eligible(List<Candidate> candidates, UUID previousIt) {
        List<UUID> base = candidates.stream()
                .filter(c -> !c.afk() && !c.optedOut() && c.survivalOrAdventure())
                .map(Candidate::uuid)
                .toList();
        if (previousIt == null) {
            return base;
        }
        List<UUID> withoutPrevious = base.stream().filter(uuid -> !uuid.equals(previousIt)).toList();
        return withoutPrevious.isEmpty() ? base : withoutPrevious;
    }
}

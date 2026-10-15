package dev.sirbepy.mc.classes.talent.kits;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory state the Paladin's talents need between events: windows on a healed ally (Sanctuary),
 * marks on a mob Smite hit (Righteous Fury), per-player cooldowns for the once-per-N-minutes saves
 * (Guardian Light, Divine Bubble) and the heartbeat timers for Aura of Light and Ember of Hope.
 * Mob-keyed entries are left to expire on their own timestamp, like {@code WarriorState}'s Sunder,
 * since a dead mob's UUID is never looked up again; player-keyed entries are cleared on that
 * player's own quit in {@link #forgetPlayer}.
 */
final class PaladinState {

    // Righteous Fury: keyed by the mob Smite hit.
    final Map<UUID, Long> righteousFuryUntil = new HashMap<>();
    final Map<UUID, Double> righteousFuryPercent = new HashMap<>();
    final Map<UUID, UUID> righteousFuryCaster = new HashMap<>();

    // Sanctuary: keyed by the ally who was healed.
    final Map<UUID, Long> sanctuaryUntil = new HashMap<>();
    final Map<UUID, Double> sanctuaryPercent = new HashMap<>();

    // Heartbeat timers (Aura of Light, Ember of Hope), keyed by the Paladin.
    final Map<UUID, Long> auraOfLightLast = new HashMap<>();
    final Map<UUID, Long> emberOfHopeLast = new HashMap<>();

    // Once-per-N-minutes saves, keyed by the Paladin who owns the talent.
    final Map<UUID, Long> guardianLightCooldownUntil = new HashMap<>();
    final Map<UUID, Long> divineBubbleCooldownUntil = new HashMap<>();
    /** "mobs can't hurt you" window opened by a Divine Bubble proc, keyed by the saved player. */
    final Map<UUID, Long> divineBubbleInvulnUntil = new HashMap<>();

    /** Drops every map entry keyed by {@code player}, run on quit so nothing leaks across sessions. */
    void forgetPlayer(UUID player) {
        sanctuaryUntil.remove(player);
        sanctuaryPercent.remove(player);
        auraOfLightLast.remove(player);
        emberOfHopeLast.remove(player);
        guardianLightCooldownUntil.remove(player);
        divineBubbleCooldownUntil.remove(player);
        divineBubbleInvulnUntil.remove(player);
    }
}

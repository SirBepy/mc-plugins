package dev.sirbepy.mc.classes.talent.kits;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory state the Warrior's talents need between events: stacking windows (Frenzy, Fencer),
 * timed buffs (Riposte, Rally, Warhorn, a planted banner) and per-mob cooldowns (Heavy Blow, Sunder).
 * Everything here is keyed by UUID and cleared on logout ({@link #forgetPlayer}); mob-keyed entries
 * are left to expire on their own timestamp since a dead mob's UUID is never looked up again.
 */
final class WarriorState {

    final Map<UUID, Long> frenzyLastHit = new HashMap<>();
    final Map<UUID, Integer> frenzyStacks = new HashMap<>();
    /** playerId -> (mobId -> {hit count, last hit millis}), for "every 3rd sword hit on the same mob". */
    final Map<UUID, Map<UUID, long[]>> fencerHits = new HashMap<>();
    final Map<UUID, Long> sunderUntil = new HashMap<>();
    final Map<UUID, Double> sunderPercent = new HashMap<>();
    final Map<UUID, Long> riposteUntil = new HashMap<>();
    final Map<UUID, Long> bloodthirstLast = new HashMap<>();
    final Map<UUID, Long> inspireLast = new HashMap<>();
    final Map<UUID, Double> rallyPercent = new HashMap<>();
    final Map<UUID, Long> rallyUntil = new HashMap<>();
    final Map<UUID, Long> warhornUntil = new HashMap<>();
    final Map<UUID, Long> heavyBlowUntil = new HashMap<>();
    final Map<UUID, Long> undyingFuryCooldownUntil = new HashMap<>();
    final Map<UUID, Long> bannerGainUntil = new HashMap<>();
    final Map<UUID, Location> bannerZones = new HashMap<>();

    /** Drops every map entry keyed by {@code player}, run on quit so nothing leaks across sessions. */
    void forgetPlayer(UUID player) {
        frenzyLastHit.remove(player);
        frenzyStacks.remove(player);
        fencerHits.remove(player);
        riposteUntil.remove(player);
        bloodthirstLast.remove(player);
        inspireLast.remove(player);
        rallyPercent.remove(player);
        rallyUntil.remove(player);
        warhornUntil.remove(player);
        undyingFuryCooldownUntil.remove(player);
        bannerGainUntil.remove(player);
        bannerZones.remove(player);
    }
}

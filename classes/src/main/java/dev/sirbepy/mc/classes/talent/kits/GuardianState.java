package dev.sirbepy.mc.classes.talent.kits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the Guardian kit keeps in memory keyed by player, cleared on quit: Last Stand's own
 * window (the engine doesn't expose Classes#lastStandActive to kits), blocked-hit and grit stacks,
 * timed buffs the kit grants to allies, and the keystone/ability cooldown-adjacent trackers that
 * aren't plain {@code Slot} cooldowns.
 */
final class GuardianState {

    /** Shared Stand's half-strength window on an ally: the reduction percent and when it ends. */
    record SharedStand(double percent, long until) {
    }

    /** Oath's link: who the Guardian is linked to and until when. */
    record OathLink(UUID ally, long until) {
    }

    final Map<UUID, Long> lastStandUntil = new HashMap<>();
    final Map<UUID, Long> fortressUntil = new HashMap<>();
    final Map<UUID, Long> fortressAllyUntil = new HashMap<>();
    final Map<UUID, List<Long>> gritStacks = new HashMap<>();
    final Map<UUID, List<Long>> blockedHits = new HashMap<>();
    final Map<UUID, Long> retaliationReadyUntil = new HashMap<>();
    final Map<UUID, Long> fallImmuneUntil = new HashMap<>();
    final Map<UUID, Long> momentumReadyAt = new HashMap<>();
    final Map<UUID, Long> lifelineReadyAt = new HashMap<>();
    final Map<UUID, Long> guardianSpiritReadyAt = new HashMap<>();
    final Map<UUID, SharedStand> sharedStand = new HashMap<>();
    final Map<UUID, OathLink> oathLink = new HashMap<>();
    /** Guards against a redirected hit (Guardian Angel, Oath) re-triggering itself on the Guardian. */
    boolean redirecting;

    void forget(UUID player) {
        lastStandUntil.remove(player);
        fortressUntil.remove(player);
        fortressAllyUntil.remove(player);
        gritStacks.remove(player);
        blockedHits.remove(player);
        retaliationReadyUntil.remove(player);
        fallImmuneUntil.remove(player);
        momentumReadyAt.remove(player);
        lifelineReadyAt.remove(player);
        guardianSpiritReadyAt.remove(player);
        sharedStand.remove(player);
        oathLink.remove(player);
    }

    static void pushStack(Map<UUID, List<Long>> map, UUID player, long expiry, int cap) {
        List<Long> list = map.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(expiry);
        while (list.size() > cap) list.remove(0);
    }

    static int countActive(Map<UUID, List<Long>> map, UUID player, long now) {
        List<Long> list = map.get(player);
        if (list == null) return 0;
        list.removeIf(e -> e <= now);
        return list.size();
    }
}

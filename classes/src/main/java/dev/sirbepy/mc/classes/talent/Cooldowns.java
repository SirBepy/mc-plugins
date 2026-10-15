package dev.sirbepy.mc.classes.talent;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Cooldowns for the kit abilities (ability 2, ultimates, keystone abilities), in memory like ability
 * 1's: a restart makes everything ready. Ability 1 keeps its cooldown in {@code Classes}; talents
 * reach both through {@link #adjust} and {@link #shorten}.
 */
public final class Cooldowns {

    /** Talent cooldown changes, applied in order; e.g. Veteran: (p, slot, s) -> slot == ONE ? s - 10 : s. */
    public interface Modifier {
        double apply(Player player, Slot slot, double seconds);
    }

    private final LongSupplier now;
    private final Map<UUID, Map<Slot, Long>> until = new HashMap<>();
    private final List<Modifier> modifiers = new ArrayList<>();

    public Cooldowns(LongSupplier now) {
        this.now = now;
    }

    public void addModifier(Modifier modifier) {
        modifiers.add(modifier);
    }

    /** {@code base} after every talent modifier; abilities 1 and 2 never under 30 s, ultimates fixed. */
    public int adjust(Player player, Slot slot, int base) {
        if (slot == Slot.ULTIMATE) return Slot.ULTIMATE_COOLDOWN_SECONDS;
        double seconds = base;
        for (Modifier m : modifiers) seconds = m.apply(player, slot, seconds);
        return (int) Math.max(Slot.MIN_COOLDOWN_SECONDS, Math.round(seconds));
    }

    public long leftMillis(UUID player, Slot slot) {
        Map<Slot, Long> map = until.get(player);
        if (map == null) return 0;
        return Math.max(0, map.getOrDefault(slot, 0L) - now.getAsLong());
    }

    public void start(UUID player, Slot slot, int seconds) {
        until.computeIfAbsent(player, k -> new EnumMap<>(Slot.class)).put(slot, now.getAsLong() + seconds * 1000L);
    }

    /** Takes {@code millis} off a running kit-ability cooldown (Chain Kill, Cold Snap...). */
    public void shorten(UUID player, Slot slot, long millis) {
        Map<Slot, Long> map = until.get(player);
        if (map != null && map.containsKey(slot)) map.put(slot, map.get(slot) - millis);
    }

    public void ready(UUID player, Slot slot) {
        Map<Slot, Long> map = until.get(player);
        if (map != null) map.remove(slot);
    }

    public void clear(UUID player) {
        until.remove(player);
    }
}

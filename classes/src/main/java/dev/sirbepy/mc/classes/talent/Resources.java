package dev.sirbepy.mc.classes.talent;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;

/**
 * Each player's class resource (Rage, Focus...), 0 to 100, in memory only: it resets on death, class
 * change, logout and restart. It drains {@link #DRAIN_PER_SECOND} a second once the player has been
 * out of combat for {@link #DRAIN_AFTER_MILLIS}. Talents scale gain and drain through modifiers.
 */
public final class Resources {

    public static final double MAX = 100;
    static final double DRAIN_PER_SECOND = 5;
    static final long DRAIN_AFTER_MILLIS = 10_000;
    /** The bar stays up this long after the last fight even when empty. */
    static final long SHOW_AFTER_COMBAT_MILLIS = 8_000;

    /** Hears the bar filling up, for the "ultimate ready" chime. */
    public interface FullListener {
        void filled(Player player);
    }

    private final LongSupplier now;
    private final Map<UUID, Double> values = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private final List<ToDoubleFunction<Player>> gainModifiers = new ArrayList<>();
    private final List<ToDoubleFunction<Player>> drainModifiers = new ArrayList<>();
    private FullListener full = player -> { };

    public Resources(LongSupplier now) {
        this.now = now;
    }

    public void onFull(FullListener full) {
        this.full = full;
    }

    /** A multiplier on every gain (Accelerate 1.25, War Banner 1.5...); 1 means no change. */
    public void addGainModifier(ToDoubleFunction<Player> multiplier) {
        gainModifiers.add(multiplier);
    }

    /** A multiplier on the out-of-combat drain (Thick Blood 0.75); 1 means no change. */
    public void addDrainModifier(ToDoubleFunction<Player> multiplier) {
        drainModifiers.add(multiplier);
    }

    public double get(UUID player) {
        return values.getOrDefault(player, 0.0);
    }

    public boolean isFull(UUID player) {
        return get(player) >= MAX;
    }

    /** Adds {@code base} times every gain modifier, capped at 100. Also counts as being in combat. */
    public void gain(Player player, double base) {
        if (base <= 0) return;
        double amount = base;
        for (ToDoubleFunction<Player> m : gainModifiers) amount *= m.applyAsDouble(player);
        UUID id = player.getUniqueId();
        double before = get(id);
        double after = Math.min(MAX, before + amount);
        values.put(id, after);
        tagCombat(id);
        if (before < MAX && after >= MAX) full.filled(player);
    }

    /** Spends {@code amount} if the player has it; false (and nothing spent) if not. */
    public boolean spend(UUID player, double amount) {
        double have = get(player);
        if (have < amount) return false;
        values.put(player, have - amount);
        return true;
    }

    public void set(UUID player, double value) {
        values.put(player, Math.max(0, Math.min(MAX, value)));
    }

    public void reset(UUID player) {
        values.remove(player);
        lastCombat.remove(player);
    }

    public void tagCombat(UUID player) {
        lastCombat.put(player, now.getAsLong());
    }

    public boolean inCombat(UUID player) {
        return now.getAsLong() - lastCombat.getOrDefault(player, Long.MIN_VALUE / 2) < SHOW_AFTER_COMBAT_MILLIS;
    }

    /** Whether the bar should be on screen: something in it, or a fight just now. */
    public boolean visible(UUID player) {
        return get(player) > 0 || inCombat(player);
    }

    /** Called once a second per player with a class. */
    public void tick(Player player) {
        UUID id = player.getUniqueId();
        double have = get(id);
        if (have <= 0) return;
        if (now.getAsLong() - lastCombat.getOrDefault(id, Long.MIN_VALUE / 2) < DRAIN_AFTER_MILLIS) return;
        double drain = DRAIN_PER_SECOND;
        for (ToDoubleFunction<Player> m : drainModifiers) drain *= m.applyAsDouble(player);
        values.put(id, Math.max(0, have - drain));
    }
}

package dev.sirbepy.mc.merfolk.trade;

import org.bukkit.Material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Kingdom-wide caps on the Treasurer's rares, counted across every Treasurer: 1 trident and 3
 * sponges per 12 hours, 1 heart of the sea per 24 hours (the council's numbers from 2026-10-08).
 * Each sale's time is kept, so a window is a rolling one, not a reset at a fixed hour.
 */
public final class RareLedger {

    public record Cap(int max, Duration window) {
    }

    public static final Map<Material, Cap> CAPS = Map.of(
            Material.TRIDENT, new Cap(1, Duration.ofHours(12)),
            Material.SPONGE, new Cap(3, Duration.ofHours(12)),
            Material.HEART_OF_THE_SEA, new Cap(1, Duration.ofHours(24)));

    private final Map<Material, List<Long>> sales = new EnumMap<>(Material.class);
    private final LongSupplier clock;
    private final Runnable save;

    public RareLedger(LongSupplier clock, Runnable save) {
        this.clock = clock;
        this.save = save;
    }

    public static boolean isRare(Material item) {
        return CAPS.containsKey(item);
    }

    /** Millis until one more {@code item} may be sold; 0 when it may be sold now. */
    public long waitFor(Material item) {
        Cap cap = CAPS.get(item);
        if (cap == null) return 0;
        List<Long> recent = recent(item, cap);
        if (recent.size() < cap.max()) return 0;
        long oldestCounted = recent.get(recent.size() - cap.max());
        return oldestCounted + cap.window().toMillis() - clock.getAsLong();
    }

    public void record(Material item) {
        Cap cap = CAPS.get(item);
        if (cap == null) return;
        List<Long> recent = new ArrayList<>(recent(item, cap));
        recent.add(clock.getAsLong());
        sales.put(item, recent);
        save.run();
    }

    /** Forgets every sale, so all three rares are buyable again (an admin refund, or the e2e reset). */
    public void clear() {
        sales.clear();
        save.run();
    }

    public Map<Material, List<Long>> snapshot() {
        return Map.copyOf(sales);
    }

    public void load(Material item, List<Long> times) {
        sales.put(item, new ArrayList<>(times));
    }

    private List<Long> recent(Material item, Cap cap) {
        long since = clock.getAsLong() - cap.window().toMillis();
        return sales.getOrDefault(item, List.of()).stream().filter(t -> t > since).sorted().toList();
    }
}

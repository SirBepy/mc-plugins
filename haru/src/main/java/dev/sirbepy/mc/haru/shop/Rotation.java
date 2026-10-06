package dev.sirbepy.mc.haru.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Deterministic picks from a pool: the same period always yields the same selection, so a restart
 * mid-rotation shows the same shelf and the stock ledger still lines up.
 */
public final class Rotation {

    private Rotation() {
    }

    public static Random random(String salt, long period) {
        return new Random(salt.hashCode() * 0x9E3779B97F4A7C15L ^ (period * 0xBF58476D1CE4E5B9L));
    }

    /** {@code count} distinct entries (fewer if the pool is smaller), in pool-shuffled order. */
    public static <T> List<T> pick(List<T> pool, String salt, long period, int count) {
        List<T> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random(salt, period));
        return List.copyOf(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    /** A Legendary Trade for this rotation, if the roll comes up. */
    public static <T> Optional<T> legendary(List<T> pool, long rotation, double chance) {
        Random random = random("legendary", rotation);
        if (pool.isEmpty() || random.nextDouble() >= chance) {
            return Optional.empty();
        }
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }
}

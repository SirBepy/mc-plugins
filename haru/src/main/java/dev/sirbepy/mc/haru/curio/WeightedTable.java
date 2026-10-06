package dev.sirbepy.mc.haru.curio;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A weighted random draw. */
public final class WeightedTable<T> {

    private record Entry<T>(T value, int weight) {
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private int total;

    public WeightedTable<T> add(int weight, T value) {
        entries.add(new Entry<>(value, weight));
        total += weight;
        return this;
    }

    public T roll(Random random) {
        int pick = random.nextInt(total);
        for (Entry<T> entry : entries) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry.value();
            }
        }
        return entries.getLast().value();
    }

    public List<T> values() {
        return entries.stream().map(Entry::value).toList();
    }
}

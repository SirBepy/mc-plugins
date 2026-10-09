package dev.sirbepy.mc.endfight.reward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure: where everything goes in a Dragon Hoard's 27 slots (the radial layout Joe approved from
 * 10ca's renders, 2026-10-09; ported from 10ca's {@code radial()} mockup). The centre slot holds
 * the best thing in the box; the rest spreads outward in mirrored groups of slots (left-right and
 * top-bottom), nearest the centre first:
 * <ul>
 *   <li>Specials (the final-blow helm, then each roll's armor piece or head), one per slot: with
 *   an odd number, the first takes the centre; the rest go in pairs into the next 2-slot groups
 *   (above and below the centre, then left and right of it).</li>
 *   <li>Valuables in {@link #PRIORITY} order, the first taking the centre when no special did;
 *   each takes the nearest free group with no more slots than it has items, its count split
 *   evenly over that group (any remainder on the first slots).</li>
 *   <li>Leftover groups go to whichever filler has the most items per slot, re-split, while every
 *   slot keeps at least one.</li>
 * </ul>
 * One change from the mockup: a valuable with fewer items than any free group (a single scrap or
 * apple once a special has the centre) takes the first slots of the nearest group instead of being
 * dropped, and the rest of that group stays free.
 */
public final class HoardLayout {

    public static final int SIZE = 27;
    private static final int COLUMNS = 9;
    private static final int MAX_STACK = 64;

    public static final List<String> PRIORITY =
            List.of("gapple", "scrap", "diamond", "shell", "emerald", "carrot", "lapis", "gold", "iron");
    /** In the mockup's order, which breaks ties between fillers with as many items per slot. */
    static final List<String> FILLER = List.of("iron", "gold", "lapis", "carrot", "emerald");

    /** What goes in one slot: a {@link #PRIORITY} key, or {@code special:<n>} for the n-th special. */
    public record Slot(String key, int count) {
    }

    private HoardLayout() {
    }

    public static Slot[] layout(int specials, Map<String, Integer> counts) {
        return layout(specials, false, counts);
    }

    /**
     * @param specials       how many specials there are (the caller keeps their order)
     * @param firstIsCentred the first special always takes the centre, even with an even count:
     *                       the final-blow helm is "the best item" (10ca's spec)
     * @param counts         each {@link #PRIORITY} key's total count (missing or 0 = none)
     * @return 27 slots, row-major, null where empty
     */
    public static Slot[] layout(int specials, boolean firstIsCentred, Map<String, Integer> counts) {
        Slot[] out = new Slot[SIZE];
        List<List<Integer>> groups = slotGroups();
        int centre = groups.get(0).get(0);
        groups.remove(0);
        int next = 0;
        boolean centreSpecial = specials % 2 == 1 || (firstIsCentred && specials > 0);
        if (centreSpecial) {
            out[centre] = new Slot("special:" + next++, 1);
        }
        while (next < specials) {
            List<Integer> pair = takeFirst(groups, 2);
            if (pair == null) {
                pair = takeFirst(groups, 4);
            }
            if (pair == null) {
                break;
            }
            for (int slot : pair) {
                if (next < specials) {
                    out[slot] = new Slot("special:" + next++, 1);
                }
            }
        }

        Map<String, List<Integer>> owned = new LinkedHashMap<>();
        List<String> items = new ArrayList<>();
        for (String key : PRIORITY) {
            if (counts.getOrDefault(key, 0) > 0) {
                items.add(key);
            }
        }
        if (!centreSpecial && !items.isEmpty()) {
            owned.computeIfAbsent(items.remove(0), k -> new ArrayList<>()).add(centre);
        }
        for (String key : items) {
            int count = counts.get(key);
            List<Integer> group = null;
            for (List<Integer> candidate : groups) {
                if (candidate.size() <= count && Math.ceil(count / (double) candidate.size()) <= MAX_STACK) {
                    group = candidate;
                    break;
                }
            }
            if (group != null) {
                groups.remove(group);
                owned.computeIfAbsent(key, k -> new ArrayList<>()).addAll(group);
            } else if (!groups.isEmpty()) {
                List<Integer> nearest = groups.remove(0);
                owned.computeIfAbsent(key, k -> new ArrayList<>()).addAll(nearest.subList(0, Math.min(count, nearest.size())));
                if (count < nearest.size()) {
                    groups.add(0, new ArrayList<>(nearest.subList(count, nearest.size())));
                }
            } else {
                stealSlots(key, count, counts, owned);
            }
        }
        for (List<Integer> group : new ArrayList<>(groups)) {
            String best = null;
            double bestPerSlot = -1;
            for (String key : FILLER) {
                if (!owned.containsKey(key)) {
                    continue;
                }
                double perSlot = counts.get(key) / (double) owned.get(key).size();
                if (perSlot > bestPerSlot) {
                    best = key;
                    bestPerSlot = perSlot;
                }
            }
            if (best != null && counts.get(best) / (owned.get(best).size() + group.size()) >= 1) {
                owned.get(best).addAll(group);
                groups.remove(group);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : owned.entrySet()) {
            List<Integer> slots = entry.getValue();
            int[] split = split(counts.get(entry.getKey()), slots.size());
            for (int i = 0; i < slots.size(); i++) {
                out[slots.get(i)] = new Slot(entry.getKey(), split[i]);
            }
        }
        return out;
    }

    /** Mirror groups of slots (left-right and top-bottom), nearest the centre slot first; the
     * centre slot is the first, alone. */
    static List<List<Integer>> slotGroups() {
        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                cells.add(new int[] {y, x});
            }
        }
        // Stable sort: ties keep row-major order, like the mockup's Python sort.
        cells.sort(Comparator.<int[]>comparingDouble(c -> Math.abs(c[1] - 4) + Math.abs(c[0] - 1) * 0.9)
                .thenComparing(c -> c[0] != 1));
        boolean[] seen = new boolean[SIZE];
        List<List<Integer>> groups = new ArrayList<>();
        for (int[] cell : cells) {
            int y = cell[0];
            int x = cell[1];
            if (seen[y * COLUMNS + x]) {
                continue;
            }
            java.util.TreeSet<Integer> group = new java.util.TreeSet<>();
            group.add(y * COLUMNS + x);
            group.add(y * COLUMNS + (8 - x));
            group.add((2 - y) * COLUMNS + x);
            group.add((2 - y) * COLUMNS + (8 - x));
            for (int slot : group) {
                seen[slot] = true;
            }
            groups.add(new ArrayList<>(group));
        }
        return groups;
    }

    static int[] split(int n, int k) {
        int[] out = new int[k];
        int base = n / k;
        int extra = n % k;
        for (int i = 0; i < k; i++) {
            out[i] = base + (i < extra ? 1 : 0);
        }
        return out;
    }

    /**
     * The box is full (many specials in a low tier): {@code key} takes the outermost slots of the
     * filler holding the most slots, as many as it needs and that filler can spare while every one
     * of its slots stays at 64 or under. Nothing in a hoard is ever dropped.
     */
    private static void stealSlots(String key, int count, Map<String, Integer> counts, Map<String, List<Integer>> owned) {
        int need = (int) Math.ceil(count / (double) MAX_STACK);
        String donor = null;
        int spare = 0;
        for (String filler : FILLER) {
            List<Integer> slots = owned.get(filler);
            if (slots == null || filler.equals(key)) {
                continue;
            }
            int canGive = slots.size() - (int) Math.ceil(counts.get(filler) / (double) MAX_STACK);
            if (canGive > spare) {
                donor = filler;
                spare = canGive;
            }
        }
        if (donor == null) {
            return;
        }
        List<Integer> from = owned.get(donor);
        List<Integer> taken = new ArrayList<>();
        for (int i = 0; i < Math.min(need, spare) && !from.isEmpty(); i++) {
            taken.add(from.remove(from.size() - 1));
        }
        owned.computeIfAbsent(key, k -> new ArrayList<>()).addAll(taken);
    }

    private static List<Integer> takeFirst(List<List<Integer>> groups, int size) {
        for (List<Integer> group : groups) {
            if (group.size() == size) {
                groups.remove(group);
                return group;
            }
        }
        return null;
    }
}

package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One tree snapshot from the server (TreeChannel in the Classes plugin, which documents every field).
 * The server already applied every rule; this only reads what to draw.
 */
public record TreeData(boolean open, String className, int color, boolean own, int level, int points, int free,
                       String resource, int resourceValue, List<Branch> branches, List<Ability> abilities,
                       List<Node> nodes) {

    /** The only snapshot shape this mod understands. */
    static final int VERSION = 1;
    static final int KEYSTONE_TIER = 5;
    static final int KEYSTONE_LEVEL = 20;

    public record Branch(String name, int spent) {
    }

    public record Ability(String slot, String label, String name, String key, String keyShort, int unlockLevel,
                          boolean unlocked, String texture, List<String> lines) {
    }

    /** {@code state} is the server's Talents.Result for one more rank: OK, NO_POINTS, MAXED, TIER_LOCKED... */
    public record Node(String id, String name, int branch, int tier, int gate, int rank, int max, boolean keystone,
                       String icon, String state, boolean locked, List<String> lines, String lock) {

        boolean maxed() {
            return rank >= max;
        }
    }

    /** Null for anything that isn't a version-1 tree snapshot. */
    static TreeData parse(JsonObject o) {
        if (!"tree".equals(str(o, "type")) || num(o, "v") != VERSION) return null;
        List<Branch> branches = new ArrayList<>();
        for (JsonElement e : arr(o, "branches")) {
            JsonObject b = e.getAsJsonObject();
            branches.add(new Branch(str(b, "name"), num(b, "spent")));
        }
        List<Ability> abilities = new ArrayList<>();
        for (JsonElement e : arr(o, "abilities")) {
            JsonObject a = e.getAsJsonObject();
            abilities.add(new Ability(str(a, "slot"), str(a, "label"), str(a, "name"), str(a, "key"), str(a, "keyShort"),
                    num(a, "unlockLevel"), bool(a, "unlocked"), str(a, "texture"), strings(a, "lines")));
        }
        List<Node> nodes = new ArrayList<>();
        for (JsonElement e : arr(o, "nodes")) {
            JsonObject n = e.getAsJsonObject();
            nodes.add(new Node(str(n, "id"), str(n, "name"), num(n, "branch"), num(n, "tier"), num(n, "gate"),
                    num(n, "rank"), num(n, "max"), bool(n, "keystone"), str(n, "icon"), str(n, "state"),
                    bool(n, "locked"), strings(n, "lines"), str(n, "lock")));
        }
        return new TreeData(bool(o, "open"), str(o, "class"), num(o, "color"), bool(o, "own"), num(o, "level"),
                num(o, "points"), num(o, "free"), str(o, "resource"), num(o, "resourceValue"),
                List.copyOf(branches), List.copyOf(abilities), List.copyOf(nodes));
    }

    /** Ranks spent in {@code branch} below {@code tier}: what that tier's gate counts. */
    int spentBelow(int branch, int tier) {
        int sum = 0;
        for (Node n : nodes) {
            if (n.branch() == branch && n.tier() < tier) sum += n.rank();
        }
        return sum;
    }

    /** Whether {@code branch}'s tier {@code tier} has its points (a keystone still needs the level too). */
    boolean tierOpen(int branch, int tier) {
        int gate = 0;
        for (Node n : nodes) {
            if (n.branch() == branch && n.tier() == tier) gate = n.gate();
        }
        return spentBelow(branch, tier) >= gate;
    }

    /** {@code branch}'s nodes in {@code tier}, in the kit's display order. */
    List<Node> row(int branch, int tier) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes) {
            if (n.branch() == branch && n.tier() == tier) out.add(n);
        }
        return out;
    }

    Node node(String id) {
        for (Node n : nodes) {
            if (n.id().equals(id)) return n;
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, key)) out.add(e.getAsString());
        return List.copyOf(out);
    }
}

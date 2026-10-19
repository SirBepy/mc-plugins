package dev.sirbepy.mc.treemod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One tree snapshot from the server (TreeChannel in the Classes plugin, which documents every field).
 * The server already applied every rule; this only reads what to draw.
 */
public record TreeData(boolean open, String className, int color, boolean own, int level, int points, int free,
                       int keystoneLevel, String resource, int resourceValue, List<Branch> branches,
                       List<Ability> abilities, List<Node> nodes) {

    /** The only snapshot shape this mod understands. */
    static final int VERSION = 1;
    /** The server's level gate on keystones before v1 carried {@code keystoneLevel}; kept so a mod ahead of an old server still works. */
    private static final int KEYSTONE_LEVEL_FALLBACK = 20;

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
        if (!"tree".equals(JsonParse.str(o, "type")) || JsonParse.num(o, "v") != VERSION) return null;
        List<Branch> branches = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "branches")) {
            JsonObject b = e.getAsJsonObject();
            branches.add(new Branch(JsonParse.str(b, "name"), JsonParse.num(b, "spent")));
        }
        List<Ability> abilities = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "abilities")) {
            JsonObject a = e.getAsJsonObject();
            abilities.add(new Ability(JsonParse.str(a, "slot"), JsonParse.str(a, "label"), JsonParse.str(a, "name"),
                    JsonParse.str(a, "key"), JsonParse.str(a, "keyShort"), JsonParse.num(a, "unlockLevel"),
                    JsonParse.bool(a, "unlocked"), JsonParse.str(a, "texture"), JsonParse.strings(a, "lines")));
        }
        List<Node> nodes = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "nodes")) {
            JsonObject n = e.getAsJsonObject();
            nodes.add(new Node(JsonParse.str(n, "id"), JsonParse.str(n, "name"), JsonParse.num(n, "branch"),
                    JsonParse.num(n, "tier"), JsonParse.num(n, "gate"), JsonParse.num(n, "rank"), JsonParse.num(n, "max"),
                    JsonParse.bool(n, "keystone"), JsonParse.str(n, "icon"), JsonParse.str(n, "state"),
                    JsonParse.bool(n, "locked"), JsonParse.strings(n, "lines"), JsonParse.str(n, "lock")));
        }
        return new TreeData(JsonParse.bool(o, "open"), JsonParse.str(o, "class"), JsonParse.num(o, "color"),
                JsonParse.bool(o, "own"), JsonParse.num(o, "level"), JsonParse.num(o, "points"), JsonParse.num(o, "free"),
                JsonParse.numOr(o, "keystoneLevel", KEYSTONE_LEVEL_FALLBACK), JsonParse.str(o, "resource"),
                JsonParse.num(o, "resourceValue"), List.copyOf(branches), List.copyOf(abilities), List.copyOf(nodes));
    }

    /** The highest tier among this tree's nodes (the keystone row), read from the snapshot instead of a hardcoded constant. */
    int topTier() {
        int max = 1;
        for (Node n : nodes) {
            if (n.tier() > max) max = n.tier();
        }
        return max;
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
}

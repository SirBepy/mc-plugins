package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@code /class} hub snapshot from the server (TreeChannel#hubSnapshot in the Classes plugin):
 * the short menu a player with a class gets instead of the full seven-class list.
 */
public record HubData(boolean open, String className, int color, int level, int points, List<Entry> entries) {

    /** The only snapshot shape this mod understands. */
    static final int VERSION = 1;

    public record Entry(String id, String label, List<String> lines) {
    }

    /** Null for anything that isn't a version-1 hub snapshot. */
    static HubData parse(JsonObject o) {
        if (!"hub".equals(str(o, "type")) || num(o, "v") != VERSION) return null;
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : arr(o, "entries")) {
            JsonObject en = e.getAsJsonObject();
            entries.add(new Entry(str(en, "id"), str(en, "label"), strings(en, "lines")));
        }
        return new HubData(bool(o, "open"), str(o, "class"), num(o, "color"), num(o, "level"), num(o, "points"), List.copyOf(entries));
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

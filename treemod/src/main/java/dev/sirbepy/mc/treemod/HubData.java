package dev.sirbepy.mc.treemod;

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
        if (!"hub".equals(JsonParse.str(o, "type")) || JsonParse.num(o, "v") != VERSION) return null;
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "entries")) {
            JsonObject en = e.getAsJsonObject();
            entries.add(new Entry(JsonParse.str(en, "id"), JsonParse.str(en, "label"), JsonParse.strings(en, "lines")));
        }
        return new HubData(JsonParse.bool(o, "open"), JsonParse.str(o, "class"), JsonParse.num(o, "color"),
                JsonParse.num(o, "level"), JsonParse.num(o, "points"), List.copyOf(entries));
    }
}

package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@code /class} browse snapshot from the server (TreeChannel#classListSnapshot in the Classes
 * plugin): every class's passive, ability and weakness at the player's own level in it, so a player
 * can compare before picking. Mirrors {@link HubData} and {@link GuildData}'s shape.
 */
public record ClassListData(boolean open, List<Entry> entries) {

    static final int VERSION = 1;

    public record Entry(String id, String name, int color, boolean own, boolean locked, List<String> lines) {
    }

    /** Null for anything that isn't a version-1 classlist snapshot. */
    static ClassListData parse(JsonObject o) {
        if (!"classlist".equals(str(o, "type")) || num(o, "v") != VERSION) return null;
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : arr(o, "entries")) {
            JsonObject en = e.getAsJsonObject();
            entries.add(new Entry(str(en, "id"), str(en, "name"), num(en, "color"), bool(en, "own"),
                    bool(en, "locked"), strings(en, "lines")));
        }
        return new ClassListData(bool(o, "open"), List.copyOf(entries));
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

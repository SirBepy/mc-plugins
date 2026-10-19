package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared field-readers for a server snapshot's JSON (TreeData, HudData, HubData, GuildData, QuestData,
 * ClassListData, which each used to paste a private copy of these). Every value is optional and
 * malformed input is never trusted, so every method falls back instead of throwing.
 */
final class JsonParse {

    private JsonParse() {
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static int num(JsonObject o, String key) {
        return numOr(o, key, 0);
    }

    /** {@code fallback} for a field an older server's snapshot never sent. */
    static int numOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }

    static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, key)) out.add(e.getAsString());
        return List.copyOf(out);
    }
}

package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One Spirit Requests snapshot from the server (QuestChannel in the Haru plugin, which documents every
 * field). The server already applied every rule; this only reads what the HUD draws.
 */
public record QuestData(boolean enabled, List<Request> requests) {

    /** The only snapshot shape this mod understands. */
    static final int VERSION = 1;

    public record Request(String text, String difficulty, int progress, int amount, boolean claimed, boolean ready) {
    }

    /** Null for anything that isn't a version-1 quest snapshot. */
    static QuestData parse(JsonObject o) {
        if (!"quest".equals(str(o, "type")) || num(o, "v") != VERSION) {
            return null;
        }
        List<Request> requests = new ArrayList<>();
        for (JsonElement e : arr(o, "requests")) {
            JsonObject r = e.getAsJsonObject();
            requests.add(new Request(str(r, "text"), str(r, "difficulty"), num(r, "progress"), num(r, "amount"),
                    bool(r, "claimed"), bool(r, "ready")));
        }
        return new QuestData(bool(o, "enabled"), List.copyOf(requests));
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
}

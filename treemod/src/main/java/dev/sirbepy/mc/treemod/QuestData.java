package dev.sirbepy.mc.treemod;

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
        if (!"quest".equals(JsonParse.str(o, "type")) || JsonParse.num(o, "v") != VERSION) {
            return null;
        }
        List<Request> requests = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "requests")) {
            JsonObject r = e.getAsJsonObject();
            requests.add(new Request(JsonParse.str(r, "text"), JsonParse.str(r, "difficulty"), JsonParse.num(r, "progress"),
                    JsonParse.num(r, "amount"), JsonParse.bool(r, "claimed"), JsonParse.bool(r, "ready")));
        }
        return new QuestData(JsonParse.bool(o, "enabled"), List.copyOf(requests));
    }
}

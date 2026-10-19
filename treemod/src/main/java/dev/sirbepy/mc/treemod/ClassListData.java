package dev.sirbepy.mc.treemod;

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
        if (!"classlist".equals(JsonParse.str(o, "type")) || JsonParse.num(o, "v") != VERSION) return null;
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "entries")) {
            JsonObject en = e.getAsJsonObject();
            entries.add(new Entry(JsonParse.str(en, "id"), JsonParse.str(en, "name"), JsonParse.num(en, "color"),
                    JsonParse.bool(en, "own"), JsonParse.bool(en, "locked"), JsonParse.strings(en, "lines")));
        }
        return new ClassListData(JsonParse.bool(o, "open"), List.copyOf(entries));
    }
}

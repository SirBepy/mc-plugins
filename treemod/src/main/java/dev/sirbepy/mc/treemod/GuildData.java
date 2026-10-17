package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@code guild:ui} snapshot from the server (GuildChannel in the Guild plugin): a screen name, a
 * title, header lines and a flat list of rows this mod renders as a {@link GuildScreen}. Every row's
 * label and tooltip lines are precomputed server-side, so this mod holds no Guild rules; a click only
 * echoes the row's id back as {@code {"op":"click",...}}.
 */
public record GuildData(String screen, String title, List<String> subtitle, List<Row> rows) {

    static final int VERSION = 1;

    public record Row(String id, String label, boolean clickable, List<String> lines) {
    }

    /** Null for anything that isn't a version-1 guild snapshot. */
    static GuildData parse(JsonObject o) {
        if (!"guild".equals(str(o, "type")) || num(o, "v") != VERSION) return null;
        List<Row> rows = new ArrayList<>();
        for (JsonElement e : arr(o, "rows")) {
            JsonObject r = e.getAsJsonObject();
            rows.add(new Row(str(r, "id"), str(r, "label"), bool(r, "clickable"), strings(r, "lines")));
        }
        return new GuildData(str(o, "screen"), str(o, "title"), strings(o, "subtitle"), List.copyOf(rows));
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

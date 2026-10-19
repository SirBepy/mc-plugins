package dev.sirbepy.mc.treemod;

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
        if (!"guild".equals(JsonParse.str(o, "type")) || JsonParse.num(o, "v") != VERSION) return null;
        List<Row> rows = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "rows")) {
            JsonObject r = e.getAsJsonObject();
            rows.add(new Row(JsonParse.str(r, "id"), JsonParse.str(r, "label"), JsonParse.bool(r, "clickable"),
                    JsonParse.strings(r, "lines")));
        }
        return new GuildData(JsonParse.str(o, "screen"), JsonParse.str(o, "title"), JsonParse.strings(o, "subtitle"),
                List.copyOf(rows));
    }
}

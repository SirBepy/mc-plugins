package dev.sirbepy.mc.treemod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One spell-bar snapshot from the server (TreeChannel#hudSnapshot in the Classes plugin): the
 * resource, the combo clicked so far ("R-L", "" for none) and each slot's combo and cooldown.
 */
public record HudData(boolean show, int color, String resource, int value, int max, boolean armed, String combo,
                      List<Ability> abilities) {

    /** The only snapshot shape this mod understands. */
    static final int VERSION = 1;

    /** {@code cd} is whole seconds of cooldown left when the server sent it. */
    public record Ability(String slot, String name, String combo, String texture, boolean unlocked, int unlockLevel,
                          int cd, boolean ready) {
    }

    /** Null for anything that isn't a version-1 HUD snapshot. */
    static HudData parse(JsonObject o) {
        if (!"hud".equals(str(o, "type")) || num(o, "v") != VERSION) return null;
        List<Ability> abilities = new ArrayList<>();
        for (JsonElement e : arr(o, "abilities")) {
            JsonObject a = e.getAsJsonObject();
            abilities.add(new Ability(str(a, "slot"), str(a, "name"), str(a, "combo"), str(a, "texture"),
                    bool(a, "unlocked"), num(a, "unlockLevel"), num(a, "cd"), bool(a, "ready")));
        }
        String combo = str(o, "combo");
        return new HudData(bool(o, "show"), num(o, "color"), str(o, "resource"), num(o, "value"), Math.max(1, num(o, "max")),
                bool(o, "armed"), combo == null ? "" : combo, List.copyOf(abilities));
    }

    /** How many of {@code ability}'s combo clicks the combo so far already matches; -1 once it went another way. */
    int matched(Ability ability) {
        if (combo.isEmpty() || ability.combo() == null) return 0;
        return ability.combo().startsWith(combo) ? (combo.length() + 1) / 2 : -1;
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

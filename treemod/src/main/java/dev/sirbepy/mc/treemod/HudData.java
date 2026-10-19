package dev.sirbepy.mc.treemod;

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
        if (!"hud".equals(JsonParse.str(o, "type")) || JsonParse.num(o, "v") != VERSION) return null;
        List<Ability> abilities = new ArrayList<>();
        for (JsonElement e : JsonParse.arr(o, "abilities")) {
            JsonObject a = e.getAsJsonObject();
            abilities.add(new Ability(JsonParse.str(a, "slot"), JsonParse.str(a, "name"), JsonParse.str(a, "combo"),
                    JsonParse.str(a, "texture"), JsonParse.bool(a, "unlocked"), JsonParse.num(a, "unlockLevel"),
                    JsonParse.num(a, "cd"), JsonParse.bool(a, "ready")));
        }
        String combo = JsonParse.str(o, "combo");
        return new HudData(JsonParse.bool(o, "show"), JsonParse.num(o, "color"), JsonParse.str(o, "resource"),
                JsonParse.num(o, "value"), Math.max(1, JsonParse.num(o, "max")), JsonParse.bool(o, "armed"),
                combo == null ? "" : combo, List.copyOf(abilities));
    }

    /** How many of {@code ability}'s combo clicks the combo so far already matches; -1 once it went another way. */
    int matched(Ability ability) {
        if (combo.isEmpty() || ability.combo() == null) return 0;
        return ability.combo().startsWith(combo) ? (combo.length() + 1) / 2 : -1;
    }
}

package dev.sirbepy.mc.core.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Shared field-readers for a plugin-message channel's incoming JSON request (see
 * {@code dev.sirbepy.mc.classes.talent.TreeChannel}, {@code dev.sirbepy.mc.haru.quest.QuestChannel}
 * and {@code dev.sirbepy.mc.guild.gui.GuildChannel}, which each used to paste a private copy of
 * these two methods). Every value is optional and malformed input is never trusted, so both methods
 * fall back instead of throwing.
 */
public final class JsonSnapshot {

    private JsonSnapshot() {
    }

    public static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }
}

package dev.sirbepy.mc.tag.logic;

import java.util.Locale;

/** How the online-player-count multiplier scales with {@code n} (see {@link PointsMath}). */
public enum PointsCurve {
    LOG,
    LINEAR;

    public static PointsCurve parse(String text, PointsCurve fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return PointsCurve.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

package dev.sirbepy.mc.core;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Parses a config-supplied {@link ZoneId}, falling back on a typo'd or missing value rather than
 * taking the plugin down at load. */
public final class Zones {

    private Zones() {
    }

    public static ZoneId parse(String raw, ZoneId fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            return fallback;
        }
    }
}

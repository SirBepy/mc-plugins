package dev.sirbepy.mc.endfight.tier;

import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Detects Dragonkind Evolved through Paper's datapack API: no Bukkit API exposes "does function
 * dke:... exist" directly (checked against this repo's pinned {@code paper-api-26.2.build.132}
 * via javap: {@code Server} has no {@code getFunction}/{@code getFunctions}, and {@code Tag} only
 * covers blocks/items/entity types, not functions), so this instead looks for an enabled datapack
 * whose own name mentions the pack. {@code dke} is the pack's internal namespace (every
 * {@code data/dke/...} path), not necessarily its distributed zip/folder name, so this also tries
 * "dragonkind"; {@code config.yml}'s {@code tiers.dke.force-installed} is the escape hatch if
 * live's actual filename matches neither.
 */
public final class BukkitDkePresence implements DkePresence {

    private final Supplier<Boolean> forcedOverride;
    private final Logger logger;

    public BukkitDkePresence(Supplier<Boolean> forcedOverride, Logger logger) {
        this.forcedOverride = forcedOverride;
        this.logger = logger;
    }

    @Override
    public boolean installed() {
        Boolean forced = forcedOverride.get();
        if (forced != null) {
            return forced;
        }
        try {
            return Bukkit.getServer().getDatapackManager().getEnabledPacks().stream()
                    .anyMatch(pack -> matches(pack.getName()));
        } catch (RuntimeException e) {
            logger.fine("Could not read the datapack list to detect Dragonkind Evolved; assuming it isn't installed: " + e);
            return false;
        }
    }

    private static boolean matches(String packName) {
        String lower = packName.toLowerCase(Locale.ROOT);
        return lower.contains("dke") || lower.contains("dragonkind");
    }
}

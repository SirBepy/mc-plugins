package dev.sirbepy.mc.endfight.cult;

import org.bukkit.entity.EntityType;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Config.yml spells out mob types as plain lowercase names ({@code pillager}); this is the one
 * place that turns those back into {@link EntityType}, warning once per bad value instead of
 * failing the whole spawn. */
final class CultEntityTypes {

    private CultEntityTypes() {
    }

    static EntityType parse(String name, Logger logger) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unknown cult entity type in config.yml: " + name, e);
            return null;
        }
    }
}

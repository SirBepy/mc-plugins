package dev.sirbepy.mc.carry;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

/** Snapshot of {@code config.yml}, rebuilt by {@code /carry reload}. */
public final class CarryConfig {

    private static final Set<EntityType> NEVER = EnumSet.of(EntityType.PLAYER, EntityType.ENDER_DRAGON, EntityType.WITHER);

    private final Set<EntityType> carryable;
    private final double throwStrength;
    private final double speedPenalty;
    private final Messages messages;

    public CarryConfig(FileConfiguration config, Logger logger) {
        Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String id : config.getStringList("carryable")) {
            NamespacedKey key = NamespacedKey.fromString(id.trim().toLowerCase(java.util.Locale.ROOT));
            EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
            if (type == null) {
                logger.warning("Unknown entity type in carryable: " + id);
            } else if (!NEVER.contains(type)) {
                types.add(type);
            }
        }
        this.carryable = Collections.unmodifiableSet(types);
        this.throwStrength = config.getDouble("throw-strength", 1.0);
        this.speedPenalty = config.getDouble("speed-penalty", 0.15);
        this.messages = new Messages(config.getConfigurationSection("messages"));
    }

    public boolean isCarryable(EntityType type) {
        return carryable.contains(type);
    }

    public double throwStrength() {
        return throwStrength;
    }

    public double speedPenalty() {
        return speedPenalty;
    }

    public Messages messages() {
        return messages;
    }
}

package dev.sirbepy.mc.endfight.cult;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The PDC tag that says "this mob belongs to the Ender cult" ({@code endfight:cult}), plus which
 * {@link dev.sirbepy.mc.endfight.arena.GuardPost} it was placed at ({@code endfight:post}, guards
 * only - wave mobs carry no post). Survives restarts and chunk unload/reload like any other PDC
 * value, so cult membership never has to be re-derived from nearby location after the fact.
 */
public final class CultMarker {

    static final NamespacedKey CULT = new NamespacedKey("endfight", "cult");
    static final NamespacedKey POST_INDEX = new NamespacedKey("endfight", "post");

    private CultMarker() {
    }

    public static void mark(Entity entity) {
        entity.getPersistentDataContainer().set(CULT, PersistentDataType.BYTE, (byte) 1);
    }

    public static void markGuard(Entity entity, int postIndex) {
        mark(entity);
        entity.getPersistentDataContainer().set(POST_INDEX, PersistentDataType.INTEGER, postIndex);
    }

    public static boolean isCult(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(CULT, PersistentDataType.BYTE);
    }

    /** -1 if this entity isn't a guard (a wave mob, or not cult at all). */
    public static int postIndex(Entity entity) {
        if (entity == null) {
            return -1;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Integer value = pdc.get(POST_INDEX, PersistentDataType.INTEGER);
        return value != null ? value : -1;
    }
}

package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Marks a boss summoned by {@code /guardianadmin test}: a rehearsal that pays nothing and records
 * nothing when it dies (no loot, no Hall of Fame, no {@code GuardianDefeatedEvent}).
 */
public final class TestFight {

    private TestFight() {
    }

    /** On a boss: BOOLEAN, present only on a rehearsal. */
    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "test_fight");
    }

    public static void mark(LivingEntity boss, NamespacedKey key) {
        boss.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
    }

    public static boolean is(LivingEntity boss, NamespacedKey key) {
        return boss.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }
}

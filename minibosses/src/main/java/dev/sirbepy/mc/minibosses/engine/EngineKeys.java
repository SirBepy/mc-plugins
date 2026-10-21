package dev.sirbepy.mc.minibosses.engine;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** The persistent-data keys the engine reads and writes, built in one place so they cannot drift apart. */
public final class EngineKeys {

    private EngineKeys() {
    }

    /** On a boss: the index of the last phase whose transition hook fired (INTEGER). */
    public static NamespacedKey phase(Plugin plugin) {
        return new NamespacedKey(plugin, "boss_phase");
    }

    /** On a temporary fight entity: the UUID of the boss that owns it (STRING). */
    public static NamespacedKey owner(Plugin plugin) {
        return new NamespacedKey(plugin, "fight_owner");
    }
}

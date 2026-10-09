package dev.sirbepy.mc.dragonballs.bedrock;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * What Bedrock players see of the summon dragon. Geyser can't show item displays, so the dragon's
 * Java model is invisible to Bedrock; instead the summon spawns one carrier entity per model part,
 * and an implementation that hooks Geyser ({@link GeyserDragons}) turns each carrier into the
 * matching custom Bedrock entity from the plugin's Bedrock pack. {@link #NONE} is the no-Geyser
 * case: the summon falls back to its Ender Dragon stand-in.
 */
public interface BedrockDragons {

    /**
     * One carrier's part as Bedrock draws it.
     *
     * @param model the part's model name, e.g. {@code shenron_head} (DragonPose's item model without
     *              its {@code dragon/} folder)
     * @param scale blocks per 16 rig units
     * @param skin  the set colours: {@code BallSet} ordinal
     * @param yaw   Minecraft yaw in -180..180
     * @param pitch Minecraft pitch, positive tips the part's front down
     */
    record Part(String model, float scale, int skin, float yaw, float pitch) {
    }

    BedrockDragons NONE = new BedrockDragons() {
        @Override
        public boolean active() {
            return false;
        }

        @Override
        public void show(UUID carrier, Part part) {
        }

        @Override
        public void turn(UUID carrier, float yaw, float pitch) {
        }

        @Override
        public void forget(UUID carrier) {
        }
    };

    /** Whether Bedrock players can see the models now; false keeps the Ender Dragon stand-in. */
    boolean active();

    /** {@code carrier} draws {@code part} for Bedrock players; call before the carrier is in the
     * world, so it is known by the time Geyser first sees it. */
    void show(UUID carrier, Part part);

    /** {@code carrier}'s part now faces {@code yaw}, {@code pitch}. */
    void turn(UUID carrier, float yaw, float pitch);

    /** {@code carrier} is gone. */
    void forget(UUID carrier);

    /** The Geyser hook when {@code enabled} (config's bedrock-dragon-models), Geyser is installed
     * and its API loads, otherwise {@link #NONE}. Disabled, nothing is registered with Geyser at
     * all: no custom entities, no pack. */
    static BedrockDragons hook(JavaPlugin plugin, boolean enabled) {
        if (plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return NONE;
        }
        if (!enabled) {
            plugin.getLogger().info("Bedrock dragon models are disabled (bedrock-dragon-models: false); "
                    + "Bedrock players see an Ender Dragon");
            return NONE;
        }
        try {
            return new GeyserDragons(plugin);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Couldn't hook Geyser for the Bedrock dragon models; Bedrock players see an Ender Dragon", e);
            return NONE;
        }
    }

    /** Minecraft yaw folded into -180..180, the range the Bedrock yaw property holds. */
    static float wrapYaw(float yaw) {
        return ((yaw % 360f) + 540f) % 360f - 180f;
    }
}

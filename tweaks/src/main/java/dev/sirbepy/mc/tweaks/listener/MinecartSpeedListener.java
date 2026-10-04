package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Doubles every minecart's top speed. maxSpeed is a runtime field, not saved with the entity, so
 * it is set every time a cart enters a world: placed, dispensed, summoned or loaded with a chunk.
 */
public final class MinecartSpeedListener implements Listener {

    // Vanilla is 0.4 blocks/tick (8 blocks/s). The default rail physics steers by one rail block
    // per tick, so above 1.0 a cart can skip a curve block and derail; 0.8 keeps a margin.
    public static final double MAX_SPEED = 0.8;

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        apply(event.getEntity());
    }

    /** Entities already in loaded chunks when the plugin enables never fire the add event. */
    public static void applyToLoaded(Iterable<World> worlds) {
        for (World world : worlds) {
            world.getEntitiesByClass(Minecart.class).forEach(MinecartSpeedListener::apply);
        }
    }

    public static void apply(Entity entity) {
        if (entity instanceof Minecart minecart) {
            minecart.setMaxSpeed(MAX_SPEED);
        }
    }
}
